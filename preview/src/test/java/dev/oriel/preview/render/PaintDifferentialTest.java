package dev.oriel.preview.render;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.paint.DisplayList;
import dev.oriel.engine.paint.Painter;
import dev.oriel.preview.host.PreviewHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaintDifferentialTest {
    @TempDir Path dir;

    @Test
    void oldNewBothAndReplayMatchEveryRgbaIncludingEffectBoundaries() throws IOException {
        MinecraftAssets assets = new TestPack(dir).assets();
        MinecraftFont font = new MinecraftFont(assets);
        for (int scale : new int[] {1, 2}) {
            for (String effect : List.of("", "opacity:.6", "filter:blur(2px)",
                    "clip-path:circle(40%)", "clip-path:polygon(0% 0%,100% 30%,60% 100%)",
                    "mask-image:linear-gradient(black,transparent)", "mix-blend-mode:screen",
                    "backdrop-filter:blur(2px)", "transform:rotate(15deg)", "shape-rendering:crispEdges")) {
                String html = "<html><body style='margin:0;background:#236'><div id='outer' style='width:100px;height:70px;overflow:hidden;border-radius:9px'>"
                        + "<div id='fx' style='width:80px;height:50px;background:radial-gradient(#fa08,#06ff);border:3px solid #fff8;"
                        + "border-radius:11px;box-shadow:2px 3px 4px #0008;" + effect + "'></div></div></body></html>";
                Document document = Document.parse(new PreviewHost(assets, font), "test:paint", html, null, new Viewport(120, 90, scale));
                document.frame(0);
                Painter painter = document.painter();
                painter.trace(true);
                ImageCanvas old = canvas(scale, assets, font);
                painter.mode(Painter.Mode.OLD);
                document.paint(old);
                assertNull(document.error());
                DisplayList original = painter.lastDisplayList();
                int[] expected = pixels(old);
                for (Painter.Mode mode : new Painter.Mode[] {Painter.Mode.NEW, Painter.Mode.NEW, Painter.Mode.BOTH}) {
                    painter.mode(mode);
                    ImageCanvas current = canvas(scale, assets, font);
                    document.paint(current);
                    assertNull(document.error());
                    original.assertEquivalent(painter.lastDisplayList(), effect);
                    assertArrayEquals(expected, pixels(current), mode + " " + effect + " scale=" + scale);
                    current.dispose();
                }
                ImageCanvas replay = canvas(scale, assets, font);
                original.replay(replay);
                assertArrayEquals(expected, pixels(replay), "replay " + effect);
                replay.dispose();
                old.dispose();
                document.close();
            }
        }
    }

    private static ImageCanvas canvas(int scale, MinecraftAssets assets, MinecraftFont font) {
        return new ImageCanvas(new BufferedImage(120 * scale, 90 * scale, BufferedImage.TYPE_INT_ARGB), scale, assets, font);
    }

    private static int[] pixels(ImageCanvas canvas) {
        BufferedImage image = canvas.image();
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
