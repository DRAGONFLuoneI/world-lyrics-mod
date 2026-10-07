package dev.lyricsoverlay.worldlyrics.client;

import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraftforge.client.event.ViewportEvent;

/** «Дыхание» поля зрения и лёгкая тряска камеры на каждой новой строке. */
public final class CameraEffects {
    private CameraEffects() {
    }

    static void onComputeFov(ViewportEvent.ComputeFov e) {
        double amount = Config.FOV_PULSE.get();
        LyricsController c = LyricsController.INSTANCE;
        if (amount <= 0 || c.alpha <= 0.01f || !Config.ENABLED.get()) {
            return;
        }
        double age = c.pulseAge(System.nanoTime());
        if (age > 1.5) {
            return;
        }
        double env = (1 - Math.exp(-age * 30)) * Math.exp(-age * 4);
        e.setFOV(e.getFOV() * (1 + amount * env * c.alpha));
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        LyricsController c = LyricsController.INSTANCE;
        if (!Config.CAMERA_SHAKE.get() || c.alpha <= 0.01f || !Config.ENABLED.get()) {
            return;
        }
        double age = c.pulseAge(System.nanoTime());
        if (age > 0.9) {
            return;
        }
        double amp = Config.CAMERA_SHAKE_AMOUNT.get() * Math.exp(-age * 7) * c.alpha;
        e.setRoll((float) (e.getRoll() + amp * Math.sin(age * 38)));
        e.setPitch((float) (e.getPitch() + amp * 0.4 * Math.sin(age * 31 + 1.3)));
    }
}
