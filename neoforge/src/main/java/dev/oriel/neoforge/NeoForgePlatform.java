package dev.oriel.neoforge;

import dev.oriel.mod.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatform implements Platform {
    @Override public boolean isDevelopmentEnvironment() { return !FMLEnvironment.isProduction(); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }
}
