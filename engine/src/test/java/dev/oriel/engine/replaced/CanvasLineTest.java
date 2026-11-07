package dev.oriel.engine.replaced;

import dev.oriel.engine.testing.TestHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanvasLineTest {
    private static Context2D context() {
        return new Context2D(new TestHost().load("<canvas id='c' width='8' height='8'></canvas>").doc.getElementById("c"));
    }

    private static int pixel(Context2D context, int x, int y) {
        byte[] rgba = new byte[4];
        context.getImageData(x, y, 1, 1, rgba, 0);
        return (rgba[3] & 255) << 24 | (rgba[0] & 255) << 16 | (rgba[1] & 255) << 8 | (rgba[2] & 255);
    }

    @Test
    void roundCapsCoverEndpointsWithoutDoubleBlending() {
        Context2D context = context();
        context.setStrokeStyle("rgba(255,0,0,0.5)");
        context.setLineWidth(2);
        context.strokeLine(2, 3, 5, 3, "round");
        assertEquals(0x80ff0000, pixel(context, 1, 2));
        assertEquals(0x80ff0000, pixel(context, 2, 2));
        assertEquals(0, pixel(context, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> context.strokeLine(0, 0, 1, 1, "unknown"));
        assertThrows(IllegalArgumentException.class, () -> context.strokeLine(Double.NaN, 0, 1, 1, "butt"));
    }

    @Test
    void lighterAndScreenAreExplicitAndSavedWithContext() {
        Context2D context = context();
        context.setFillStyle("#800000");
        context.fillRect(0, 0, 8, 8);
        context.save();
        context.setGlobalCompositeOperation("lighter");
        context.fillRect(0, 0, 1, 1);
        assertEquals(0xffff0000, pixel(context, 0, 0));
        context.restore();
        assertEquals("source-over", context.globalCompositeOperation());
        context.setGlobalCompositeOperation("screen");
        context.fillRect(1, 0, 1, 1);
        assertEquals(0xffc00000, pixel(context, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> context.setGlobalCompositeOperation("xor"));
    }
}
