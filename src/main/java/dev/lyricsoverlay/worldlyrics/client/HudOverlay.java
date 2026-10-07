package dev.lyricsoverlay.worldlyrics.client;

import dev.lyricsoverlay.worldlyrics.bridge.Snapshot;
import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Экранные эффекты: вспышка по краям, строки в HUD, титр трека и всплывающие подсказки. */
public final class HudOverlay {
    private HudOverlay() {
    }

    static void register(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("lyrics_hud", HudOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        LyricsController c = LyricsController.INSTANCE;
        long now = System.nanoTime();
        vignette(g, c, now, w, h);
        Config.Mode mode = Config.MODE.get();
        if ((mode == Config.Mode.HUD || Config.SHOW_HUD.get()) && c.alpha > 0.01f) {
            lyrics(g, mc.font, c, now, w, h);
        }
        title(g, mc.font, c, now, w, h);
        toast(g, mc.font, c, now, w);
    }

    // ------------------------------------------------------------ вспышка

    private static void vignette(GuiGraphics g, LyricsController c, long now, int w, int h) {
        if (!Config.VIGNETTE.get() || c.alpha <= 0.01f) {
            return;
        }
        double age = c.pulseAge(now);
        if (age > 1.5) {
            return;
        }
        float v = (float) (Config.VIGNETTE_STRENGTH.get() * (1 - Math.exp(-age * 40)) * Math.exp(-age * 3.5) * c.alpha);
        if (v < 0.005f) {
            return;
        }
        int col = ColorUtil.parse(Config.VIGNETTE_COLOR.get(), 0xFFFFFFFF);
        int edge = ColorUtil.withAlpha(col, v);
        int clear = col & 0x00FFFFFF;
        int band = Math.max(8, (int) (Math.min(w, h) * 0.22f));
        g.fillGradient(0, 0, w, band, edge, clear);
        g.fillGradient(0, h - band, w, h, clear, edge);
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            float k = 1f - (i + 0.5f) / steps;
            int cc = ColorUtil.withAlpha(col, v * k * k);
            int x0 = i * band / steps;
            int x1 = (i + 1) * band / steps;
            g.fill(x0, 0, x1, h, cc);
            g.fill(w - x1, 0, w - x0, h, cc);
        }
    }

    // ------------------------------------------------------------ строки

    private static void lyrics(GuiGraphics g, Font font, LyricsController c, long now, int w, int h) {
        Snapshot s = c.snap;
        List<Snapshot.Line> lines = s.lines();
        if (lines.isEmpty()) {
            return;
        }
        int count = Config.HUD_LINES.get();
        float scale = Config.HUD_SCALE.get().floatValue();
        int start = Math.max(0, c.idx);
        int unsung = ColorUtil.parse(Config.COLOR_UNSUNG.get(), 0xFF8E8E8E);
        int sung = ColorUtil.parse(Config.COLOR_SUNG.get(), 0xFFFFFFFF);
        int inactive = ColorUtil.parse(Config.COLOR_INACTIVE.get(), 0xFFC8C8C8);
        Config.Karaoke karaoke = Config.KARAOKE.get();
        double age = (now - c.idxChanged) / 1e9;
        float appear = Ease.outCubic(Ease.clamp01(age / Math.max(0.05, Config.APPEAR_TIME.get())));

        int lineH = 11;
        float blockH = count * lineH * scale;
        float top = Config.HUD_POS.get() == Config.HudPos.BOTTOM ? h - Config.HUD_MARGIN.get() - blockH : Config.HUD_MARGIN.get();

        for (int k = 0; k < count && start + k < lines.size(); k++) {
            int i = start + k;
            String text = TextPainter.prepare(lines.get(i).text());
            if (text.isBlank()) {
                if (!Config.SHOW_NOTE.get()) {
                    continue;
                }
                text = "♪";
            }
            boolean current = i == c.idx;
            float ls = current ? scale : scale * 0.8f;
            float a = c.alpha * (current ? 1f : Config.INACTIVE_ALPHA.get().floatValue());
            float dy = 0f;
            if (current) {
                a *= appear;
                dy = (1f - appear) * 6f;
            }
            float progress = !current ? -1f : switch (karaoke) {
                case FILL -> LyricsController.progress(lines, i, c.pos);
                case LINE -> 1f;
                case NONE -> -1f;
            };
            int plain = current ? sung : (i < c.idx ? inactive : unsung);
            float y = top + k * lineH * scale + dy;
            g.pose().pushPose();
            g.pose().translate(w / 2f, y, 0);
            g.pose().scale(ls, ls, 1f);
            drawKaraoke(g, font, text, -font.width(text) / 2, 0, progress, unsung, sung, plain, a);
            g.pose().popPose();
        }
    }

    private static void drawKaraoke(GuiGraphics g, Font font, String text, int x, int y, float progress,
                                    int unsung, int sung, int plain, float alpha) {
        int total = Math.max(1, text.codePointCount(0, text.length()));
        float n = progress < 0 ? -1 : progress * total;
        boolean rainbow = Config.RAINBOW.get();
        float hue = (float) ((System.nanoTime() / 1e9) * Config.RAINBOW_SPEED.get());
        boolean shadow = Config.SHADOW.get() || Config.OUTLINE.get();
        int gi = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            int color;
            if (n < 0) {
                color = plain;
            } else {
                int sc = rainbow ? (ColorUtil.rainbow(hue + gi * 0.035f) & 0xFFFFFF) | (sung & 0xFF000000) : sung;
                color = gi < (int) n ? sc : gi == (int) n ? ColorUtil.lerp(unsung, sc, n - (int) n) : unsung;
            }
            int c = ColorUtil.withAlpha(color, alpha);
            if (ColorUtil.alpha(c) >= 5) {
                g.drawString(font, ch, x, y, c, shadow);
            }
            x += font.width(ch);
            gi++;
        }
    }

    // ------------------------------------------------------------ титр

    private static void title(GuiGraphics g, Font font, LyricsController c, long now, int w, int h) {
        boolean hud = Config.TITLE_IN_HUD.get() || Config.MODE.get() == Config.Mode.HUD;
        if (!hud || !Config.TITLE_CARD.get() || c.titleAt == 0 || c.titleText.isEmpty() || !Config.ENABLED.get()) {
            return;
        }
        double age = (now - c.titleAt) / 1e9;
        double dur = Config.TITLE_TIME.get();
        if (age > dur) {
            return;
        }
        float a = Math.min(Ease.outCubic(Ease.clamp01(age / 0.5)), Ease.clamp01((dur - age) / 0.7));
        if (a < 0.02f) {
            return;
        }
        float rise = (1f - Ease.outCubic(Ease.clamp01(age / 0.6))) * 8f;
        int y = (int) (h * 0.2f + rise);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y, 0);
        g.pose().scale(2f, 2f, 1f);
        String t = TextPainter.prepare(c.titleText);
        g.drawString(font, t, -font.width(t) / 2, 0, ColorUtil.withAlpha(0xFFFFFFFF, a), true);
        g.pose().popPose();
        if (!c.titleSub.isEmpty()) {
            String sub = TextPainter.prepare(c.titleSub);
            g.drawString(font, sub, w / 2 - font.width(sub) / 2, y + 22, ColorUtil.withAlpha(0xFFBDBDBD, a), true);
        }
    }

    // ------------------------------------------------------------ подсказка

    private static void toast(GuiGraphics g, Font font, LyricsController c, long now, int w) {
        String text = c.activeToast(now);
        if (text.isEmpty()) {
            return;
        }
        float a = c.toastAlpha(now);
        int tw = font.width(text);
        int x = w / 2 - tw / 2;
        int y = 10;
        g.fill(x - 8, y - 5, x + tw + 8, y + 13, ColorUtil.withAlpha(0xD0101010, a));
        g.fill(x - 8, y + 12, x + tw + 8, y + 13, ColorUtil.withAlpha(0x60FFFFFF, a));
        int col = ColorUtil.withAlpha(0xFFF2F2F2, a);
        if (ColorUtil.alpha(col) >= 5) {
            g.drawString(font, text, x, y, col, false);
        }
    }
}
