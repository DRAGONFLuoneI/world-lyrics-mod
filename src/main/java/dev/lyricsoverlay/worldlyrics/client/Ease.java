package dev.lyricsoverlay.worldlyrics.client;

/** Функции сглаживания для анимаций. */
public final class Ease {
    private Ease() {
    }

    public static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    public static float outCubic(float x) {
        float t = 1f - clamp01(x);
        return 1f - t * t * t;
    }

    public static float inOutSine(float x) {
        return (float) (-(Math.cos(Math.PI * clamp01(x)) - 1.0) / 2.0);
    }

    /** Перелёт с отскоком («пружинка»), bounce = 0 — без перелёта. */
    public static float outBack(float x, float bounce) {
        float c1 = 1.70158f * bounce;
        float c3 = c1 + 1f;
        float t = clamp01(x) - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    /** Затухающие колебания: 1 при x=0, стремится к 0 с покачиванием. */
    public static float wobble(float seconds, float freq, float decay) {
        return (float) (Math.exp(-decay * seconds) * Math.cos(2 * Math.PI * freq * seconds));
    }

    /** Детерминированный «случай» 0..1 по зерну и индексу (для рассыпания букв). */
    public static float hash(float seed, int i) {
        double v = Math.sin(seed * 127.1 + i * 311.7) * 43758.5453;
        return (float) (v - Math.floor(v));
    }
}
