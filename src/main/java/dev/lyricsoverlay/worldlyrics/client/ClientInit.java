package dev.lyricsoverlay.worldlyrics.client;

import dev.lyricsoverlay.worldlyrics.bridge.BridgeClient;
import dev.lyricsoverlay.worldlyrics.client.gui.SettingsScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Регистрация всего клиентского: клавиши, отрисовка, HUD, экран настроек, мост. */
public final class ClientInit {
    private ClientInit() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(Keybinds::register);
        modBus.addListener(HudOverlay::register);
        modBus.addListener(ClientInit::onSetup);

        MinecraftForge.EVENT_BUS.addListener(Keybinds::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(WorldRenderer::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(CameraEffects::onComputeFov);
        MinecraftForge.EVENT_BUS.addListener(CameraEffects::onCameraAngles);
        MinecraftForge.EVENT_BUS.addListener(LyricsController::onLogout);

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }

    private static void onSetup(FMLClientSetupEvent event) {
        BridgeClient.INSTANCE.start();
    }
}
