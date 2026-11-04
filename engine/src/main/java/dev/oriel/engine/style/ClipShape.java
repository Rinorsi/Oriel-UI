package dev.oriel.engine.style;

import java.util.List;

/**
 * A {@code clip-path} basic shape (CSS Shapes 1 §3), against the element's border box. {@link #outline} turns it into
 * a polygon, which is how painters clip with it (curves become short segments).
 */
public sealed interface ClipShape {
    /** Segments per full circle when a curve becomes a polygon. */
    int CIRCLE_SEGMENTS = 48;
    /** {@code clip-path: none}. */
    ClipShape NONE = new None();

    record None() implements ClipShape {}

    /** {@code inset(top right bottom left round radius)}; one radius for every corner. */
    record Inset(Length top, Length right, Length bottom, Length left, Length radius) implements ClipShape {}

    /** {@code circle(r at cx cy)}; a null radius is {@code closest-side}, the default. */
    record Circle(Length radius, boolean farthest, Length cx, Length cy) implements ClipShape {}

    /** {@code ellipse(rx ry at cx cy)}; null radii are {@code closest-side}. */
    record Ellipse(Length rx, Length ry, boolean farthestX, boolean farthestY, Length cx, Length cy) implements ClipShape {}

    /** {@code polygon(x y, ...)}: x and y alternate. */
    record Polygon(List<Length> points, boolean evenOdd) implements ClipShape {}

    /** The shape as polygon vertices (x, y alternating) in a box at (x, y), {@code w} by {@code h}. */
    default float[] outline(float x, float y, float w, float h) {
        return switch (this) {
            case None n -> null;
            case Inset in -> {
                float l = x + in.left.resolve(w), t = y + in.top.resolve(h);
                float r = x + w - in.right.resolve(w), b = y + h - in.bottom.resolve(h);
                float rad = Math.max(0, Math.min(in.radius == null ? 0 : in.radius.resolve(Math.min(w, h)), Math.min(r - l, b - t) / 2));
                yield rad <= 0 ? new float[] {l, t, r, t, r, b, l, b} : roundedRect(l, t, r, b, rad);
            }
            case Circle c -> {
                float cx = x + c.cx.resolve(w), cy = y + c.cy.resolve(h);
                float dx = Math.min(cx - x, x + w - cx), dy = Math.min(cy - y, y + h - cy);
                float fx = Math.max(cx - x, x + w - cx), fy = Math.max(cy - y, y + h - cy);
                float rad = c.radius != null ? c.radius.resolve((float) Math.hypot(w, h) / (float) Math.sqrt(2))
                        : c.farthest ? (float) Math.hypot(fx, fy) : Math.min(dx, dy);
                yield ellipse(cx, cy, rad, rad);
            }
            case Ellipse e -> {
                float cx = x + e.cx.resolve(w), cy = y + e.cy.resolve(h);
                float rx = e.rx != null ? e.rx.resolve(w) : e.farthestX ? Math.max(cx - x, x + w - cx) : Math.min(cx - x, x + w - cx);
                float ry = e.ry != null ? e.ry.resolve(h) : e.farthestY ? Math.max(cy - y, y + h - cy) : Math.min(cy - y, y + h - cy);
                yield ellipse(cx, cy, rx, ry);
            }
            case Polygon p -> {
                float[] out = new float[p.points.size()];
                for (int i = 0; i < out.length; i += 2) {
                    out[i] = x + p.points.get(i).resolve(w);
                    out[i + 1] = y + p.points.get(i + 1).resolve(h);
                }
                yield out;
            }
        };
    }

    private static float[] ellipse(float cx, float cy, float rx, float ry) {
        float[] out = new float[CIRCLE_SEGMENTS * 2];
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double t = 2 * Math.PI * i / CIRCLE_SEGMENTS;
            out[2 * i] = cx + rx * (float) Math.cos(t);
            out[2 * i + 1] = cy + ry * (float) Math.sin(t);
        }
        return out;
    }

    private static float[] roundedRect(float l, float t, float r, float b, float rad) {
        int per = CIRCLE_SEGMENTS / 4;
        float[] out = new float[(per + 1) * 4 * 2];
        float[][] centres = {{r - rad, t + rad}, {r - rad, b - rad}, {l + rad, b - rad}, {l + rad, t + rad}};
        int k = 0;
        for (int corner = 0; corner < 4; corner++) {
            double start = -Math.PI / 2 + corner * Math.PI / 2;
            for (int i = 0; i <= per; i++) {
                double a = start + Math.PI / 2 * i / per;
                out[k++] = centres[corner][0] + rad * (float) Math.cos(a);
                out[k++] = centres[corner][1] + rad * (float) Math.sin(a);
            }
        }
        return out;
    }
}
