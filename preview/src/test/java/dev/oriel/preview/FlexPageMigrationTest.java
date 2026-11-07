package dev.oriel.preview;

import dev.oriel.engine.css.StyleEngine;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.style.Prop;
import dev.oriel.kit.OrielKit;
import dev.oriel.preview.render.ImageCanvas;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Pixel and computed-style differential at identical DOM/animation state, without rewriting golden files. */
class FlexPageMigrationTest {
    @TestFactory
    Stream<DynamicTest> pages() throws Exception {
        OrielKit.register();
        List<Path> pages = new ArrayList<>();
        Path root = Path.of("..");
        for (String directory : List.of("kit/src/main/resources/assets/oriel/ui",
                "common/src/main/resources/assets/oriel/oriel/showcase", "tools/mcp/examples", "tools/h5ref/corpus")) {
            try (var files = Files.walk(root.resolve(directory))) {
                pages.addAll(files.filter(p -> p.toString().endsWith(".html")).sorted().toList());
            }
        }
        return pages.stream().flatMap(page -> Stream.of(640, 427).map(width ->
                DynamicTest.dynamicTest(page + " @" + width, () -> compare(page, width))));
    }

    private static void compare(Path page, int width) throws Exception {
        var jar = MinecraftAssets.findClientJar();
        assertTrue(jar.isPresent(), "Flex pixel migration requires the fixed Minecraft font resources");
        String before = System.getProperty("oriel.layout.flex");
        System.setProperty("oriel.layout.flex", "old");
        try (TestScene test = TestScene.open(page, jar, width, width == 640 ? 360 : 240, 1)) {
            for (int frame = 0; frame < 20; frame++)
                test.scene().frame(frame * (1000.0 / 60), test.width(), test.height(), 1);
            assertNull(test.scene().error(), page.toString());
            var document = test.scene().document();
            try {
                document.invalidateLayout();
                document.flushLayout();
                for (var content : document.replacedContents()) content.afterLayout();
                String oldStyle = styles(document.documentElement());
                int[] oldPixels = pixels(test);
                System.setProperty("oriel.layout.flex", "both");
                document.invalidateLayout();
                document.flushLayout();
                // Match the production frame's post-layout host phase (scene-anchor projection, etc.).
                for (var content : document.replacedContents()) content.afterLayout();
                assertNull(document.error(), page.toString());
                assertEquals(oldStyle, styles(document.documentElement()), "computed style: " + page);
                assertArrayEquals(oldPixels, pixels(test), "RGBA: " + page);
            } finally { document.close(); }
        } finally {
            if (before == null) System.clearProperty("oriel.layout.flex");
            else System.setProperty("oriel.layout.flex", before);
        }
    }

    private static int[] pixels(TestScene test) {
        BufferedImage image = new BufferedImage(test.width(), test.height(), BufferedImage.TYPE_INT_ARGB);
        ImageCanvas canvas = new ImageCanvas(image, 1, test.assets(), new MinecraftFont(test.assets()));
        try { test.scene().paint(canvas); } finally { canvas.dispose(); }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static String styles(Element element) {
        StringBuilder result = new StringBuilder();
        appendStyles(element, result);
        return result.toString();
    }

    private static void appendStyles(Element element, StringBuilder result) {
        result.append(element).append('\n');
        if (element.style != null) for (Prop property : Prop.values())
            result.append(property.cssName).append('=').append(StyleEngine.computedValue(element.style, property.cssName)).append('\n');
        for (Element child : element.children()) appendStyles(child, result);
    }
}
