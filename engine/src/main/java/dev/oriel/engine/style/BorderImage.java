package dev.oriel.engine.style;

/**
 * Values of the {@code border-image-*} longhands (CSS Backgrounds 3 §6): a texture cut into nine regions by the
 * slice, its corners and edges drawn over the border area and its middle over the padding area when {@code fill}.
 * Sides are in CSS order: top, right, bottom, left.
 */
public final class BorderImage {
    private BorderImage() {}

    /** How a side's value is given. {@link #AUTO} only in border-image-width, {@link #PERCENT} not in the outset. */
    public enum Unit { NUMBER, PX, PERCENT, AUTO }

    /** One side's value: a number (texels of the source in the slice, multiples of the border width elsewhere). */
    public record Side(Unit unit, float value) {
        public static final Side ONE = new Side(Unit.NUMBER, 1), ZERO = new Side(Unit.NUMBER, 0),
                ALL = new Side(Unit.PERCENT, 100);
    }

    /** Four sides. */
    public record Sides(Side top, Side right, Side bottom, Side left) {
        public static final Sides ONE = all(Side.ONE), ZERO = all(Side.ZERO);

        public static Sides all(Side s) {
            return new Sides(s, s, s, s);
        }

        public Side get(int i) {
            return switch (i) {
                case 0 -> top;
                case 1 -> right;
                case 2 -> bottom;
                default -> left;
            };
        }
    }

    /** {@code border-image-source}: a texture URL, or none (a null image). */
    public record Source(Image image) {
        public static final Source NONE = new Source(null);
    }

    /** {@code border-image-slice}; {@code fill} keeps the middle region. Initial: 100%, no fill. */
    public record Slice(Sides sides, boolean fill) {
        public static final Slice INITIAL = new Slice(Sides.all(Side.ALL), false);
    }

    /** {@code border-image-repeat} keywords. */
    public enum Repeat { STRETCH, REPEAT, ROUND, SPACE }

    /** {@code border-image-repeat}: horizontal (top and bottom edges) and vertical (left and right edges). */
    public record Repeats(Repeat x, Repeat y) {
        public static final Repeats STRETCH = new Repeats(Repeat.STRETCH, Repeat.STRETCH);
    }
}
