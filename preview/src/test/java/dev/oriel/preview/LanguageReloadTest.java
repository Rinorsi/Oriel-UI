package dev.oriel.preview;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.kit.OrielKit;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LanguageReloadTest {
    @Test void anOpenPreviewReloadsItsControllerWhenTheHostLanguageChanges() throws Exception {
        OrielKit.register();
        try (MinecraftAssets assets = MinecraftAssets.open(List.of(Path.of("../kit/src/main/resources")), Optional.empty())) {
            PreviewHost host = new PreviewHost(assets, new MinecraftFont(assets));
            host.setLanguage("zh_cn");
            PageScene scene = new PageScene(host, OrielKit.SETTINGS, null, new Viewport(640, 360, 2));
            scene.frame(0, 640, 360, 2);
            Document before = scene.document();
            assertEquals("设置", before.querySelector("h1").textContent());
            host.setLanguage("en_us");
            scene.frame(16, 640, 360, 2);
            assertNotSame(before, scene.document());
            assertNull(scene.error());
            assertEquals("Settings", scene.document().querySelector("h1").textContent());
            scene.document().close();
        }
    }
}
