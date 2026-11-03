package dev.oriel.engine.paint;

import dev.oriel.engine.style.BorderImage;
import dev.oriel.engine.style.BorderImage.Repeat;
import dev.oriel.engine.style.BorderImage.Unit;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.testing.Page;
import dev.oriel.engine.testing.RecordingCanvas;
import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** border-image: its longhands and shorthand, and nine-slice painting of a 12×16 texture. */
class BorderImagesTest {
    /** Paints a 40×20 div with {@code css}; {@code a.png} is 12×16. */
    private static RecordingCanvas paint(String css) {
        TestHost host = new TestHost();
        host.imageSizes.put("test:a.png", new float[] {12, 16});
        return host.load("<div style='width: 40px; height: 20px; " + css + "'></div>").paint(new RecordingCanvas());
    }

    @Test
    void longhandsParse() {
        ComputedStyle s = Page.styleOf("border-image-source: url(a.png); border-image-slice: 2 3 4 fill; "
                + "border-image-width: 2px auto 50% 1; border-image-outset: 1px 2; border-image-repeat: round stretch");
        assertTrue(s.borderImageSource.image() != null);
        assertEquals(new BorderImage.Side(Unit.NUMBER, 3), s.borderImageSlice.sides().right());
        assertEquals(new BorderImage.Side(Unit.NUMBER, 3), s.borderImageSlice.sides().left());
        assertTrue(s.borderImageSlice.fill());
        assertEquals(new BorderImage.Side(Unit.PX, 2), s.borderImageWidth.top());
        assertEquals(Unit.AUTO, s.borderImageWidth.right().unit());
        assertEquals(new BorderImage.Side(Unit.PERCENT, 50), s.borderImageWidth.bottom());
        assertEquals(new BorderImage.Side(Unit.NUMBER, 2), s.borderImageOutset.right());
        assertEquals(new BorderImage.Repeats(Repeat.ROUND, Repeat.STRETCH), s.borderImageRepeat);
    }

    @Test
    void shorthandSplitsSliceWidthOutsetAndRepeat() {
        ComputedStyle s = Page.styleOf("border-image: url(a.png) 2 2 4 2 fill / 3px / 1px repeat");
        assertEquals(new BorderImage.Side(Unit.NUMBER, 4), s.borderImageSlice.sides().bottom());
        assertTrue(s.borderImageSlice.fill());
        assertEquals(BorderImage.Sides.all(new BorderImage.Side(Unit.PX, 3)), s.borderImageWidth);
        assertEquals(BorderImage.Sides.all(new BorderImage.Side(Unit.PX, 1)), s.borderImageOutset);
        assertEquals(new BorderImage.Repeats(Repeat.REPEAT, Repeat.REPEAT), s.borderImageRepeat);
        // Left out parts return to their initial values.
        s = Page.styleOf("border-image: url(a.png) 2");
        assertEquals(BorderImage.Sides.ONE, s.borderImageWidth);
        assertEquals(BorderImage.Repeats.STRETCH, s.borderImageRepeat);
    }

    @Test
    void invalidValuesAreDropped() {
        ComputedStyle initial = Page.styleOf("");
        for (String css : List.of("border-image-slice: -1", "border-image-slice: 1 2 3 4 5", "border-image-slice: fill fill 1",
                "border-image-width: -2px", "border-image-outset: 10%", "border-image-repeat: stretch round space",
                "border-image-source: linear-gradient(red, blue)", "border-image: url(a.png) 2 /", "border-image: url(a.png) url(a.png)")) {
            ComputedStyle s = Page.styleOf(css);
            assertEquals(initial.borderImageSource, s.borderImageSource, css);
            assertEquals(initial.borderImageSlice, s.borderImageSlice, css);
            assertEquals(initial.borderImageWidth, s.borderImageWidth, css);
            assertEquals(initial.borderImageOutset, s.borderImageOutset, css);
            assertEquals(initial.borderImageRepeat, s.borderImageRepeat, css);
        }
    }

    @Test
    void nineSlicesCoverTheBorderBox() {
        RecordingCanvas c = paint("box-sizing: content-box; border: solid transparent; border-width: 2px 2px 4px 2px; "
                + "border-image: url(a.png) 2 2 4 2 fill");
        List<RecordingCanvas.Call> images = c.ops("drawImage");
        assertEquals(9, images.size());
        // Top-left corner: 2×2 px from texels 0..2 of 12 and 0..2 of 16.
        assertArrayEquals(new float[] {0, 0, 2, 2, 0, 0, 2 / 12f, 2 / 16f}, images.get(0).args(), 1e-5f);
        // Middle: the padding box, from the texture's middle.
        assertArrayEquals(new float[] {2, 2, 40, 20, 2 / 12f, 2 / 16f, 10 / 12f, 12 / 16f}, images.get(4).args(), 1e-5f);
        // Bottom-right corner: 2×4 px.
        assertArrayEquals(new float[] {42, 22, 2, 4, 10 / 12f, 12 / 16f, 1, 1}, images.get(8).args(), 1e-5f);
        // The border's own colour is not painted under it.
        assertTrue(c.ops("fillQuads").isEmpty() && c.ops("fillRect").isEmpty());
    }

    @Test
    void withoutFillTheMiddleIsLeftOut() {
        RecordingCanvas c = paint("border: 2px solid transparent; border-image: url(a.png) 2");
        assertEquals(8, c.ops("drawImage").size());
    }

    @Test
    void unknownSizeFallsBackToTheBorder() {
        TestHost host = new TestHost();
        RecordingCanvas c = host.load("<div style='width: 40px; height: 20px; border: 2px solid red; "
                + "border-image: url(missing.png) 2'></div>").paint(new RecordingCanvas());
        assertTrue(c.ops("drawImage").isEmpty());
        assertEquals(List.of("border 0,0 40x20 #ffff0000"), c.trace("border"));
    }

    @Test
    void widthsTooWideForTheBoxScaleDownTogether() {
        // 4 + 4 px of corner on a 6 px tall box: both scaled by 6 / 8.
        TestHost host = new TestHost();
        host.imageSizes.put("test:a.png", new float[] {12, 16});
        RecordingCanvas c = host.load("<div style='box-sizing: border-box; width: 40px; height: 6px; border: 2px solid transparent; "
                + "border-image: url(a.png) 4 / 4px'></div>").paint(new RecordingCanvas());
        float[] corner = c.ops("drawImage").getFirst().args();
        assertEquals(3, corner[2], 1e-5f);
        assertEquals(3, corner[3], 1e-5f);
    }

    @Test
    void roundAndRepeatTileTheEdges() {
        // A 40 px edge of 8 px tiles (8 texels at scale 1): round fits 5 whole tiles.
        float[] round = BorderImages.tiles(0, 40, Repeat.ROUND, 8);
        assertEquals(10, round.length);
        assertEquals(32, round[8], 1e-5f);
        // 30 px of 8 px tiles round to 4 tiles of 7.5 px.
        assertArrayEquals(new float[] {0, 7.5f, 7.5f, 15, 15, 22.5f, 22.5f, 30}, BorderImages.tiles(0, 30, Repeat.ROUND, 8), 1e-5f);
        // Repeat centres the tiles: 3 tiles of 8 on 20 px start 2 px before the edge.
        assertArrayEquals(new float[] {-2, 6, 6, 14, 14, 22}, BorderImages.tiles(0, 20, Repeat.REPEAT, 8), 1e-5f);
        // Space spreads whole tiles: 2 tiles of 8 on 20 px leave three 4/3 px gaps.
        float[] space = BorderImages.tiles(0, 20, Repeat.SPACE, 8);
        assertEquals(4, space.length);
        assertEquals(4 / 3f, space[0], 1e-4f);
    }

    @Test
    void stretchedEdgesAreOneDrawEach() {
        RecordingCanvas c = paint("border: 2px solid transparent; border-image: url(a.png) 2 fill");
        assertEquals(9, c.ops("drawImage").size());
        RecordingCanvas tiled = paint("border: 2px solid transparent; border-image: url(a.png) 2 fill round");
        assertTrue(tiled.ops("drawImage").size() > 9);
    }
}
