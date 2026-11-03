package dev.oriel.engine.css;

import dev.oriel.engine.css.Numeric.Kind;
import dev.oriel.engine.css.Numeric.Quantity;
import dev.oriel.engine.css.Token.Type;
import dev.oriel.engine.style.BorderImage;
import dev.oriel.engine.style.BorderImage.Repeat;
import dev.oriel.engine.style.BorderImage.Side;
import dev.oriel.engine.style.BorderImage.Sides;
import dev.oriel.engine.style.BorderImage.Unit;
import dev.oriel.engine.style.Image;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsers and serializers of the {@code border-image-*} longhands (CSS Backgrounds 3 §6). A value that does not fit
 * is invalid (null), so the declaration is dropped and reported like any other.
 */
final class BorderImageValues {
    private BorderImageValues() {}

    /** {@code none | <url>}. Only textures can be sliced: sprites and gradients are invalid here. */
    static BorderImage.Source source(ValueReader r, ValueContext ctx) {
        if (r.next(Type.IDENT) instanceof Token t) return t.lower.equals("none") ? BorderImage.Source.NONE : null;
        Image image = Images.read(r, ctx);
        return image instanceof Image.Url ? new BorderImage.Source(image) : null;
    }

    /** {@code [<number> | <percentage>]{1,4} && fill?}: offsets into the source, numbers being its texels. */
    static BorderImage.Slice slice(ValueReader r, ValueContext ctx) {
        boolean fill = false;
        List<Side> sides = new ArrayList<>();
        while (!r.atEnd()) {
            int m = r.mark();
            if (r.next(Type.IDENT) instanceof Token t) {
                if (!t.lower.equals("fill") || fill) return null;
                fill = true;
                continue;
            }
            r.reset(m);
            Quantity q = Numeric.of(r.next(), ctx);
            Side side = q == null ? null : q.kind() == Kind.NUMBER ? new Side(Unit.NUMBER, q.value())
                    : q.isPercentage() ? new Side(Unit.PERCENT, q.percent()) : null;
            if (side == null || side.value() < 0 || sides.size() == 4) return null;
            sides.add(side);
        }
        return sides.isEmpty() ? null : new BorderImage.Slice(sides(sides), fill);
    }

    /** {@code [<length> | <percentage> | <number> | auto]{1,4}}, none negative. */
    static Sides width(ValueReader r, ValueContext ctx) {
        return sides(r, ctx, true);
    }

    /** {@code [<length> | <number>]{1,4}}, none negative. */
    static Sides outset(ValueReader r, ValueContext ctx) {
        return sides(r, ctx, false);
    }

    private static Sides sides(ValueReader r, ValueContext ctx, boolean width) {
        List<Side> sides = new ArrayList<>();
        while (!r.atEnd()) {
            if (sides.size() == 4) return null;
            int m = r.mark();
            if (r.next(Type.IDENT) instanceof Token t) {
                if (!width || !t.lower.equals("auto")) return null;
                sides.add(new Side(Unit.AUTO, 0));
                continue;
            }
            r.reset(m);
            Quantity q = Numeric.of(r.next(), ctx);
            Side side;
            if (q == null) return null;
            if (q.kind() == Kind.NUMBER) side = new Side(Unit.NUMBER, q.value());
            else if (width && q.isPercentage()) side = new Side(Unit.PERCENT, q.percent());
            else if (q.kind() == Kind.LENGTH && q.length().isLinear() && q.length().percent == 0) side = new Side(Unit.PX, q.length().px);
            else return null;
            if (side.value() < 0) return null;
            sides.add(side);
        }
        return sides.isEmpty() ? null : sides(sides);
    }

    /** {@code [stretch | repeat | round | space]{1,2}}. */
    static BorderImage.Repeats repeat(ValueReader r, ValueContext ctx) {
        List<Repeat> repeats = new ArrayList<>();
        while (!r.atEnd()) {
            if (!(r.next(Type.IDENT) instanceof Token t) || repeats.size() == 2) return null;
            Repeat repeat = switch (t.lower) {
                case "stretch" -> Repeat.STRETCH;
                case "repeat" -> Repeat.REPEAT;
                case "round" -> Repeat.ROUND;
                case "space" -> Repeat.SPACE;
                default -> null;
            };
            if (repeat == null) return null;
            repeats.add(repeat);
        }
        if (repeats.isEmpty()) return null;
        return new BorderImage.Repeats(repeats.get(0), repeats.get(repeats.size() - 1));
    }

    /** One to four values, filled in as margins are. */
    private static Sides sides(List<Side> s) {
        return switch (s.size()) {
            case 1 -> Sides.all(s.get(0));
            case 2 -> new Sides(s.get(0), s.get(1), s.get(0), s.get(1));
            case 3 -> new Sides(s.get(0), s.get(1), s.get(2), s.get(1));
            default -> new Sides(s.get(0), s.get(1), s.get(2), s.get(3));
        };
    }

    // ---- Serialization ----

    static String serializeSource(Object v) {
        BorderImage.Source s = (BorderImage.Source) v;
        return s.image() == null ? "none" : Images.serialize(s.image());
    }

    static String serializeSlice(Object v) {
        BorderImage.Slice s = (BorderImage.Slice) v;
        return serialize(s.sides()) + (s.fill() ? " fill" : "");
    }

    static String serializeSides(Object v) {
        return serialize((Sides) v);
    }

    static String serializeRepeat(Object v) {
        BorderImage.Repeats r = (BorderImage.Repeats) v;
        String x = r.x().name().toLowerCase(Locale.ROOT), y = r.y().name().toLowerCase(Locale.ROOT);
        return x.equals(y) ? x : x + " " + y;
    }

    private static String serialize(Sides s) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 4; i++) parts.add(serialize(s.get(i)));
        return CssText.collapse(parts);
    }

    private static String serialize(Side s) {
        return switch (s.unit()) {
            case NUMBER -> CssText.number(s.value());
            case PX -> CssText.number(s.value()) + "px";
            case PERCENT -> CssText.number(s.value()) + "%";
            case AUTO -> "auto";
        };
    }
}
