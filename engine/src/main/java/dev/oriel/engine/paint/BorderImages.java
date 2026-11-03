package dev.oriel.engine.paint;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.replaced.ImageSources;
import dev.oriel.engine.style.BorderImage;
import dev.oriel.engine.style.BorderImage.Repeat;
import dev.oriel.engine.style.BorderImage.Side;
import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.ImageRendering;

/**
 * Paints {@code border-image} (CSS Backgrounds 3 §6): the source texture is cut into nine regions by the slice, the
 * corners drawn into the corners of the border image area, the edges stretched or tiled along its sides, and the
 * middle into what is left when {@code fill}. Region edges snap to device pixels so pixel art stays crisp: one
 * texel lands on a whole number of device pixels whenever the widths are whole GUI px at an integer GUI scale.
 */
final class BorderImages {
    /** Tiles drawn along one edge at most, so a tiny slice on a huge box cannot flood the canvas. */
    static final int MAX_TILES = 512;

    private final Document document;
    /** Texel offsets of the slice lines in the source (left, right edges of the middle column; top, bottom of the middle row). */
    private final float[] sx = new float[4], sy = new float[4];
    /** Destination lines in GUI px: x0, x0 + left width, x1 - right width, x1; same for y. */
    private final float[] dx = new float[4], dy = new float[4];

    BorderImages(Document document) {
        this.document = document;
    }

    /**
     * Paints the border image of a box with border box {@code g}, and returns whether it did. False when there is no
     * usable source (none, not loaded, no intrinsic size): the border is then painted as usual, as CSS says.
     */
    boolean paint(Canvas canvas, Geometry g, ComputedStyle s, float dp) {
        BorderImage.Source source = s.borderImageSource;
        if (source.image() == null) return false;
        String url = ImageSources.textureUrl(document, source.image());
        float[] size = ImageSources.size(document, source.image());
        if (url == null || size == null || !(size[0] > 0) || !(size[1] > 0)) return false;
        float iw = size[0], ih = size[1];

        // Slice offsets, clamped to the image; opposite slices meeting or crossing leave the middle empty.
        BorderImage.Slice slice = s.borderImageSlice;
        float st = sliceOffset(slice.sides().top(), ih), sr = sliceOffset(slice.sides().right(), iw);
        float sb = sliceOffset(slice.sides().bottom(), ih), sl = sliceOffset(slice.sides().left(), iw);

        // The border image area: the border box grown by the outset.
        float[] border = g.border;
        float ot = outset(s.borderImageOutset.top(), border[0]), or = outset(s.borderImageOutset.right(), border[1]);
        float ob = outset(s.borderImageOutset.bottom(), border[2]), ol = outset(s.borderImageOutset.left(), border[3]);
        float ax = g.x - ol, ay = g.y - ot, aw = g.width + ol + or, ah = g.height + ot + ob;
        if (!(aw > 0) || !(ah > 0)) return true;

        float wt = width(s.borderImageWidth.top(), border[0], ah, st);
        float wr = width(s.borderImageWidth.right(), border[1], aw, sr);
        float wb = width(s.borderImageWidth.bottom(), border[2], ah, sb);
        float wl = width(s.borderImageWidth.left(), border[3], aw, sl);
        // Widths that overlap are scaled down together (§6.4).
        float f = 1;
        if (wl + wr > aw) f = Math.min(f, aw / (wl + wr));
        if (wt + wb > ah) f = Math.min(f, ah / (wt + wb));
        wt *= f; wr *= f; wb *= f; wl *= f;

        sx[0] = 0; sx[1] = Math.min(sl, iw); sx[2] = Math.max(sx[1], iw - sr); sx[3] = iw;
        sy[0] = 0; sy[1] = Math.min(st, ih); sy[2] = Math.max(sy[1], ih - sb); sy[3] = ih;
        float x0 = Geometry.snap(ax, dp), x3 = Geometry.snap(ax + aw, dp);
        float y0 = Geometry.snap(ay, dp), y3 = Geometry.snap(ay + ah, dp);
        dx[0] = x0; dx[1] = Geometry.snap(ax + wl, dp); dx[2] = Math.max(dx[1], Geometry.snap(ax + aw - wr, dp)); dx[3] = x3;
        dy[0] = y0; dy[1] = Geometry.snap(ay + wt, dp); dy[2] = Math.max(dy[1], Geometry.snap(ay + ah - wb, dp)); dy[3] = y3;

        boolean smooth = s.imageRendering == ImageRendering.SMOOTH;
        Repeat rx = s.borderImageRepeat.x(), ry = s.borderImageRepeat.y();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1 && !slice.fill()) continue;
                float w = dx[col + 1] - dx[col], h = dy[row + 1] - dy[row];
                float tw = sx[col + 1] - sx[col], th = sy[row + 1] - sy[row];
                if (!(w > 0) || !(h > 0) || !(tw > 0) || !(th > 0)) continue;
                // Corners stretch; edges tile along their length, scaled to the edge's thickness (§6.5). The
                // middle is scaled like the top edge horizontally and the left edge vertically.
                Repeat alongX = col == 1 ? rx : Repeat.STRETCH, alongY = row == 1 ? ry : Repeat.STRETCH;
                float tileW = w, tileH = h;
                if (col == 1 && alongX != Repeat.STRETCH) tileW = tw * scale(row == 1 ? dy[1] - dy[0] : h, sy[1] - sy[0], th, row);
                if (row == 1 && alongY != Repeat.STRETCH) tileH = th * scale(col == 1 ? dx[1] - dx[0] : w, sx[1] - sx[0], tw, col);
                region(canvas, url, s.tint, smooth, dx[col], dy[row], w, h,
                        sx[col] / iw, sy[row] / ih, sx[col + 1] / iw, sy[row + 1] / ih, alongX, tileW, alongY, tileH, dp);
            }
        }
        return true;
    }

    /** How much the source of a region is scaled: the thickness of its edge over the texels it came from. */
    private static float scale(float thickness, float edgeTexels, float ownTexels, int index) {
        if (index != 1) return thickness / ownTexels;
        return edgeTexels > 0 ? thickness / edgeTexels : 1;
    }

    /** Draws one region, as one stretched quad or as tiles along x and y. */
    private static void region(Canvas canvas, String url, int tint, boolean smooth, float x, float y, float w, float h,
                               float u0, float v0, float u1, float v1, Repeat rx, float tileW, Repeat ry, float tileH, float dp) {
        float[] xs = tiles(x, w, rx, tileW), ys = tiles(y, h, ry, tileH);
        for (int j = 0; j + 1 < ys.length; j += 2) {
            for (int i = 0; i + 1 < xs.length; i += 2) {
                float tx = xs[i], tx1 = xs[i + 1], ty = ys[j], ty1 = ys[j + 1];
                // The tile's span, cut to the region: partial tiles take the matching part of the slice.
                float cx0 = Math.max(tx, x), cx1 = Math.min(tx1, x + w), cy0 = Math.max(ty, y), cy1 = Math.min(ty1, y + h);
                cx0 = Geometry.snap(cx0, dp); cx1 = Geometry.snap(cx1, dp); cy0 = Geometry.snap(cy0, dp); cy1 = Geometry.snap(cy1, dp);
                if (!(cx1 > cx0) || !(cy1 > cy0)) continue;
                float fu0 = (cx0 - tx) / (tx1 - tx), fu1 = (cx1 - tx) / (tx1 - tx);
                float fv0 = (cy0 - ty) / (ty1 - ty), fv1 = (cy1 - ty) / (ty1 - ty);
                canvas.drawImage(url, cx0, cy0, cx1 - cx0, cy1 - cy0,
                        u0 + (u1 - u0) * fu0, v0 + (v1 - v0) * fv0, u0 + (u1 - u0) * fu1, v0 + (v1 - v0) * fv1, tint, smooth);
            }
        }
    }

    /** Start and end of each tile along an axis of length {@code len} from {@code pos}, as pairs. */
    static float[] tiles(float pos, float len, Repeat repeat, float tile) {
        if (repeat == Repeat.STRETCH || !(tile > 0) || tile >= len && repeat != Repeat.REPEAT) return new float[] {pos, pos + len};
        int count;
        float step, start;
        switch (repeat) {
            case ROUND -> {
                count = Math.max(1, Math.round(len / tile));
                tile = len / count;
                step = tile;
                start = pos;
            }
            case SPACE -> {
                count = (int) Math.floor(len / tile);
                if (count == 0) return new float[0];
                step = tile + (len - count * tile) / (count + 1);
                start = pos + step - tile;
            }
            default -> {
                // Repeat: tiles centred on the region, the outermost ones cut.
                int half = (int) Math.ceil((len / tile - 1) / 2);
                count = half * 2 + 1;
                step = tile;
                start = pos + len / 2 - tile / 2 - half * tile;
            }
        }
        count = Math.min(count, MAX_TILES);
        float[] out = new float[count * 2];
        for (int i = 0; i < count; i++) {
            out[i * 2] = start + i * step;
            out[i * 2 + 1] = start + i * step + tile;
        }
        return out;
    }

    private static float sliceOffset(Side s, float size) {
        float v = s.unit() == BorderImage.Unit.PERCENT ? size * s.value() / 100f : s.value();
        return Math.max(0, Math.min(v, size));
    }

    private static float outset(Side s, float borderWidth) {
        return s.unit() == BorderImage.Unit.NUMBER ? s.value() * borderWidth : s.value();
    }

    /** A border image width in GUI px: a multiple of the border width, a length, a share of the area, or the slice. */
    private static float width(Side s, float borderWidth, float areaSize, float slice) {
        return switch (s.unit()) {
            case NUMBER -> s.value() * borderWidth;
            case PX -> s.value();
            case PERCENT -> areaSize * s.value() / 100f;
            case AUTO -> slice;
        };
    }
}
