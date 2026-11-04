package dev.oriel.engine.architecture;

import dev.oriel.engine.host.Host;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainBoundaryTest {
    @Test
    void coreArtifactDoesNotContainMovedMinecraftDomainClasses() throws Exception {
        URL core = Host.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[] {core}, ClassLoader.getPlatformClassLoader())) {
            assertNotNull(isolated.loadClass(Host.class.getName()));
            for (String name : List.of("dev.oriel.engine.scene.SceneModel", "dev.oriel.engine.scene.Nbt",
                    "dev.oriel.engine.style.EntityFraming", "dev.oriel.engine.host.MinecraftGlyphs")) {
                assertThrows(ClassNotFoundException.class, () -> isolated.loadClass(name), name);
            }
        }
    }
}
