package dev.oriel.preview.render;

import dev.oriel.engine.paint.SmoothShape;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;

/**
 * Draws {@link SmoothShape}s straight into an ARGB pixel array the way Minecraft's shape shader does: every device
 * pixel under the shape's quad samples {@link SmoothShape#coverage} at its centre, and the colour is blended
 * source-over. The previewer then shows the same smooth edges and blurs as the game.
 */
final class ShapeRasterizer {
    private final int[] pixels;
    private final int stride;
    private final Rectangle2D.Float quad = new Rectangle2D.Float();

    ShapeRasterizer(int[] pixels, int stride) {
        this.pixels = pixels;
        this.stride = stride;
    }

    /**
     * Fills {@code shape} (in GUI px, placed by {@code guiToDevice}) in {@code argb}, which already carries the
     * canvas's opacity, inside {@code clip}. {@code devicePixel} is a device pixel's size in GUI px.
     */
    void fill(SmoothShape shape, AffineTransform guiToDevice, Rectangle clip, float devicePixel, int argb) {
        AffineTransform deviceToGui;
        try {
            deviceToGui = guiToDevice.createInverse();
        } catch (NoninvertibleTransformException e) {
            return;
        }
        float edge = devicePixel;
        quad.setRect(shape.x0() - edge, shape.y0() - edge, shape.x1() - shape.x0() + 2 * edge, shape.y1() - shape.y0() + 2 * edge);
        Rectangle area = guiToDevice.createTransformedShape(quad).getBounds().intersection(clip);
        int alpha = argb >>> 24, rgb = argb & 0xFFFFFF;
        float[] hollow = shape.hollow(devicePixel);
        // The pixel centres in shape space, stepped along each row rather than transformed one by one.
        float m00 = (float) deviceToGui.getScaleX(), m01 = (float) deviceToGui.getShearX();
        float m10 = (float) deviceToGui.getShearY(), m11 = (float) deviceToGui.getScaleY();
        float tx = (float) deviceToGui.getTranslateX() - shape.cx(), ty = (float) deviceToGui.getTranslateY() - shape.cy();
        for (int y = area.y; y < area.y + area.height; y++) {
            float cx = area.x + 0.5f, cy = y + 0.5f;
            float sx = m00 * cx + m01 * cy + tx, sy = m10 * cx + m11 * cy + ty;
            for (int x = area.x; x < area.x + area.width; x++, sx += m00, sy += m10) {
                if (hollow != null && Math.abs(sx) < hollow[0] && Math.abs(sy) < hollow[1]) continue;
                float coverage = shape.coverage(sx, sy, devicePixel);
                int a = Math.round(alpha * coverage);
                if (a == 0) continue;
                int i = y * stride + x;
                pixels[i] = TriangleRasterizer.blend(pixels[i], a << 24 | rgb);
            }
        }
    }
}
