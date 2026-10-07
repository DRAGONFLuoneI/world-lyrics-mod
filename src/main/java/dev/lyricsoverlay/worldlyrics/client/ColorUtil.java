package dev.lyricsoverlay.worldlyrics.client;

import net.minecraft.util.Mth;

/** Цвета в формате ARGB (int) и строках «#AARRGGBB» / «#RRGGBB». */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static boolean isValid(String s) {
        return parseOrNull(s) != null;
    }

    public static Integer parseOrNull(String s) {
        if (s == null) {
            return null;
        }
        String h = s.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        } else if (h.startsWith("0x") || h.startsWith("0X")) {
            h = h.substring(2);
        }
        if (h.length() != 6 && h.length() != 8) {
            return null;
        }
        try {
            long v = Long.parseLong(h, 16);
            if (h.length() == 6) {
                v |= 0xFF000000L;
            }
            return (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int parse(String s, int fallback) {
        Integer v = parseOrNull(s);
        return v == null ? fallback : v;
    }

    public static String format(int argb) {
        return String.format("#%08X", argb);
    }

    public static int alpha(int c) {
        return c >>> 24;
    }

    public static int withAlpha(int c, float alphaMul) {
        int a = Math.round((c >>> 24) * Mth.clamp(alphaMul, 0f, 1f));
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Радужный цвет (ARGB, непрозрачный) по фазе 0..1. */
    public static int rainbow(float phase) {
        float h = phase - (float) Math.floor(phase);
        return 0xFF000000 | Mth.hsvToRgb(h, 0.65f, 1.0f);
    }
}
