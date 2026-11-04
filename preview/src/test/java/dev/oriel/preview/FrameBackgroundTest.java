package dev.oriel.preview;

import dev.oriel.engine.paint.Canvas;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameBackgroundTest {
    @Test
    void solidBackdropSurvivesReusedFramesAndResize() throws Exception {
        try (MinecraftAssets assets = MinecraftAssets.open(List.of(), Optional.empty())) {
            FrameRenderer renderer = new FrameRenderer(assets, new MinecraftFont(assets), 0xFF123456);
            Scene scene = new Scene() {
                public void frame(double now, float width, float height, float scale) {}
                public void paint(Canvas canvas) { canvas.fillRect(1, 1, 2, 2, 0xFFFFFFFF); }
            };
            for (int size : new int[] {20, 20, 24}) {
                var image = renderer.render(scene, size, size, 2, 0, null);
                assertEquals(0xFF123456, image.getRGB(0, 0));
                assertEquals(0xFF123456, image.getRGB(size - 1, size - 1));
                assertEquals(0xFFFFFFFF, image.getRGB(3, 3));
            }
        }
    }
}
