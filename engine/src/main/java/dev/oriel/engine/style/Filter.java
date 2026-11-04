package dev.oriel.engine.style;

import java.util.List;

/**
 * One function of {@code filter} or {@code backdrop-filter} (Filter Effects 1 §12). {@code amount} is the function's
 * argument as a number: the blur radius in px, the angle in degrees for {@code hue-rotate}, the factor for the rest
 * (percentages already divided by 100). A drop shadow keeps its offset in {@code x}, {@code y}, its blur in
 * {@code amount} and its colour.
 */
public record Filter(Kind kind, float amount, float x, float y, int color) {
    public enum Kind { BLUR, BRIGHTNESS, CONTRAST, DROP_SHADOW, GRAYSCALE, HUE_ROTATE, INVERT, OPACITY, SATURATE, SEPIA }

    public static Filter of(Kind kind, float amount) {
        return new Filter(kind, amount, 0, 0, 0);
    }

    /** Whether the function only maps each pixel's colour (everything but blur and drop shadow). */
    public boolean isColorOnly() {
        return kind != Kind.BLUR && kind != Kind.DROP_SHADOW;
    }

    /**
     * The function as a colour matrix on non-premultiplied sRGB: 4 rows (R, G, B, A) of 5 values, the fifth an
     * offset in 0..1, as {@code feColorMatrix} applies it. Only for {@link #isColorOnly} functions.
     */
    public float[] matrix() {
        float a = amount;
        return switch (kind) {
            case BRIGHTNESS -> diagonal(a, a, a, 1, 0);
            case CONTRAST -> diagonal(a, a, a, 1, 0.5f - 0.5f * a);
            case INVERT -> {
                float i = clamp01(a);
                yield diagonal(1 - 2 * i, 1 - 2 * i, 1 - 2 * i, 1, i);
            }
            case OPACITY -> diagonal(1, 1, 1, clamp01(a), 0);
            case GRAYSCALE -> {
                float g = 1 - clamp01(a);
                yield rgb(0.2126f + 0.7874f * g, 0.7152f - 0.7152f * g, 0.0722f - 0.0722f * g,
                        0.2126f - 0.2126f * g, 0.7152f + 0.2848f * g, 0.0722f - 0.0722f * g,
                        0.2126f - 0.2126f * g, 0.7152f - 0.7152f * g, 0.0722f + 0.9278f * g);
            }
            case SEPIA -> {
                float s = 1 - clamp01(a);
                yield rgb(0.393f + 0.607f * s, 0.769f - 0.769f * s, 0.189f - 0.189f * s,
                        0.349f - 0.349f * s, 0.686f + 0.314f * s, 0.168f - 0.168f * s,
                        0.272f - 0.272f * s, 0.534f - 0.534f * s, 0.131f + 0.869f * s);
            }
            case SATURATE -> rgb(0.213f + 0.787f * a, 0.715f - 0.715f * a, 0.072f - 0.072f * a,
                    0.213f - 0.213f * a, 0.715f + 0.285f * a, 0.072f - 0.072f * a,
                    0.213f - 0.213f * a, 0.715f - 0.715f * a, 0.072f + 0.928f * a);
            case HUE_ROTATE -> {
                double r = Math.toRadians(a);
                float c = (float) Math.cos(r), s = (float) Math.sin(r);
                yield rgb(0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f,
                        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f,
                        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f);
            }
            case BLUR, DROP_SHADOW -> throw new IllegalStateException(kind + " is not a colour matrix");
        };
    }

    /** {@code b} applied after {@code a}: the matrix of the two in a row. */
    public static float[] then(float[] a, float[] b) {
        float[] out = new float[20];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float v = col == 4 ? b[row * 5 + 4] : 0;
                for (int k = 0; k < 4; k++) v += b[row * 5 + k] * a[k * 5 + col];
                out[row * 5 + col] = v;
            }
        }
        return out;
    }

    /** The identity colour matrix. */
    public static float[] identity() {
        return diagonal(1, 1, 1, 1, 0);
    }

    /** The colour-only functions of {@code filters} from {@code from} up to the next blur or shadow, as one matrix. */
    public static float[] matrix(List<Filter> filters, int from, int to) {
        float[] m = identity();
        for (int i = from; i < to; i++) m = then(m, filters.get(i).matrix());
        return m;
    }

    private static float[] diagonal(float r, float g, float b, float a, float offset) {
        return new float[] {r, 0, 0, 0, offset, 0, g, 0, 0, offset, 0, 0, b, 0, offset, 0, 0, 0, a, 0};
    }

    private static float[] rgb(float rr, float rg, float rb, float gr, float gg, float gb, float br, float bg, float bb) {
        return new float[] {rr, rg, rb, 0, 0, gr, gg, gb, 0, 0, br, bg, bb, 0, 0, 0, 0, 0, 1, 0};
    }

    private static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
