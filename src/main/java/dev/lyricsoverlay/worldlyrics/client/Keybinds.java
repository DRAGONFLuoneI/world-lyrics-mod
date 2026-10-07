package dev.lyricsoverlay.worldlyrics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lyricsoverlay.worldlyrics.Lang;
import dev.lyricsoverlay.worldlyrics.bridge.BridgeClient;
import dev.lyricsoverlay.worldlyrics.client.gui.SettingsScreen;
import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Клавиши мода. Все можно переназначить в «Настройки → Управление». */
public final class Keybinds {
    private Keybinds() {
    }

    public static final String CATEGORY = "key.categories.worldlyrics";

    public static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_K);
    public static final KeyMapping SETTINGS = key("settings", GLFW.GLFW_KEY_O);
    public static final KeyMapping OFFSET_PLUS = key("offset_plus", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping OFFSET_MINUS = key("offset_minus", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping ANCHOR = key("anchor", GLFW.GLFW_KEY_H);
    public static final KeyMapping MODE = key("mode", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping RELOAD = key("reload", InputConstants.UNKNOWN.getValue());

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.worldlyrics." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent e) {
        e.register(TOGGLE);
        e.register(SETTINGS);
        e.register(OFFSET_PLUS);
        e.register(OFFSET_MINUS);
        e.register(ANCHOR);
        e.register(MODE);
        e.register(RELOAD);
    }

    static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LyricsController ctl = LyricsController.INSTANCE;
        while (TOGGLE.consumeClick()) {
            boolean on = !Config.ENABLED.get();
            Config.ENABLED.set(on);
            Config.save();
            ctl.toast(on ? Lang.t("Тексты: включены", "Lyrics: on") : Lang.t("Тексты: выключены", "Lyrics: off"));
        }
        while (SETTINGS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new SettingsScreen(null));
            }
        }
        while (OFFSET_PLUS.consumeClick()) {
            BridgeClient.INSTANCE.action("offset_plus");
            ctl.expectOffsetToast();
        }
        while (OFFSET_MINUS.consumeClick()) {
            BridgeClient.INSTANCE.action("offset_minus");
            ctl.expectOffsetToast();
        }
        while (ANCHOR.consumeClick()) {
            ctl.anchorHere(mc);
            ctl.toast(Lang.t("Текст закреплён здесь", "Lyrics anchored here"));
        }
        while (MODE.consumeClick()) {
            Config.Mode[] all = Config.Mode.values();
            Config.Mode next = all[(Config.MODE.get().ordinal() + 1) % all.length];
            Config.MODE.set(next);
            Config.save();
            ctl.onModeChanged();
            ctl.toast(Lang.t("Режим: ", "Mode: ") + modeName(next));
        }
        while (RELOAD.consumeClick()) {
            BridgeClient.INSTANCE.action("reload_lyrics");
            ctl.toast(Lang.t("Ищу текст заново…", "Reloading lyrics…"));
        }
    }

    public static String modeName(Config.Mode m) {
        return switch (m) {
            case FOLLOW -> Lang.t("перед камерой", "in front of camera");
            case SCATTER -> Lang.t("разбросано в мире", "scattered in world");
            case ANCHORED -> Lang.t("закреплено на месте", "anchored");
            case HUD -> Lang.t("только HUD", "HUD only");
        };
    }
}
