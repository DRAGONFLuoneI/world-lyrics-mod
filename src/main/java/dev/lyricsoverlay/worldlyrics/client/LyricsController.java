package dev.lyricsoverlay.worldlyrics.client;

import dev.lyricsoverlay.worldlyrics.Lang;
import dev.lyricsoverlay.worldlyrics.bridge.BridgeClient;
import dev.lyricsoverlay.worldlyrics.bridge.Snapshot;
import dev.lyricsoverlay.worldlyrics.client.gui.SettingsScreen;
import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Мозг мода: следит за треком и текущей строкой, управляет строками в мире, пружинами,
 * видимостью, частицами и эффектами. Вызывается один раз за кадр из {@link WorldRenderer}.
 */
public final class LyricsController {
    public static final LyricsController INSTANCE = new LyricsController();

    final List<WorldLine> lines = new ArrayList<>();
    private final RandomSource rnd = RandomSource.create();

    Snapshot snap = Snapshot.EMPTY;
    double pos;
    int idx = -2;
    private int lastRev = Integer.MIN_VALUE;
    private String lastKey;
    long idxChanged = System.nanoTime();
    private long lastFrame;

    float alpha;
    float scroll;
    private float scrollV;
    private boolean snapScroll = true;

    Vec3 followPos;
    private Vec3 followVel = Vec3.ZERO;
    Vec3 anchor;

    long pulseAt;                 // момент начала текущей строки — для эффектов
    private double trailAcc;

    String titleText = "";
    String titleSub = "";
    long titleAt;

    private volatile String toast = "";
    private volatile long toastUntil;
    private boolean offsetToastPending;
    private long offsetToastDeadline;
    private int lastOffsetMs;
    private BridgeClient.Status lastStatus;

    private LyricsController() {
    }

    // =================================================================== кадр

    void frame(Minecraft mc, Camera cam) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : (float) Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;

        BridgeClient br = BridgeClient.INSTANCE;
        snap = br.snapshot();
        pos = br.position();
        statusToasts(br, now);
        offsetToasts(now);

        if (!Objects.equals(snap.key(), lastKey)) {
            onTrackChange(mc, cam, now);
        }
        if (snap.rev() != lastRev) {
            lastRev = snap.rev();
            idx = -2;
            snapScroll = true;
            killLyricLines(now);
        }
        int ni = findIndex(snap.lines(), pos);
        if (ni != idx) {
            int old = idx;
            idx = ni;
            onLineChange(mc, cam, old, ni, now);
        }

        updateVisibility(mc, br, dt);
        stepScroll(dt);
        stepFollow(cam, dt);
        if (Config.MODE.get() == Config.Mode.ANCHORED && anchor == null && mc.player != null) {
            anchorHere(mc);
        }
        updateWorldLines(now);
        trail(mc, cam, dt);
    }

    private void updateVisibility(Minecraft mc, BridgeClient br, float dt) {
        boolean want = Config.ENABLED.get()
                && br.status() == BridgeClient.Status.OK
                && snap.hasTrack()
                && !(Config.HIDE_WHEN_PAUSED.get() && br.pausedSeconds() > Config.PAUSED_HIDE_DELAY.get())
                && !(Config.HIDE_WITH_F1.get() && mc.options.hideGui)
                && !(Config.HIDE_IN_MENUS.get() && mc.screen != null && !(mc.screen instanceof SettingsScreen));
        float target = want ? 1f : 0f;
        alpha += (target - alpha) * (1f - (float) Math.exp(-dt * 7.0));
        if (Math.abs(target - alpha) < 0.003f) {
            alpha = target;
        }
    }

    private void stepScroll(float dt) {
        float target = Math.max(0, idx);
        if (snapScroll || Math.abs(target - scroll) > 3.5f) {
            scroll = target;
            scrollV = 0;
            snapScroll = false;
            return;
        }
        double k = Config.LINE_STIFFNESS.get();
        double zeta = Mth.clamp(1.0 - 0.85 * Config.LINE_BOUNCE.get(), 0.12, 1.0);
        double c = 2 * zeta * Math.sqrt(k);
        int n = Math.max(1, (int) (dt / 0.004f) + 1);
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            double a = -k * (scroll - target) - c * scrollV;
            scrollV += (float) (a * h);
            scroll += scrollV * h;
        }
        if (Math.abs(scroll - target) < 1e-3f && Math.abs(scrollV) < 1e-3f) {
            scroll = target;
            scrollV = 0;
        }
    }

    private void stepFollow(Camera cam, float dt) {
        Vec3 target = followTarget(cam);
        if (followPos == null || followPos.distanceToSqr(target) > 30 * 30) {
            followPos = target;
            followVel = Vec3.ZERO;
            return;
        }
        double k = Config.FOLLOW_STIFFNESS.get();
        double zeta = Mth.clamp(1.0 - 0.85 * Config.FOLLOW_BOUNCE.get(), 0.12, 1.0);
        double c = 2 * zeta * Math.sqrt(k);
        int n = Math.max(1, (int) (dt / 0.004f) + 1);
        double h = dt / (double) n;
        for (int i = 0; i < n; i++) {
            Vec3 acc = followPos.subtract(target).scale(-k).subtract(followVel.scale(c));
            followVel = followVel.add(acc.scale(h));
            followPos = followPos.add(followVel.scale(h));
        }
    }

    /** Базис камеры: вперёд, вправо, вверх (с учётом настройки «следовать за наклоном»). */
    static Vec3[] basis(Camera cam, boolean withPitch) {
        double yaw = Math.toRadians(cam.getYRot());
        Vec3 fwd;
        if (withPitch) {
            double pitch = Math.toRadians(cam.getXRot());
            fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        } else {
            fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(worldUp);
        if (right.lengthSqr() < 1e-6) {
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();
        return new Vec3[]{fwd, right, up};
    }

    private Vec3 followTarget(Camera cam) {
        Vec3[] b = basis(cam, Config.FOLLOW_PITCH.get());
        return cam.getPosition()
                .add(b[0].scale(Config.FOLLOW_DISTANCE.get()))
                .add(b[2].scale(Config.FOLLOW_HEIGHT.get()))
                .add(b[1].scale(Config.FOLLOW_SIDE.get()));
    }

    // ========================================================== события

    private void onTrackChange(Minecraft mc, Camera cam, long now) {
        boolean first = lastKey == null;
        lastKey = snap.key();
        idx = -2;
        snapScroll = true;
        long t = now;
        for (WorldLine wl : lines) {
            wl.kill(t);
        }
        if (Config.MODE.get() == Config.Mode.ANCHORED && anchor == null && mc.player != null) {
            anchorHere(mc);
        }
        if (!snap.hasTrack() || !Config.TITLE_CARD.get() || (first && !Config.ENABLED.get())) {
            return;
        }
        titleText = snap.title();
        titleSub = snap.artist();
        titleAt = now;
        if (Config.TITLE_IN_HUD.get() || Config.MODE.get() == Config.Mode.HUD) {
            return;
        }
        Vec3 p;
        if (Config.MODE.get() == Config.Mode.ANCHORED && anchor != null) {
            p = anchor.add(0, Config.ANCHOR_HEIGHT.get() + 1.6, 0);
        } else {
            Vec3[] b = basis(cam, false);
            p = cam.getPosition().add(b[0].scale(Config.FOLLOW_DISTANCE.get() + 3.0)).add(0, 0.9, 0);
        }
        lines.add(new WorldLine(snap.title(), snap.artist(), -1, true, p, yawTowards(cam.getPosition(), p), now,
                rnd.nextFloat() * 100f));
        burst(mc, cam, p, snap.title(), 1.6f, Config.PARTICLE_COUNT.get() * 2);
    }

    private void onLineChange(Minecraft mc, Camera cam, int old, int ni, long now) {
        idxChanged = now;
        boolean initial = old == -2;
        boolean jump = !initial && (ni < old || ni > old + 3);
        String text = ni >= 0 && ni < snap.lines().size() ? snap.lines().get(ni).text() : "";
        boolean blank = text.isBlank();
        Config.Mode mode = Config.MODE.get();

        Vec3 at = null;
        if (mode == Config.Mode.SCATTER) {
            if (jump) {
                killLyricLines(now);
            }
            for (WorldLine wl : lines) {
                if (!wl.title) {
                    wl.current = false;
                }
            }
            if (ni >= 0 && (!blank || Config.SHOW_NOTE.get())) {
                Vec3 p = scatterPos(mc, cam);
                WorldLine wl = new WorldLine(blank ? "♪" : text, "", ni, false, p, yawTowards(cam.getPosition(), p), now,
                        rnd.nextFloat() * 100f);
                wl.current = true;
                lines.add(wl);
                at = p;
            }
            enforceKeep(now);
        } else if (mode == Config.Mode.FOLLOW) {
            at = followPos;
        } else if (mode == Config.Mode.ANCHORED && anchor != null) {
            at = anchor.add(0, Config.ANCHOR_HEIGHT.get(), 0);
        }

        if (!initial && ni >= 0 && alpha > 0.05f) {
            pulseAt = now;
            if (at != null && !blank) {
                burst(mc, cam, at, text, 1f, Config.PARTICLE_COUNT.get());
            }
        }
    }

    public void onModeChanged() {
        long now = System.nanoTime();
        killLyricLines(now);
        followPos = null;
        snapScroll = true;
        idx = -2;
    }

    public void anchorHere(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3[] b = basis(cam, false);
        anchor = mc.player.position().add(b[0].scale(Math.max(2.5, Config.FOLLOW_DISTANCE.get())));
    }

    static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        LyricsController c = INSTANCE;
        c.lines.clear();
        c.followPos = null;
        c.anchor = null;
        c.idx = -2;
        c.lastKey = null;
        c.snapScroll = true;
    }

    // ============================================================ строки в мире

    private void killLyricLines(long now) {
        for (WorldLine wl : lines) {
            if (!wl.title) {
                wl.kill(now);
                wl.current = false;
            }
        }
    }

    private void enforceKeep(long now) {
        int keep = Config.SCATTER_KEEP.get();
        List<WorldLine> old = new ArrayList<>();
        for (WorldLine wl : lines) {
            if (!wl.title && !wl.current && wl.die < 0) {
                old.add(wl);
            }
        }
        old.sort(Comparator.comparingLong(w -> w.born));
        for (int i = 0; i < old.size() - keep; i++) {
            old.get(i).kill(now);
        }
    }

    private void updateWorldLines(long now) {
        double life = Config.SCATTER_LIFETIME.get();
        double titleLife = Config.TITLE_TIME.get();
        double gone = Config.DISAPPEAR_TIME.get();
        lines.removeIf(wl -> {
            if (wl.die < 0) {
                double limit = wl.title ? titleLife : (wl.current ? Math.max(life, 30) : life);
                if (wl.age(now) > limit) {
                    wl.kill(now);
                }
            }
            return wl.dying(now) > gone + 0.05;
        });
    }

    private Vec3 scatterPos(Minecraft mc, Camera cam) {
        Level level = mc.level;
        Vec3 eye = cam.getPosition();
        double min = Math.min(Config.SCATTER_MIN.get(), Config.SCATTER_MAX.get());
        double max = Math.max(Config.SCATTER_MIN.get(), Config.SCATTER_MAX.get());
        double hMin = Math.min(Config.SCATTER_H_MIN.get(), Config.SCATTER_H_MAX.get());
        double hMax = Math.max(Config.SCATTER_H_MIN.get(), Config.SCATTER_H_MAX.get());
        float spread = Config.SCATTER_SPREAD.get();
        Vec3 best = null;
        for (int attempt = 0; attempt < 18 && level != null; attempt++) {
            double ang = Math.toRadians(cam.getYRot() + (rnd.nextFloat() - 0.5f) * spread);
            double d = min + rnd.nextDouble() * (max - min);
            double x = eye.x - Math.sin(ang) * d;
            double z = eye.z + Math.cos(ang) * d;
            double y;
            Double ground = Config.SCATTER_GROUND.get() ? groundY(level, x, z, eye.y) : null;
            if (ground != null) {
                y = ground + Config.SCATTER_GROUND_HEIGHT.get() + rnd.nextDouble() * Math.max(0, hMax - hMin) * 0.5;
            } else {
                y = eye.y + hMin + rnd.nextDouble() * (hMax - hMin);
            }
            Vec3 p = new Vec3(x, y, z);
            if (!free(level, p) || tooClose(p)) {
                continue;
            }
            if (Config.SCATTER_VISIBLE.get() && mc.player != null) {
                HitResult hit = level.clip(new ClipContext(eye, p, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(eye) < p.distanceToSqr(eye) - 0.5) {
                    if (best == null) {
                        best = p;
                    }
                    continue;
                }
            }
            return p;
        }
        if (best != null && !Config.SCATTER_VISIBLE.get()) {
            return best;
        }
        Vec3[] b = basis(cam, false);
        return eye.add(b[0].scale((min + max) / 2.0)).add(0, (hMin + hMax) / 4.0, 0);
    }

    private static boolean free(Level level, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        BlockState s = level.getBlockState(bp);
        return s.getCollisionShape(level, bp).isEmpty() && level.getFluidState(bp).isEmpty();
    }

    private static Double groundY(Level level, double x, double z, double fromY) {
        int top = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(fromY) + 8);
        int bottom = Math.max(level.getMinBuildHeight(), top - 40);
        for (int y = top; y >= bottom; y--) {
            BlockPos bp = BlockPos.containing(x, y, z);
            BlockState s = level.getBlockState(bp);
            boolean solid = !s.getCollisionShape(level, bp).isEmpty() || !level.getFluidState(bp).isEmpty();
            if (solid) {
                return (double) (y + 1);
            }
        }
        return null;
    }

    private boolean tooClose(Vec3 p) {
        for (WorldLine wl : lines) {
            if (wl.die < 0 && wl.pos.distanceToSqr(p) < 2.4 * 2.4) {
                return true;
            }
        }
        return false;
    }

    static float yawTowards(Vec3 eye, Vec3 p) {
        Vec3 d = p.subtract(eye);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    // ============================================================== частицы

    static SimpleParticleType particle(Config.Particles p) {
        return switch (p) {
            case NONE -> null;
            case NOTE -> ParticleTypes.NOTE;
            case END_ROD -> ParticleTypes.END_ROD;
            case ENCHANT -> ParticleTypes.ENCHANT;
            case GLOW -> ParticleTypes.GLOW;
            case CHERRY -> ParticleTypes.CHERRY_LEAVES;
            case SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case WAX -> ParticleTypes.WAX_ON;
            case SNOW -> ParticleTypes.SNOWFLAKE;
            case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
            case HAPPY -> ParticleTypes.HAPPY_VILLAGER;
            case FIREWORK -> ParticleTypes.FIREWORK;
        };
    }

    private void burst(Minecraft mc, Camera cam, Vec3 at, String text, float sizeMul, int count) {
        SimpleParticleType type = particle(Config.PARTICLES.get());
        if (type == null || mc.level == null || count <= 0) {
            return;
        }
        Vec3[] b = basis(cam, false);
        double width = Math.max(0.6, mc.font.width(text) * 0.025 * Config.SCALE.get() * sizeMul);
        for (int i = 0; i < count; i++) {
            double side = (rnd.nextDouble() - 0.5) * width;
            double up = (rnd.nextDouble() - 0.3) * 0.5 * sizeMul;
            Vec3 p = at.add(b[1].scale(side)).add(0, up, 0);
            spawn(mc, type, p, i);
        }
    }

    private void spawn(Minecraft mc, SimpleParticleType type, Vec3 p, int i) {
        if (type == ParticleTypes.NOTE) {
            mc.level.addParticle(type, p.x, p.y, p.z, rnd.nextDouble(), 0, 0);
        } else {
            mc.level.addParticle(type, p.x, p.y, p.z, (rnd.nextDouble() - 0.5) * 0.04,
                    0.01 + rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.04);
        }
    }

    private void trail(Minecraft mc, Camera cam, float dt) {
        if (!Config.TRAIL.get() || alpha < 0.3f || idx < 0 || mc.level == null || !BridgeClient.INSTANCE.playing()) {
            trailAcc = 0;
            return;
        }
        SimpleParticleType type = particle(Config.PARTICLES.get());
        if (type == null) {
            return;
        }
        Vec3 at = switch (Config.MODE.get()) {
            case FOLLOW -> followPos;
            case ANCHORED -> anchor == null ? null : anchor.add(0, Config.ANCHOR_HEIGHT.get(), 0);
            case SCATTER -> lines.stream().filter(w -> w.current && w.die < 0).map(w -> w.pos).findFirst().orElse(null);
            case HUD -> null;
        };
        if (at == null) {
            return;
        }
        String text = idx < snap.lines().size() ? snap.lines().get(idx).text() : "";
        double width = Math.max(0.6, mc.font.width(text) * 0.025 * Config.SCALE.get());
        Vec3[] b = basis(cam, false);
        trailAcc += Config.TRAIL_RATE.get() * dt;
        while (trailAcc >= 1) {
            trailAcc -= 1;
            Vec3 p = at.add(b[1].scale((rnd.nextDouble() - 0.5) * width)).add(0, (rnd.nextDouble() - 0.5) * 0.4, 0);
            spawn(mc, type, p, 0);
        }
    }

    // ============================================================== караоке

    static int findIndex(List<Snapshot.Line> lines, double pos) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).t() <= pos) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    /** Доля спетой строки 0..1 — оценка по длине строки и паузе до следующей. */
    static float progress(List<Snapshot.Line> lines, int i, double pos) {
        if (i < 0 || i >= lines.size()) {
            return 0f;
        }
        double t0 = lines.get(i).t();
        double est = Math.max(1.2, lines.get(i).text().length() * 0.075 + 0.6);
        double dur;
        if (i + 1 < lines.size()) {
            double gap = lines.get(i + 1).t() - t0;
            dur = gap <= est * 1.8 ? gap : est * 1.4;
        } else {
            dur = est * 1.4;
        }
        return Ease.clamp01((pos - t0) / Math.max(0.25, dur));
    }

    // ============================================================== тосты

    public void toast(String text) {
        toast = text;
        toastUntil = System.nanoTime() + 2_200_000_000L;
    }

    public String activeToast(long now) {
        return now < toastUntil ? toast : "";
    }

    public float toastAlpha(long now) {
        return Ease.clamp01((toastUntil - now) / 3e8);
    }

    public void expectOffsetToast() {
        offsetToastPending = true;
        offsetToastDeadline = System.nanoTime() + 3_000_000_000L;
    }

    private void offsetToasts(long now) {
        int off = snap.offsetMs();
        if (offsetToastPending) {
            if (off != lastOffsetMs) {
                toast(Lang.t("Сдвиг трека: ", "Track offset: ") + String.format(java.util.Locale.ROOT, "%+.2f", off / 1000.0)
                        + Lang.t(" с", " s"));
                offsetToastPending = false;
            } else if (now > offsetToastDeadline) {
                offsetToastPending = false;
            }
        }
        lastOffsetMs = off;
    }

    private void statusToasts(BridgeClient br, long now) {
        BridgeClient.Status s = br.status();
        if (s == lastStatus) {
            return;
        }
        BridgeClient.Status prev = lastStatus;
        lastStatus = s;
        if (!Config.SHOW_STATUS.get() || s == BridgeClient.Status.CONNECTING || s == BridgeClient.Status.DISABLED) {
            return;
        }
        switch (s) {
            case OK -> toast(Lang.t("Подключено к Lyrics Overlay", "Connected to Lyrics Overlay"));
            case NO_APP -> {
                if (prev == BridgeClient.Status.OK || prev == BridgeClient.Status.CONNECTING || prev == null) {
                    toast(Lang.t("Нет связи с Lyrics Overlay — запустите программу (порт ", "No connection to Lyrics Overlay — start the app (port ")
                            + Config.PORT.get() + ")");
                }
            }
            case ERROR -> toast(Lang.t("Ошибка связи: ", "Bridge error: ") + br.error());
            default -> {
            }
        }
    }

    /** Секунды с начала текущей строки — для пульса, вспышки и тряски. */
    public double pulseAge(long now) {
        return pulseAt == 0 ? 99.0 : (now - pulseAt) / 1e9;
    }
}
