package dev.oriel.mod.internal.platform;


import java.nio.file.Path;

/** Loader-specific facilities that common code needs. */
public interface Platform {
    public boolean isDevelopmentEnvironment();

    public Path configDir();

    public boolean isModLoaded(String modId);
}
