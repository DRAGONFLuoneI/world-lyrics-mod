package dev.lyricsoverlay.worldlyrics;

import com.mojang.logging.LogUtils;
import dev.lyricsoverlay.worldlyrics.client.ClientInit;
import dev.lyricsoverlay.worldlyrics.config.Config;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * World Lyrics — тексты играющих песен прямо в мире Minecraft.
 * Мод только клиентский: на сервере он ничего не делает.
 */
@Mod(WorldLyrics.MOD_ID)
public final class WorldLyrics {
    public static final String MOD_ID = "worldlyrics";
    public static final Logger LOG = LogUtils.getLogger();

    public WorldLyrics() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC, "worldlyrics-client.toml");
            ClientInit.init(FMLJavaModLoadingContext.get().getModEventBus());
        } else {
            LOG.info("World Lyrics — клиентский мод, на сервере он не нужен.");
        }
    }
}
