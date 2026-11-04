package dev.oriel.engine.paint;

import dev.oriel.engine.style.Colors;
import dev.oriel.engine.style.Image;

import java.util.List;

/**
 * Tessellates gradient images into a {@link QuadBatch}: linear gradients (any angle, any number of stops, repeating)
 * as bands between colour stops, radial gradients as concentric rings. Pieces are generously sized; the batch's clip
 * region trims them to the painted area (the rounded border shape intersected with the image tile).
 */
final class Gradients {
    /** Repeating gradients with very short periods are capped at this many bands. */
    private static final int MAX_BANDS = 2048;

    private float[] stopPos = new float[8];
    private int[] stopColor = new int[8];
    private int stopCount;

    /** The gradient line of the linear gradient being drawn: start point, direction, perpendicular half-extent. */
    private float startX, startY, dirX, dirY, halfWidth;

    /** The rings of the radial gradient being drawn: centre, vertical scale, unit circle samples. */
    private float centerX, centerY, yScale;
    private float[] ringCos = new float[65], ringSin = new float[65];
    private int ringSegments;

    /** Draws a gradient image filling the tile (x, y, w, h). Other images are ignored. */
    void paint(QuadBatch batch, Image image, float x, float y, float w, float h, float devicePixel) {
        switch (image) {
            case Image.LinearGradient g -> linear(batch, g, x, y, w, h);
            case Image.RadialGradient g -> radial(batch, g, x, y, w, h, devicePixel);
            case Image.ConicGradient g -> conic(batch, g, x, y, w, h);
            default -> {}
        }
    }

    // ---- Linear ----

    private void linear(QuadBatch batch, Image.LinearGradient g, float x, float y, float w, float h) {
        double angle = Math.toRadians(g.angleDeg());
        dirX = (float) Math.sin(angle);
        dirY = (float) -Math.cos(angle);
        // The gradient line passes through the centre and is long enough for its ends to touch opposite corners.
        float length = Math.abs(w * dirX) + Math.abs(h * dirY);
        if (!(length > 0) || !resolveStops(g.stops(), length)) return;
        startX = x + w / 2 - dirX * length / 2;
        startY = y + h / 2 - dirY * length / 2;
        halfWidth = (float) Math.hypot(w, h) / 2 + 1;
        int last = stopCount - 1;
        float period = stopPos[last] - stopPos[0];
        if (!g.repeating()) {
            band(batch, -1, stopPos[0], stopColor[0], stopColor[0]);
            stops(batch, 0);
            band(batch, stopPos[last], length + 1, stopColor[last], stopColor[last]);
        } else if (period < 1e-3f || (length / period) * stopCount > MAX_BANDS) {
            band(batch, -1, length + 1, stopColor[last], stopColor[last]);
        } else {
            int from = (int) Math.floor(-stopPos[last] / period), to = (int) Math.ceil((length - stopPos[0]) / period);
            for (int k = from; k <= to; k++) stops(batch, k * period);
        }
    }

    /** The bands between consecutive stops, shifted along the line by {@code offset}. */
    private void stops(QuadBatch batch, float offset) {
        for (int i = 0; i + 1 < stopCount; i++) {
            band(batch, stopPos[i] + offset, stopPos[i + 1] + offset, stopColor[i], stopColor[i + 1]);
        }
    }

    /** The band where the gradient line runs from {@code s0} to {@code s1}, shading from c0 to c1. */
    private void band(QuadBatch batch, float s0, float s1, int c0, int c1) {
        if (!(s1 > s0)) return;
        c0 = premultipliedEnd(c0, c1);
        c1 = premultipliedEnd(c1, c0);
        float px = -dirY * halfWidth, py = dirX * halfWidth;
        float ax = startX + dirX * s0, ay = startY + dirY * s0, bx = startX + dirX * s1, by = startY + dirY * s1;
        batch.quad(ax + px, ay + py, ax - px, ay - py, bx - px, by - py, bx + px, by + py, c0, c0, c1, c1);
    }

    // ---- Radial ----

    private void radial(QuadBatch batch, Image.RadialGradient g, float x, float y, float w, float h, float dp) {
        centerX = x + g.centerX().resolve(w);
        centerY = y + g.centerY().resolve(h);
        float left = Math.abs(centerX - x), right = Math.abs(x + w - centerX);
        float top = Math.abs(centerY - y), bottom = Math.abs(y + h - centerY);
        float fx = Math.max(left, right), fy = Math.max(top, bottom);
        Image.RadialSize size = g.size();
        float rx, ry;
        if (size.extent() == null) {
            rx = size.radiusX().resolve(w);
            ry = g.circle() ? rx : size.radiusY().resolve(h);
        } else {
            // The distances to the sides the extent names; a corner extent passes through the corner they meet at.
            boolean closest = size.extent() == Image.RadialSize.Extent.CLOSEST_SIDE
                    || size.extent() == Image.RadialSize.Extent.CLOSEST_CORNER;
            boolean corner = size.extent() == Image.RadialSize.Extent.CLOSEST_CORNER
                    || size.extent() == Image.RadialSize.Extent.FARTHEST_CORNER;
            float sx = closest ? Math.min(left, right) : fx, sy = closest ? Math.min(top, bottom) : fy;
            if (g.circle()) {
                rx = ry = corner ? (float) Math.hypot(sx, sy) : closest ? Math.min(sx, sy) : Math.max(sx, sy);
            } else {
                // An ellipse keeps the sides' aspect ratio; through their corner, that scales both by √2.
                float k = corner ? (float) Math.sqrt(2) : 1;
                rx = sx * k;
                ry = sy * k;
            }
        }
        if (!(rx > 0) || !(ry > 0) || !resolveStops(g.stops(), rx)) return;
        yScale = ry / rx;
        int last = stopCount - 1;
        // The last ring reaches the farthest corner, so the last colour covers the rest of the tile.
        float outer = Math.max((float) Math.hypot(fx, fy / yScale), stopPos[last]) + 1;
        int segments = Math.clamp(4L * Shapes.segments(Math.max(outer, outer * yScale), dp), 8, 64);
        if (segments != ringSegments) { // the table depends on the count alone
            ringSegments = segments;
            for (int j = 0; j <= segments; j++) {
                double a = 2 * Math.PI * j / segments;
                ringCos[j] = (float) Math.cos(a);
                ringSin[j] = (float) Math.sin(a);
            }
        }
        ring(batch, 0, stopPos[0], stopColor[0], stopColor[0]);
        for (int i = 0; i < last; i++) ring(batch, stopPos[i], stopPos[i + 1], stopColor[i], stopColor[i + 1]);
        ring(batch, stopPos[last], outer, stopColor[last], stopColor[last]);
    }

    /** The ring between radii r0 and r1 (along the horizontal ray), shading from c0 inside to c1 outside. */
    private void ring(QuadBatch batch, float r0, float r1, int c0, int c1) {
        r0 = Math.max(0, r0);
        if (!(r1 > r0)) return;
        c0 = premultipliedEnd(c0, c1);
        c1 = premultipliedEnd(c1, c0);
        float ry0 = r0 * yScale, ry1 = r1 * yScale;
        for (int j = 0; j < ringSegments; j++) {
            float cos0 = ringCos[j], sin0 = ringSin[j], cos1 = ringCos[j + 1], sin1 = ringSin[j + 1];
            batch.quad(centerX + r0 * cos0, centerY + ry0 * sin0, centerX + r1 * cos0, centerY + ry1 * sin0,
                    centerX + r1 * cos1, centerY + ry1 * sin1, centerX + r0 * cos1, centerY + ry0 * sin1,
                    c0, c1, c1, c0);
        }
    }

    // ---- Conic ----

    /** Slices of at most this many degrees; stops add their own edges, so hard stops stay hard. */
    private static final float CONIC_STEP = 2;

    /**
     * A fan of thin slices around the centre, out past the farthest corner (the batch clips it to the tile). Each
     * slice shades from the colour at its first edge to the one at its second.
     */
    private void conic(QuadBatch batch, Image.ConicGradient g, float x, float y, float w, float h) {
        if (!resolveStops(g.stops(), 1)) return;
        float cx = x + g.centerX().resolve(w), cy = y + g.centerY().resolve(h);
        float reach = (float) Math.max(Math.max(Math.hypot(cx - x, cy - y), Math.hypot(x + w - cx, cy - y)),
                Math.max(Math.hypot(cx - x, y + h - cy), Math.hypot(x + w - cx, y + h - cy))) + 1;
        float period = stopPos[stopCount - 1] - stopPos[0];
        boolean repeating = g.repeating() && period > 1e-3f;
        // The slice edges: an even step, plus every stop (repeated through the turn when the gradient repeats).
        java.util.TreeSet<Float> edges = new java.util.TreeSet<>();
        int steps = (int) Math.ceil(360 / CONIC_STEP);
        for (int i = 0; i <= steps; i++) edges.add((float) i / steps);
        if (repeating) {
            for (float base = stopPos[0] - (float) Math.ceil(stopPos[0] / period) * period; base < 1; base += period) {
                for (int i = 0; i < stopCount; i++) addEdge(edges, base + stopPos[i] - stopPos[0]);
            }
        } else {
            for (int i = 0; i < stopCount; i++) addEdge(edges, stopPos[i]);
        }
        Float prev = null;
        for (float t : edges) {
            if (prev != null && t - prev > 1e-5f) {
                float t0 = prev, t1 = t, eps = Math.min(1e-4f, (t1 - t0) / 4);
                int c0 = conicColor(t0 + eps, repeating, period), c1 = conicColor(t1 - eps, repeating, period);
                double a0 = Math.toRadians(g.fromDeg() + t0 * 360), a1 = Math.toRadians(g.fromDeg() + t1 * 360);
                float x0 = cx + reach * (float) Math.sin(a0), y0 = cy - reach * (float) Math.cos(a0);
                float x1 = cx + reach * (float) Math.sin(a1), y1 = cy - reach * (float) Math.cos(a1);
                int mid = conicColor((t0 + t1) / 2, repeating, period);
                batch.quad(cx, cy, x1, y1, x0, y0, x0, y0, mid, c1, c0, c0);
            }
            prev = t;
        }
    }

    private static void addEdge(java.util.TreeSet<Float> edges, float t) {
        if (t > 0 && t < 1) edges.add(t);
    }

    /** The colour at {@code t} (a fraction of the turn), interpolated premultiplied between the stops around it. */
    private int conicColor(float t, boolean repeating, float period) {
        if (repeating) t = stopPos[0] + (((t - stopPos[0]) % period) + period) % period;
        if (t <= stopPos[0]) return stopColor[0];
        for (int i = 1; i < stopCount; i++) {
            if (t <= stopPos[i]) {
                float span = stopPos[i] - stopPos[i - 1];
                float f = span <= 0 ? 1 : (t - stopPos[i - 1]) / span;
                return lerpPremultiplied(stopColor[i - 1], stopColor[i], f);
            }
        }
        return stopColor[stopCount - 1];
    }

    private static int lerpPremultiplied(int a, int b, float f) {
        float aa = Colors.alpha(a) / 255f, ba = Colors.alpha(b) / 255f, oa = aa + (ba - aa) * f;
        if (oa <= 0) return 0;
        int out = Math.round(oa * 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float ca = (a >> shift & 255) * aa, cb = (b >> shift & 255) * ba;
            out |= Math.clamp(Math.round((ca + (cb - ca) * f) / oa), 0, 255) << shift;
        }
        return out;
    }

    // ---- Stops ----

    /**
     * Resolves stop positions against the gradient length: missing first and last positions are 0 and 100%,
     * other missing ones are spread evenly, and each position is at least the previous one. False when empty.
     */
    private boolean resolveStops(List<Image.ColorStop> stops, float length) {
        int n = stops.size();
        if (n == 0) return false;
        if (stopPos.length < n) {
            stopPos = new float[n];
            stopColor = new int[n];
        }
        stopCount = n;
        for (int i = 0; i < n; i++) {
            Image.ColorStop stop = stops.get(i);
            stopColor[i] = stop.color();
            stopPos[i] = stop.position() == null ? Float.NaN : stop.position().resolve(length);
        }
        if (Float.isNaN(stopPos[0])) stopPos[0] = 0;
        if (Float.isNaN(stopPos[n - 1])) stopPos[n - 1] = Math.max(length, stopPos[0]);
        for (int i = 1; i < n; i++) {
            if (!Float.isNaN(stopPos[i])) {
                stopPos[i] = Math.max(stopPos[i], stopPos[i - 1]);
                continue;
            }
            int j = i;
            while (Float.isNaN(stopPos[j])) j++;
            float from = stopPos[i - 1], to = Math.max(stopPos[j], from);
            for (int k = i; k < j; k++) stopPos[k] = from + (to - from) * (k - i + 1) / (j - i + 1);
        }
        return true;
    }

    /**
     * Vertex colours interpolate per channel, but CSS interpolates premultiplied: a fully transparent end takes
     * the other end's RGB so fading to {@code transparent} does not darken. (Partial alpha differences keep the
     * small per-channel error.)
     */
    private static int premultipliedEnd(int c, int other) {
        return Colors.alpha(c) == 0 ? other & 0x00FFFFFF : c;
    }
}
