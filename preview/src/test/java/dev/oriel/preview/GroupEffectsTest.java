package dev.oriel.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Group effects through the whole pipeline: a page laid out, painted by the engine and drawn by the previewer. */
class GroupEffectsTest {
    @TempDir
    Path dir;

    private BufferedImage render(String body) throws IOException {
        Path page = dir.resolve("page.html");
        Files.writeString(page, "<html><body style=\"margin: 0\"><div style=\"width: 40px; height: 40px; background: #0000ff\">" + body + "</div></body></html>");
        try (TestScene t = TestScene.open(page, Optional.empty(), 40, 40, 1)) {
            BufferedImage image = t.render(0);
            assertNull(t.scene().error(), () -> "failed: " + t.scene().error());
            return image;
        }
    }

    @Test
    void overflowClipsToRoundedCorners() throws IOException {
        BufferedImage image = render("""
                <div style="width: 40px; height: 40px; border-radius: 50%; overflow: hidden">
                  <div style="width: 40px; height: 40px; background: #ff0000"></div>
                </div>""");
        assertEquals(0xFF0000FF, image.getRGB(1, 1), "the corner is outside the circle");
        assertEquals(0xFFFF0000, image.getRGB(20, 20), "the middle is inside");
        assertEquals(0xFFFF0000, image.getRGB(20, 1), "the top edge is on the circle");
    }

    @Test
    void squareOverflowStillClipsToTheRectangle() throws IOException {
        BufferedImage image = render("""
                <div style="width: 20px; height: 20px; overflow: hidden">
                  <div style="width: 40px; height: 40px; background: #ff0000"></div>
                </div>""");
        assertEquals(0xFFFF0000, image.getRGB(1, 1));
        assertEquals(0xFF0000FF, image.getRGB(30, 30));
    }
}
