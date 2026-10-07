package dev.lyricsoverlay.worldlyrics;

import net.minecraft.client.Minecraft;

/** Русский интерфейс, если в игре выбран русский язык, иначе английский. */
public final class Lang {
    private Lang() {
    }

    public static boolean ru() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return true;
        }
        String code = mc.options.languageCode;
        return code != null && (code.startsWith("ru") || code.startsWith("uk") || code.startsWith("be"));
    }

    public static String t(String ru, String en) {
        return ru() ? ru : en;
    }
}
