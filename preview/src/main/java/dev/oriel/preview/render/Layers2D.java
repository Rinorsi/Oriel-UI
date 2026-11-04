package dev.oriel.preview.render;

import dev.oriel.engine.style.BlendMode;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.MaskComposite;

import java.awt.Rectangle;
import java.util.List;

/**
 * The pixel work of {@link ImageCanvas}'s groups, on {@code TYPE_INT_ARGB} pixel arrays (non-premultiplied, as the
 * canvas draws them): filters (colour matrices, Gaussian blur, drop shadows), mask compositing, and drawing a layer
 * onto the one under it with a blend mode (Compositing and Blending 1). Everything works inside a device-pixel
 * rectangle, the canvas's clip, which is where anything can have been drawn.
 */
final class Layers2D {
    private Layers2D() {}

    // ---- Filters ----

    /** Applies {@code filters} in order to {@code px}; {@code scale} turns filter px (GUI) into device px. */
    static void filter(int[] px, int width, Rectangle area, List<Filter> filters, float scale) {
        int i = 0;
        while (i < filters.size()) {
            Filter f = filters.get(i);
            if (f.isColorOnly()) {
                int j = i;
                while (j < filters.size() && filters.get(j).isColorOnly()) j++;
                colorMatrix(px, width, area, Filter.matrix(filters, i, j));
                i = j;
            } else if (f.kind() == Filter.Kind.BLUR) {
                blur(px, width, area, f.amount() * scale);
                i++;
            } else {
                dropShadow(px, width, area, f.x() * scale, f.y() * scale, f.amount() * scale / 2, f.color());
                i++;
            }
        }
    }

    /** A 4x5 colour matrix on each pixel's non-premultiplied channels (0..1), as feColorMatrix. */
    static void colorMatrix(int[] px, int width, Rectangle area, float[] m) {
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x; x < area.x + area.width; x++) {
                int k = y * width + x, c = px[k];
                float a = (c >>> 24) / 255f, r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
                float r2 = m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4];
                float g2 = m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9];
                float b2 = m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14];
                float a2 = m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19];
                px[k] = argb(a2, r2, g2, b2);
            }
        }
    }

    /** Gaussian blur with standard deviation {@code sigma} (device px), as three box blurs on premultiplied colour. */
    static void blur(int[] px, int width, Rectangle area, float sigma) {
        if (sigma < 0.3f) return;
        int w = area.width, h = area.height;
        float[][] ch = premultiplied(px, width, area);
        float[] tmp = new float[w * h];
        for (int box : boxes(sigma)) {
            int r = (box - 1) / 2;
            for (float[] c : ch) {
                boxHorizontal(c, tmp, w, h, r);
                boxVertical(tmp, c, w, h, r);
            }
        }
        unpremultiply(ch, px, width, area);
    }

    /**
     * {@code drop-shadow}: the input's alpha, blurred ({@code sigma}), coloured and offset, drawn under the input.
     */
    static void dropShadow(int[] px, int width, Rectangle area, float dx, float dy, float sigma, int color) {
        int w = area.width, h = area.height;
        float[] alpha = new float[w * h];
        int ox = Math.round(dx), oy = Math.round(dy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = x - ox, sy = y - oy;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                alpha[y * w + x] = (px[(area.y + sy) * width + area.x + sx] >>> 24) / 255f;
            }
        }
        if (sigma >= 0.3f) {
            float[] tmp = new float[w * h];
            for (int box : boxes(sigma)) {
                int r = (box - 1) / 2;
                boxHorizontal(alpha, tmp, w, h, r);
                boxVertical(tmp, alpha, w, h, r);
            }
        }
        float ca = (color >>> 24) / 255f;
        int rgb = color & 0xFFFFFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int k = (area.y + y) * width + area.x + x;
                float sa = alpha[y * w + x] * ca;
                if (sa <= 0) continue;
                int shadow = (Math.round(Math.min(1, sa) * 255) << 24) | rgb;
                px[k] = over(shadow, px[k]); // the input stays on top of its shadow
            }
        }
    }

    // ---- Masks ----

    /** Combines mask layer {@code layer} (alpha) onto {@code acc} (0..1 per pixel of {@code area}). */
    static void maskComposite(float[] acc, int[] layer, int width, Rectangle area, MaskComposite op, boolean first) {
        int w = area.width;
        for (int y = 0; y < area.height; y++) {
            for (int x = 0; x < w; x++) {
                float s = (layer[(area.y + y) * width + area.x + x] >>> 24) / 255f, d = acc[y * w + x];
                acc[y * w + x] = first ? s : switch (op) {
                    case ADD -> s + d * (1 - s);
                    case SUBTRACT -> s * (1 - d);
                    case INTERSECT -> s * d;
                    case EXCLUDE -> s * (1 - d) + d * (1 - s);
                };
            }
        }
    }

    /** Multiplies {@code px}'s alpha by {@code mask} (per pixel of {@code area}) and by {@code opacity}. */
    static void multiplyAlpha(int[] px, int width, Rectangle area, float[] mask, float opacity) {
        int w = area.width;
        for (int y = 0; y < area.height; y++) {
            for (int x = 0; x < w; x++) {
                int k = (area.y + y) * width + area.x + x, c = px[k];
                float a = (c >>> 24) / 255f * opacity * (mask == null ? 1 : mask[y * w + x]);
                px[k] = (Math.round(a * 255) << 24) | (c & 0xFFFFFF);
            }
        }
    }

    // ---- Compositing ----

    /** Draws {@code src} over {@code dst} inside {@code area} with {@code mode} (then source-over). */
    static void composite(int[] src, int[] dst, int width, Rectangle area, BlendMode mode) {
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x; x < area.x + area.width; x++) {
                int k = y * width + x, s = src[k];
                if ((s >>> 24) == 0) continue;
                dst[k] = mode == BlendMode.NORMAL ? over(dst[k], s) : blend(dst[k], s, mode);
            }
        }
    }

    /** Source-over of non-premultiplied colours. */
    static int over(int dst, int src) {
        float sa = (src >>> 24) / 255f, da = (dst >>> 24) / 255f;
        if (sa >= 1) return src;
        float oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        float r = ((src >> 16 & 255) * sa + (dst >> 16 & 255) * da * (1 - sa)) / oa;
        float g = ((src >> 8 & 255) * sa + (dst >> 8 & 255) * da * (1 - sa)) / oa;
        float b = ((src & 255) * sa + (dst & 255) * da * (1 - sa)) / oa;
        return (Math.round(oa * 255) << 24) | (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
    }

    private static int blend(int dst, int src, BlendMode mode) {
        float sa = (src >>> 24) / 255f, da = (dst >>> 24) / 255f;
        float[] cs = {(src >> 16 & 255) / 255f, (src >> 8 & 255) / 255f, (src & 255) / 255f};
        float[] cb = {(dst >> 16 & 255) / 255f, (dst >> 8 & 255) / 255f, (dst & 255) / 255f};
        if (mode == BlendMode.PLUS_LIGHTER) {
            float oa = Math.min(1, sa + da);
            float[] o = new float[3];
            for (int i = 0; i < 3; i++) o[i] = oa <= 0 ? 0 : Math.min(1, cs[i] * sa + cb[i] * da) / oa;
            return argb(oa, o[0], o[1], o[2]);
        }
        float[] mixed = switch (mode) {
            case HUE -> lum(sat(cs, satOf(cb)), lumOf(cb));
            case SATURATION -> lum(sat(cb, satOf(cs)), lumOf(cb));
            case COLOR -> lum(cs, lumOf(cb));
            case LUMINOSITY -> lum(cb, lumOf(cs));
            default -> {
                float[] o = new float[3];
                for (int i = 0; i < 3; i++) o[i] = separable(mode, cb[i], cs[i]);
                yield o;
            }
        };
        float oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        float[] o = new float[3];
        for (int i = 0; i < 3; i++) {
            float cs2 = (1 - da) * cs[i] + da * mixed[i];
            o[i] = (sa * cs2 + (1 - sa) * da * cb[i]) / oa;
        }
        return argb(oa, o[0], o[1], o[2]);
    }

    private static float separable(BlendMode mode, float b, float s) {
        return switch (mode) {
            case MULTIPLY -> b * s;
            case SCREEN -> b + s - b * s;
            case OVERLAY -> hardLight(s, b);
            case DARKEN -> Math.min(b, s);
            case LIGHTEN -> Math.max(b, s);
            case COLOR_DODGE -> b == 0 ? 0 : s >= 1 ? 1 : Math.min(1, b / (1 - s));
            case COLOR_BURN -> b >= 1 ? 1 : s <= 0 ? 0 : 1 - Math.min(1, (1 - b) / s);
            case HARD_LIGHT -> hardLight(b, s);
            case SOFT_LIGHT -> {
                if (s <= 0.5f) yield b - (1 - 2 * s) * b * (1 - b);
                float d = b <= 0.25f ? ((16 * b - 12) * b + 4) * b : (float) Math.sqrt(b);
                yield b + (2 * s - 1) * (d - b);
            }
            case DIFFERENCE -> Math.abs(b - s);
            case EXCLUSION -> b + s - 2 * b * s;
            default -> s;
        };
    }

    private static float hardLight(float b, float s) {
        return s <= 0.5f ? b * 2 * s : b + (2 * s - 1) - b * (2 * s - 1);
    }

    private static float lumOf(float[] c) {
        return 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2];
    }

    private static float[] lum(float[] c, float l) {
        float d = l - lumOf(c);
        float[] o = {c[0] + d, c[1] + d, c[2] + d};
        float lum = lumOf(o), n = Math.min(o[0], Math.min(o[1], o[2])), x = Math.max(o[0], Math.max(o[1], o[2]));
        for (int i = 0; i < 3; i++) {
            if (n < 0) o[i] = lum + (o[i] - lum) * lum / (lum - n);
            if (x > 1) o[i] = lum + (o[i] - lum) * (1 - lum) / (x - lum);
        }
        return o;
    }

    private static float satOf(float[] c) {
        return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2]));
    }

    private static float[] sat(float[] c, float s) {
        float max = Math.max(c[0], Math.max(c[1], c[2])), min = Math.min(c[0], Math.min(c[1], c[2]));
        float[] o = new float[3];
        for (int i = 0; i < 3; i++) o[i] = max > min ? (c[i] - min) * s / (max - min) : 0;
        return o;
    }

    // ---- Helpers ----

    private static int argb(float a, float r, float g, float b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    /** Box sizes for three box blurs approximating a Gaussian of {@code sigma}. */
    private static int[] boxes(float sigma) {
        double wIdeal = Math.sqrt(12 * sigma * sigma / 3 + 1);
        int wl = (int) Math.floor(wIdeal);
        if (wl % 2 == 0) wl--;
        int wu = wl + 2;
        double mIdeal = (12 * sigma * sigma - 3 * wl * wl - 4 * 3 * wl - 3 * 3) / (-4.0 * wl - 4);
        long m = Math.round(mIdeal);
        int[] sizes = new int[3];
        for (int i = 0; i < 3; i++) sizes[i] = i < m ? wl : wu;
        return sizes;
    }

    private static void boxHorizontal(float[] in, float[] out, int w, int h, int r) {
        float norm = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float acc = 0;
            for (int x = -r; x <= r; x++) acc += x >= 0 && x < w ? in[row + x] : 0;
            for (int x = 0; x < w; x++) {
                out[row + x] = acc * norm;
                int add = x + r + 1, sub = x - r;
                if (add < w) acc += in[row + add];
                if (sub >= 0) acc -= in[row + sub];
            }
        }
    }

    private static void boxVertical(float[] in, float[] out, int w, int h, int r) {
        float norm = 1f / (2 * r + 1);
        for (int x = 0; x < w; x++) {
            float acc = 0;
            for (int y = -r; y <= r; y++) acc += y >= 0 && y < h ? in[y * w + x] : 0;
            for (int y = 0; y < h; y++) {
                out[y * w + x] = acc * norm;
                int add = y + r + 1, sub = y - r;
                if (add < h) acc += in[add * w + x];
                if (sub >= 0) acc -= in[sub * w + x];
            }
        }
    }

    private static float[][] premultiplied(int[] px, int width, Rectangle area) {
        int w = area.width, h = area.height;
        float[][] ch = new float[4][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[(area.y + y) * width + area.x + x], k = y * w + x;
                float a = (c >>> 24) / 255f;
                ch[0][k] = a;
                ch[1][k] = (c >> 16 & 255) / 255f * a;
                ch[2][k] = (c >> 8 & 255) / 255f * a;
                ch[3][k] = (c & 255) / 255f * a;
            }
        }
        return ch;
    }

    private static void unpremultiply(float[][] ch, int[] px, int width, Rectangle area) {
        int w = area.width, h = area.height;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int k = y * w + x;
                float a = ch[0][k];
                px[(area.y + y) * width + area.x + x] = a <= 0.002f ? 0 : argb(a, ch[1][k] / a, ch[2][k] / a, ch[3][k] / a);
            }
        }
    }
}
