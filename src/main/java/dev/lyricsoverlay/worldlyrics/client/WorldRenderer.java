package dev.lyricsoverlay.worldlyrics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lyricsoverlay.worldlyrics.Lang;
import dev.lyricsoverlay.worldlyrics.bridge.Snapshot;
import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.List;

/** Рисует строки в мире: панель перед камерой / на якоре и отдельные строки «разбросано». */
public final class WorldRenderer {
    private WorldRenderer() {
    }

    private static final TextPainter.Paint PAINT = new TextPainter.Paint();
    private static float slotRows = 1f;
    private static long lastSlotNanos;

    static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Camera cam = e.getCamera();
        LyricsController c = LyricsController.INSTANCE;
        c.frame(mc, cam);
        if (c.alpha <= 0.01f && c.lines.isEmpty()) {
            return;
        }

        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        long now = System.nanoTime();
        Config.Mode mode = Config.MODE.get();

        if (c.alpha > 0.01f) {
            if (mode == Config.Mode.FOLLOW && c.followPos != null) {
                drawPanel(ps, buf, mc, cam, c, c.followPos, now);
            } else if (mode == Config.Mode.ANCHORED && c.anchor != null) {
                drawPanel(ps, buf, mc, cam, c, c.anchor.add(0, Config.ANCHOR_HEIGHT.get(), 0), now);
            }
        }
        if (mode != Config.Mode.HUD) {
            for (WorldLine wl : c.lines) {
                drawWorldLine(ps, buf, mc, cam, c, wl, now);
            }
        }
        buf.endBatch();
    }

    // ------------------------------------------------------------ помощники

    private static int light(Minecraft mc, Vec3 pos) {
        if (Config.FULL_BRIGHT.get() || mc.level == null) {
            return LightTexture.FULL_BRIGHT;
        }
        return LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));
    }

    private static float bob(float seed, long now) {
        if (!Config.BOB.get()) {
            return 0f;
        }
        double t = now / 1e9 * Config.BOB_SPEED.get();
        return (float) (Math.sin(t * Math.PI + seed) * Config.BOB_AMOUNT.get());
    }

    private static void sway(PoseStack ps, float seed, long now) {
        if (Config.SWAY.get()) {
            double t = now / 1e9 * Config.BOB_SPEED.get() * 0.7;
            ps.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin(t * Math.PI + seed * 1.7) * Config.SWAY_DEG.get())));
        }
    }

    private static float sizeFactor(double dist) {
        return Config.SCREEN_SIZE.get() ? (float) Math.max(1.0, dist / 6.0) : 1f;
    }

    private static int[] colors(float t) {
        int unsung = ColorUtil.parse(Config.COLOR_UNSUNG.get(), 0xFF8E8E8E);
        int sung = ColorUtil.parse(Config.COLOR_SUNG.get(), 0xFFFFFFFF);
        int inactive = ColorUtil.parse(Config.COLOR_INACTIVE.get(), 0xFFC8C8C8);
        return new int[]{ColorUtil.lerp(unsung, inactive, t), ColorUtil.lerp(sung, inactive, t), inactive};
    }

    // ------------------------------------------------------------ панель

    private static void drawPanel(PoseStack ps, MultiBufferSource buf, Minecraft mc, Camera cam, LyricsController c,
                                  Vec3 center, long now) {
        Vec3 camPos = cam.getPosition();
        double dist = center.distanceTo(camPos);
        if (dist > Config.MAX_DISTANCE.get()) {
            return;
        }
        Font font = mc.font;
        int light = light(mc, center);
        ps.pushPose();
        ps.translate(center.x - camPos.x, center.y - camPos.y + bob(0f, now), center.z - camPos.z);
        ps.mulPose(cam.rotation());
        sway(ps, 0f, now);
        float s = TextPainter.PX * Config.SCALE.get().floatValue() * sizeFactor(dist);
        ps.scale(-s, -s, s);

        Snapshot snap = c.snap;
        List<Snapshot.Line> lines = snap.lines();
        if (lines.isEmpty()) {
            drawStatus(ps, buf, font, c, light);
            ps.popPose();
            return;
        }

        int n = Config.LINE_COUNT.get();
        float half = (n - 1) / 2f;
        int maxW = Config.MAX_WIDTH.get();
        int lo = Math.max(0, Mth.floor(c.scroll - half - 1));
        int hi = Math.min(lines.size() - 1, Mth.ceil(c.scroll + half + 1));

        // высота «слота» плавно подстраивается под переносы строк
        int maxRows = 1;
        for (int i = lo; i <= hi; i++) {
            maxRows = Math.max(maxRows, TextPainter.wrap(font, TextPainter.prepare(lines.get(i).text()), maxW).size());
        }
        float dt = lastSlotNanos == 0 ? 0.016f : (float) Math.min(0.1, (now - lastSlotNanos) / 1e9);
        lastSlotNanos = now;
        slotRows += (maxRows - slotRows) * (1f - (float) Math.exp(-dt * 8));
        float slotH = TextPainter.ROW_H * Config.LINE_SPACING.get().floatValue() * slotRows;

        Config.Karaoke karaoke = Config.KARAOKE.get();
        float inactiveScale = Config.INACTIVE_SCALE.get().floatValue();
        float inactiveAlpha = Config.INACTIVE_ALPHA.get().floatValue();
        double lineAge = (now - c.idxChanged) / 1e9;

        for (int i = lo; i <= hi; i++) {
            float rel = i - c.scroll;
            float a = Math.abs(rel);
            float fade = Mth.clamp(half + 1f - a, 0f, 1f);
            if (fade <= 0f) {
                continue;
            }
            String text = lines.get(i).text();
            if (text.isBlank()) {
                if (!Config.SHOW_NOTE.get()) {
                    continue;
                }
                text = "♪";
            }
            float t = Math.min(1f, a);
            int[] col = colors(t);
            TextPainter.Paint p = PAINT.reset();
            p.rows = TextPainter.wrap(font, TextPainter.prepare(text), maxW);
            p.y = rel * slotH;
            p.scale = 1f + (inactiveScale - 1f) * t;
            p.alpha = (1f + (inactiveAlpha - 1f) * t) * fade * c.alpha;
            p.light = light;
            p.unsung = col[0];
            p.sung = col[1];
            if (i == c.idx) {
                p.glow = true;
                p.animate = true;
                p.age = lineAge;
                p.seed = i * 3.1f;
                if (Config.BEAT_PULSE.get()) {
                    p.pulse = Config.BEAT_PULSE_AMOUNT.get().floatValue() * Math.max(-0.4f, Ease.wobble((float) lineAge, 1.6f, 6f))
                            * Ease.clamp01(lineAge * 25);
                }
                switch (karaoke) {
                    case FILL -> p.progress = LyricsController.progress(lines, i, c.pos);
                    case LINE -> p.progress = 1f;
                    case NONE -> p.plain = col[1];
                }
            } else if (i < c.idx) {
                p.plain = karaoke == Config.Karaoke.NONE ? col[2] : col[1];
            } else {
                p.plain = karaoke == Config.Karaoke.NONE ? col[2] : col[0];
            }
            TextPainter.draw(ps, buf, font, p);
        }
        ps.popPose();
    }

    private static void drawStatus(PoseStack ps, MultiBufferSource buf, Font font, LyricsController c, int light) {
        Snapshot s = c.snap;
        int[] col = colors(0f);
        TextPainter.Paint p = PAINT.reset();
        String head = s.artist().isEmpty() ? "♪ " + s.title() : "♪ " + s.title() + " — " + s.artist();
        p.rows = TextPainter.wrap(font, TextPainter.prepare(head), Config.MAX_WIDTH.get());
        p.plain = col[1];
        p.alpha = c.alpha * 0.9f;
        p.scale = 0.8f;
        p.light = light;
        p.y = -4;
        TextPainter.draw(ps, buf, font, p);

        String status = s.status().isEmpty() ? Lang.t("Текст не найден", "No lyrics found") : s.status();
        p = PAINT.reset();
        p.rows = List.of(status);
        p.plain = col[2];
        p.alpha = c.alpha * 0.6f;
        p.scale = 0.55f;
        p.light = light;
        p.background = false;
        p.y = 8;
        TextPainter.draw(ps, buf, font, p);
    }

    // ------------------------------------------------------------ строки в мире

    private static void drawWorldLine(PoseStack ps, MultiBufferSource buf, Minecraft mc, Camera cam, LyricsController c,
                                      WorldLine wl, long now) {
        Vec3 camPos = cam.getPosition();
        double dist = wl.pos.distanceTo(camPos);
        if (dist > Config.MAX_DISTANCE.get()) {
            return;
        }
        float vis = wl.title ? Math.max(c.alpha, Config.ENABLED.get() ? 1f : 0f) : c.alpha;
        if (vis <= 0.01f) {
            return;
        }
        Font font = mc.font;
        int light = light(mc, wl.pos);
        ps.pushPose();
        ps.translate(wl.pos.x - camPos.x, wl.pos.y - camPos.y + bob(wl.seed, now), wl.pos.z - camPos.z);
        if (Config.BILLBOARD.get()) {
            ps.mulPose(cam.rotation());
        } else {
            ps.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-wl.yaw), 0f, 0f));
        }
        sway(ps, wl.seed, now);
        float s = TextPainter.PX * Config.SCALE.get().floatValue() * sizeFactor(dist) * (wl.title ? 1.6f : 1f);
        ps.scale(-s, -s, s);

        int[] col = colors(0f);
        TextPainter.Paint p = PAINT.reset();
        p.rows = TextPainter.wrap(font, TextPainter.prepare(wl.text), Config.MAX_WIDTH.get());
        p.light = light;
        p.animate = true;
        p.age = wl.age(now);
        p.dying = wl.dying(now);
        p.seed = wl.seed;
        p.alpha = vis;

        if (wl.title) {
            p.plain = col[1];
            p.glow = true;
            p.y = wl.subText.isEmpty() ? 0 : -4;
            TextPainter.draw(ps, buf, font, p);
            if (!wl.subText.isEmpty()) {
                TextPainter.Paint sub = PAINT.reset();
                sub.rows = List.of(TextPainter.prepare(wl.subText));
                sub.plain = col[2];
                sub.light = light;
                sub.animate = true;
                sub.age = Math.max(0, wl.age(now) - 0.15);
                sub.dying = wl.dying(now);
                sub.seed = wl.seed + 7f;
                sub.alpha = vis * 0.85f;
                sub.scale = 0.6f;
                sub.background = false;
                sub.y = 9;
                TextPainter.draw(ps, buf, font, sub);
            }
        } else {
            Config.Karaoke karaoke = Config.KARAOKE.get();
            List<Snapshot.Line> lines = c.snap.lines();
            boolean sameText = wl.index >= 0 && wl.index < lines.size();
            if (wl.current && wl.die < 0 && sameText) {
                p.glow = true;
                if (Config.BEAT_PULSE.get()) {
                    float age = (float) wl.age(now);
                    p.pulse = Config.BEAT_PULSE_AMOUNT.get().floatValue() * Math.max(-0.4f, Ease.wobble(age, 1.6f, 6f))
                            * Ease.clamp01(age * 25);
                }
                p.unsung = col[0];
                p.sung = col[1];
                switch (karaoke) {
                    case FILL -> p.progress = LyricsController.progress(lines, wl.index, c.pos);
                    case LINE -> p.progress = 1f;
                    case NONE -> p.plain = col[1];
                }
            } else {
                int[] old = colors(0.5f);
                p.plain = karaoke == Config.Karaoke.NONE ? col[2] : old[1];
                p.alpha *= Config.INACTIVE_ALPHA.get().floatValue();
            }
            TextPainter.draw(ps, buf, font, p);
        }
        ps.popPose();
    }
}
