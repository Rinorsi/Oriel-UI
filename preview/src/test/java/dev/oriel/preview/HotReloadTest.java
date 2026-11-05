package dev.oriel.preview;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class HotReloadTest {
    @TempDir Path root;

    @Test void fileSavesUseTheSameDocumentForCssAndStateHandoffForHtml() throws Exception {
        Path folder = Files.createDirectories(root.resolve("assets/reload/ui"));
        Path html = folder.resolve("page.html"), css = folder.resolve("page.css");
        Files.writeString(html, "<link rel=stylesheet href=page.css><input id=field><p id=text>before</p>");
        Files.writeString(css, "p { color:red }");
        class Controller implements PageControllers.Controller {
            int value, saved, restored;
            public void attach(Document doc) {}
            public Object saveState() { saved++; return value; }
            public void restoreState(Object state) { restored++; value = (Integer) state; }
        }
        PageControllers.register("reload:ui/page.html", Controller::new);
        try (MinecraftAssets assets = MinecraftAssets.open(List.of(root), Optional.empty())) {
            PageScene scene = new PageScene(new PreviewHost(assets, new MinecraftFont(assets)),
                    "reload:ui/page.html", null, new Viewport(320, 240, 2));
            scene.frame(0, 320, 240, 2);
            Document before = scene.document();
            Controller controller = (Controller) before.controller();
            controller.value = 42;
            var field = before.getElementById("field");
            field.setValue("输入中的文字"); field.focus();
            Files.writeString(css, "p { color:blue }");
            scene.reloadChanged(List.of(css));
            scene.frame(16, 320, 240, 2);
            assertSame(before, scene.document());
            assertSame(controller, scene.document().controller());
            assertEquals(0, controller.saved);
            assertSame(field, scene.document().getElementById("field"));
            assertTrue(field.isFocused());
            assertEquals("输入中的文字", field.value());
            assertEquals(0xFF0000FF, before.getElementById("text").style.color);
            Files.writeString(html, "<p id=text>after</p>");
            scene.reloadChanged(List.of(html));
            scene.frame(32, 320, 240, 2);
            assertNotSame(before, scene.document());
            assertEquals(1, controller.saved);
            Controller restored = (Controller) scene.document().controller();
            assertEquals(1, restored.restored);
            assertEquals(42, restored.value);
            assertEquals("after", scene.document().getElementById("text").textContent());
            assertNull(scene.error());
            scene.document().close();
        }
    }
}
