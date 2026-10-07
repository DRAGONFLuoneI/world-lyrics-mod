package dev.lyricsoverlay.worldlyrics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Рисует строку текста в 3D побуквенно: караоке-заливка, обводка, свечение, подложка,
 * анимации появления/исчезновения для каждой буквы.
 */
final class TextPainter {
    /** Размер одного пикселя шрифта в блоках (как у табличек над мобами). */
    static final float PX = 0.025f;
    static final int ROW_H = 10;

    private static final float[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    /** Параметры одной строки. Объект переиспользуется, чтобы не мусорить каждый кадр. */
    static final class Paint {
        List<String> rows = List.of();
        float y;                 // центр строки (в пикселях шрифта)
        float scale = 1f;
        float alpha = 1f;
        float progress = -1f;    // -1 — без караоке
        int plain = 0xFFFFFFFF;
        int unsung = 0xFF888888;
        int sung = 0xFFFFFFFF;
        boolean glow;
        boolean animate;         // анимация появления
        double age = 99;         // секунды с момента появления
        double dying = -1;       // секунды с начала исчезновения
        float seed;
        float pulse;             // доп. масштаб (пульс)
        int light = 0xF000F0;
        boolean background;

        Paint reset() {
            rows = List.of();
            y = 0;
            scale = 1f;
            alpha = 1f;
            progress = -1f;
            glow = false;
            animate = false;
            age = 99;
            dying = -1;
            seed = 0;
            pulse = 0;
            background = Config.BACKGROUND.get();
            return this;
        }
    }

    private TextPainter() {
    }

    // ------------------------------------------------------------------ перенос

    static String prepare(String text) {
        String t = text == null ? "" : text.strip();
        return Config.UPPERCASE.get() ? t.toUpperCase(Locale.ROOT) : t;
    }

    static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (font.width(text) <= maxWidth) {
            out.add(text);
            return out;
        }
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = row.length() == 0 ? word : row + " " + word;
            if (font.width(candidate) > maxWidth && row.length() > 0) {
                out.add(row.toString());
                row = new StringBuilder(word);
            } else {
                row = new StringBuilder(candidate);
            }
        }
        if (row.length() > 0) {
            out.add(row.toString());
        }
        return out;
    }

    static int totalChars(List<String> rows) {
        int n = 0;
        for (String r : rows) {
            n += r.codePointCount(0, r.length());
        }
        return n;
    }

    // ------------------------------------------------------------------ рисование

    static void draw(PoseStack ps, MultiBufferSource buf, Font font, Paint p) {
        if (p.rows.isEmpty() || p.alpha <= 0.01f) {
            return;
        }
        Config.Appear appear = Config.APPEAR.get();
        Config.Disappear disappear = Config.DISAPPEAR.get();
        double appearT = Math.max(0.01, Config.APPEAR_TIME.get());
        double disT = Math.max(0.01, Config.DISAPPEAR_TIME.get());
        float ap = p.animate ? Ease.clamp01(p.age / appearT) : 1f;
        float d = p.dying >= 0 ? Ease.clamp01(p.dying / disT) : 0f;

        float lineAlpha = p.alpha;
        float lineScale = p.scale * (1f + p.pulse);
        float dyLine = 0f;
        if (p.animate) {
            switch (appear) {
                case FADE -> lineAlpha *= Ease.outCubic(ap);
                case POP -> lineScale *= Math.max(0f, Ease.outBack(ap, Config.POP_BOUNCE.get().floatValue()));
                case RISE -> {
                    dyLine += (1f - Ease.outCubic(ap)) * 10f;
                    lineAlpha *= ap;
                }
                default -> {
                }
            }
        }
        if (p.dying >= 0) {
            switch (disappear) {
                case FADE -> lineAlpha *= 1f - d;
                case FLOAT_UP -> {
                    dyLine -= Ease.outCubic(d) * 16f;
                    lineAlpha *= 1f - d;
                }
                case SHRINK -> {
                    lineScale *= 1f - Ease.outCubic(d);
                    lineAlpha *= 1f - d * 0.5f;
                }
                default -> {
                }
            }
        }
        if (lineAlpha <= 0.01f || lineScale <= 0.01f) {
            return;
        }

        int total = Math.max(1, totalChars(p.rows));
        float sungCount = p.progress < 0 ? -1f : p.progress * total;
        boolean rainbow = Config.RAINBOW.get();
        float hueBase = (float) ((System.nanoTime() / 1e9) * Config.RAINBOW_SPEED.get());
        boolean seeThrough = Config.SEE_THROUGH.get();
        Font.DisplayMode mode = seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        boolean outline = Config.OUTLINE.get();
        boolean shadow = Config.SHADOW.get();
        boolean glow = p.glow && Config.GLOW.get();
        int outlineColor = ColorUtil.parse(Config.OUTLINE_COLOR.get(), 0xC0000000);
        int glowColor = ColorUtil.parse(Config.GLOW_COLOR.get(), 0x50FFFFFF);
        float glowSize = Config.GLOW_SIZE.get().floatValue();
        double stagger = Config.WAVE_STAGGER.get();

        ps.pushPose();
        ps.translate(0, p.y + dyLine, 0);
        ps.scale(lineScale, lineScale, 1f);

        int rows = p.rows.size();
        float totalH = rows * ROW_H;
        if (p.background) {
            int maxW = 0;
            for (String r : p.rows) {
                maxW = Math.max(maxW, font.width(r));
            }
            int pad = Config.BG_PADDING.get();
            int bg = ColorUtil.withAlpha(ColorUtil.parse(Config.BG_COLOR.get(), 0x80000000), lineAlpha);
            quad(ps.last().pose(), buf, seeThrough, -maxW / 2f - pad, -totalH / 2f - pad,
                    maxW / 2f + pad, totalH / 2f + pad - 1, bg, p.light);
        }

        int gi = 0;
        for (int r = 0; r < rows; r++) {
            String row = p.rows.get(r);
            float x = -font.width(row) / 2f;
            float rowY = -totalH / 2f + r * ROW_H;
            int i = 0;
            while (i < row.length()) {
                int cp = row.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                int w = font.width(ch);
                if (cp == ' ') {
                    x += w;
                    gi++;
                    continue;
                }

                float ca = 1f;
                float dy = 0f;
                if (p.animate) {
                    if (appear == Config.Appear.WAVE) {
                        float ai = Ease.clamp01((p.age - gi * stagger) / appearT);
                        dy += (1f - Ease.outBack(ai, 1.3f)) * 7f;
                        ca *= Ease.clamp01(ai * 1.6);
                    } else if (appear == Config.Appear.TYPEWRITER) {
                        ca *= Ease.clamp01(ap * total - gi);
                    }
                }
                if (p.dying >= 0 && disappear == Config.Disappear.DISSOLVE) {
                    float thr = Ease.hash(p.seed, gi);
                    ca *= Ease.clamp01((thr - d) * 5f);
                    dy -= d * thr * 8f;
                }

                int color;
                if (sungCount < 0) {
                    color = p.plain;
                } else {
                    int sung = rainbow ? (ColorUtil.rainbow(hueBase + gi * 0.035f) & 0xFFFFFF) | (p.sung & 0xFF000000) : p.sung;
                    if (gi < (int) sungCount) {
                        color = sung;
                    } else if (gi == (int) sungCount) {
                        color = ColorUtil.lerp(p.unsung, sung, sungCount - (int) sungCount);
                    } else {
                        color = p.unsung;
                    }
                }
                float a = lineAlpha * ca;
                int main = ColorUtil.withAlpha(color, a);
                if (ColorUtil.alpha(main) >= 5) {
                    Matrix4f m = ps.last().pose();
                    float cy = rowY + dy;
                    if (glow) {
                        int gc = ColorUtil.withAlpha(glowColor, a);
                        if (ColorUtil.alpha(gc) >= 5) {
                            for (float[] o : RING) {
                                font.drawInBatch(ch, x + o[0] * glowSize, cy + o[1] * glowSize, gc, false, m, buf, mode, 0, p.light);
                            }
                        }
                    }
                    if (outline) {
                        int oc = ColorUtil.withAlpha(outlineColor, a);
                        if (ColorUtil.alpha(oc) >= 5) {
                            for (float[] o : RING) {
                                font.drawInBatch(ch, x + o[0] * 0.7f, cy + o[1] * 0.7f, oc, false, m, buf, mode, 0, p.light);
                            }
                        }
                    }
                    font.drawInBatch(ch, x, cy, main, shadow && !outline, m, buf, mode, 0, p.light);
                }
                x += w;
                gi++;
            }
        }
        ps.popPose();
    }

    private static void quad(Matrix4f m, MultiBufferSource buf, boolean seeThrough, float x0, float y0, float x1, float y1,
                             int argb, int light) {
        if (ColorUtil.alpha(argb) < 3) {
            return;
        }
        RenderType type = seeThrough ? RenderType.textBackgroundSeeThrough() : RenderType.textBackground();
        VertexConsumer vc = buf.getBuffer(type);
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float z = 0.01f;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        if (buf instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(type); // подложка — строго до текста (важно для режима «сквозь блоки»)
        }
    }
}
