package dev.oriel.engine.css;

import dev.oriel.engine.Limits;
import dev.oriel.engine.css.ComponentValue.Func;
import dev.oriel.engine.css.Token.Type;
import dev.oriel.engine.style.BackgroundLayer;
import dev.oriel.engine.style.ClipShape;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.Image;
import dev.oriel.engine.style.Length;
import dev.oriel.engine.style.MaskComposite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Values of the properties that paint an element as a group: {@code filter} and {@code backdrop-filter} (Filter
 * Effects 1), {@code clip-path} basic shapes (CSS Shapes 1) and the mask layers (CSS Masking 1).
 */
final class Effects {
    private static final Map<String, MaskComposite> WEBKIT_COMPOSITE = Map.of("source-over", MaskComposite.ADD,
            "source-out", MaskComposite.SUBTRACT, "source-in", MaskComposite.INTERSECT, "xor", MaskComposite.EXCLUDE,
            "add", MaskComposite.ADD, "subtract", MaskComposite.SUBTRACT, "intersect", MaskComposite.INTERSECT,
            "exclude", MaskComposite.EXCLUDE);

    private Effects() {}

    // ---- filter, backdrop-filter ----

    /** {@code none | <filter-function>+}; {@code url()} references are not supported (invalid). */
    static List<Filter> filters(ValueReader r, ValueContext ctx) {
        if (r.ident("none")) return List.of();
        List<Filter> out = new ArrayList<>();
        while (!r.atEnd()) {
            if (!(r.next() instanceof Func f)) return null;
            Filter filter = filter(f, ctx);
            if (filter == null || out.size() >= Limits.current().maxListItems()) return null;
            out.add(filter);
        }
        return out.isEmpty() ? null : List.copyOf(out);
    }

    private static Filter filter(Func f, ValueContext ctx) {
        ValueReader a = new ValueReader(f.args());
        Filter result = switch (f.name().toLowerCase(Locale.ROOT)) {
            case "blur" -> {
                if (a.atEnd()) yield Filter.of(Filter.Kind.BLUR, 0);
                Float px = Numeric.px(a, ctx, false);
                yield px == null ? null : Filter.of(Filter.Kind.BLUR, px);
            }
            case "brightness" -> amount(a, ctx, Filter.Kind.BRIGHTNESS);
            case "contrast" -> amount(a, ctx, Filter.Kind.CONTRAST);
            case "grayscale" -> amount(a, ctx, Filter.Kind.GRAYSCALE);
            case "invert" -> amount(a, ctx, Filter.Kind.INVERT);
            case "opacity" -> amount(a, ctx, Filter.Kind.OPACITY);
            case "saturate" -> amount(a, ctx, Filter.Kind.SATURATE);
            case "sepia" -> amount(a, ctx, Filter.Kind.SEPIA);
            case "hue-rotate" -> {
                if (a.atEnd()) yield Filter.of(Filter.Kind.HUE_ROTATE, 0);
                Float deg = Numeric.angle(a, ctx);
                yield deg == null ? null : Filter.of(Filter.Kind.HUE_ROTATE, deg);
            }
            case "drop-shadow" -> dropShadow(a, ctx);
            default -> null;
        };
        return result != null && a.atEnd() ? result : null;
    }

    /** A number or percentage, 1 when omitted; never negative. */
    private static Filter amount(ValueReader a, ValueContext ctx, Filter.Kind kind) {
        if (a.atEnd()) return Filter.of(kind, 1);
        Float v = Numeric.fraction(a, ctx);
        return v == null || v < 0 ? null : Filter.of(kind, v);
    }

    /** {@code [<color>? && <offset-x> <offset-y> <blur>?]}. */
    private static Filter dropShadow(ValueReader a, ValueContext ctx) {
        Integer color = null;
        float[] lengths = new float[3];
        int count = 0;
        while (!a.atEnd()) {
            if (color == null) {
                color = CssColors.read(a, ctx);
                if (color != null) continue;
            }
            Float px = Numeric.px(a, ctx, true);
            if (px == null || count == 3 || (count == 2 && px < 0)) return null;
            lengths[count++] = px;
        }
        if (count < 2) return null;
        return new Filter(Filter.Kind.DROP_SHADOW, lengths[2], lengths[0], lengths[1], color == null ? ctx.currentColor() : color);
    }

    static String serializeFilters(List<Filter> filters) {
        if (filters.isEmpty()) return "none";
        return CssText.join(filters, " ", f -> switch (f.kind()) {
            case BLUR -> "blur(" + CssText.px(f.amount()) + ")";
            case HUE_ROTATE -> "hue-rotate(" + CssText.number(f.amount()) + "deg)";
            case DROP_SHADOW -> "drop-shadow(" + CssColors.serialize(f.color()) + " " + CssText.px(f.x()) + " "
                    + CssText.px(f.y()) + " " + CssText.px(f.amount()) + ")";
            default -> f.kind().name().toLowerCase(Locale.ROOT) + "(" + CssText.number(f.amount()) + ")";
        });
    }

    // ---- clip-path ----

    /** {@code none | <basic-shape> [border-box]?}; other reference boxes and {@code url()} are not supported. */
    static ClipShape clipPath(ValueReader r, ValueContext ctx) {
        if (r.ident("none")) return ClipShape.NONE;
        ClipShape shape = null;
        boolean box = false;
        while (!r.atEnd()) {
            if (!box && r.ident("border-box")) {
                box = true;
            } else if (shape == null && r.peek() instanceof Func f) {
                r.next();
                shape = shape(f, ctx);
                if (shape == null) return null;
            } else {
                return null;
            }
        }
        return shape;
    }

    private static ClipShape shape(Func f, ValueContext ctx) {
        ValueReader a = new ValueReader(f.args());
        return switch (f.name().toLowerCase(Locale.ROOT)) {
            case "inset" -> {
                List<Length> sides = new ArrayList<>(4);
                Length radius = null;
                while (!a.atEnd()) {
                    if (a.ident("round")) {
                        radius = Numeric.length(a, ctx, false);
                        if (radius == null || !a.atEnd()) yield null;
                        break;
                    }
                    Length l = Numeric.length(a, ctx, true);
                    if (l == null || sides.size() == 4) yield null;
                    sides.add(l);
                }
                if (sides.isEmpty()) yield null;
                Length t = sides.get(0), rt = sides.size() > 1 ? sides.get(1) : t;
                Length b = sides.size() > 2 ? sides.get(2) : t, l = sides.size() > 3 ? sides.get(3) : rt;
                yield new ClipShape.Inset(t, rt, b, l, radius);
            }
            case "circle" -> {
                Length radius = null;
                boolean farthest = false;
                if (a.ident("farthest-side")) farthest = true;
                else if (!a.ident("closest-side")) radius = Numeric.length(a, ctx, false);
                Length[] at = at(a, ctx);
                yield at == null ? null : new ClipShape.Circle(radius, farthest, at[0], at[1]);
            }
            case "ellipse" -> {
                Length rx = null, ry = null;
                boolean fx = false, fy = false;
                if (!(a.peekIdent() != null && a.peekIdent().equals("at")) && !a.atEnd()) {
                    if (a.ident("farthest-side")) fx = true;
                    else if (!a.ident("closest-side") && (rx = Numeric.length(a, ctx, false)) == null) yield null;
                    if (a.ident("farthest-side")) fy = true;
                    else if (!a.ident("closest-side") && (ry = Numeric.length(a, ctx, false)) == null) yield null;
                }
                Length[] at = at(a, ctx);
                yield at == null ? null : new ClipShape.Ellipse(rx, ry, fx, fy, at[0], at[1]);
            }
            case "polygon" -> {
                List<List<ComponentValue>> parts = ValueReader.splitCommas(f.args());
                boolean evenOdd = false;
                int start = 0;
                ValueReader first = new ValueReader(parts.get(0));
                if (first.ident("evenodd")) {
                    evenOdd = true;
                    start = 1;
                } else if (first.ident("nonzero")) {
                    start = 1;
                }
                if (start == 1 && !first.atEnd()) yield null;
                List<Length> points = new ArrayList<>();
                for (int i = start; i < parts.size(); i++) {
                    ValueReader p = new ValueReader(parts.get(i));
                    Length x = Numeric.length(p, ctx, true), y = Numeric.length(p, ctx, true);
                    if (x == null || y == null || !p.atEnd()) yield null;
                    points.add(x);
                    points.add(y);
                }
                yield points.size() < 6 ? null : new ClipShape.Polygon(List.copyOf(points), evenOdd);
            }
            default -> null;
        };
    }

    /** {@code [at <position>]}, centred when omitted: one or two keywords or lengths. Null when malformed. */
    private static Length[] at(ValueReader a, ValueContext ctx) {
        Length[] centre = {Length.PERCENT_50, Length.PERCENT_50};
        if (a.atEnd()) return centre;
        if (!a.ident("at")) return null;
        Length x = null, y = null;
        for (int i = 0; i < 2 && !a.atEnd(); i++) {
            String id = a.peekIdent();
            if (id != null) {
                a.next();
                switch (id) {
                    case "left" -> x = Length.ZERO;
                    case "right" -> x = Length.PERCENT_100;
                    case "top" -> y = Length.ZERO;
                    case "bottom" -> y = Length.PERCENT_100;
                    case "center" -> {
                        if (x == null) x = Length.PERCENT_50;
                        else y = Length.PERCENT_50;
                    }
                    default -> {
                        return null;
                    }
                }
            } else {
                Length l = Numeric.length(a, ctx, true);
                if (l == null) return null;
                if (x == null) x = l;
                else y = l;
            }
        }
        if (!a.atEnd()) return null;
        return new Length[] {x == null ? Length.PERCENT_50 : x, y == null ? Length.PERCENT_50 : y};
    }

    static String serializeClipPath(ClipShape shape) {
        return switch (shape) {
            case ClipShape.None n -> "none";
            case ClipShape.Inset in -> "inset(" + String.valueOf(in.top()) + " " + String.valueOf(in.right()) + " "
                    + String.valueOf(in.bottom()) + " " + String.valueOf(in.left())
                    + (in.radius() == null ? "" : " round " + String.valueOf(in.radius())) + ")";
            case ClipShape.Circle c -> "circle(" + (c.radius() != null ? String.valueOf(c.radius()) : c.farthest() ? "farthest-side" : "closest-side")
                    + " at " + String.valueOf(c.cx()) + " " + String.valueOf(c.cy()) + ")";
            case ClipShape.Ellipse e -> "ellipse(" + (e.rx() != null ? String.valueOf(e.rx()) : e.farthestX() ? "farthest-side" : "closest-side") + " "
                    + (e.ry() != null ? String.valueOf(e.ry()) : e.farthestY() ? "farthest-side" : "closest-side")
                    + " at " + String.valueOf(e.cx()) + " " + String.valueOf(e.cy()) + ")";
            case ClipShape.Polygon p -> {
                StringBuilder s = new StringBuilder("polygon(" + (p.evenOdd() ? "evenodd, " : ""));
                for (int i = 0; i < p.points().size(); i += 2) {
                    if (i > 0) s.append(", ");
                    s.append(p.points().get(i)).append(" ").append(p.points().get(i + 1));
                }
                yield s.append(')').toString();
            }
        };
    }

    // ---- masks ----

    /** {@code none | [<image> | none]#}: the layers' images, {@code none} layers left out. */
    static List<Image> maskImages(ValueReader r, ValueContext ctx) {
        List<Image> out = new ArrayList<>();
        for (List<ComponentValue> layer : ValueReader.splitCommas(r.rest())) {
            ValueReader l = new ValueReader(layer);
            if (l.ident("none") && l.atEnd()) continue;
            Image image = Images.read(l, ctx);
            if (image == null || !l.atEnd()) return null;
            out.add(image);
        }
        return List.copyOf(out);
    }

    /** {@code <compositing-operator>#}, also the {@code -webkit-mask-composite} names. */
    static List<MaskComposite> maskComposites(ValueReader r, ValueContext ctx) {
        List<MaskComposite> out = new ArrayList<>();
        for (List<ComponentValue> layer : ValueReader.splitCommas(r.rest())) {
            ValueReader l = new ValueReader(layer);
            String id = l.peekIdent();
            MaskComposite c = id == null ? null : WEBKIT_COMPOSITE.get(id);
            if (c == null) return null;
            l.next();
            if (!l.atEnd()) return null;
            out.add(c);
        }
        return out.isEmpty() ? null : List.copyOf(out);
    }

    /** {@code mask-clip}: {@code border-box | padding-box | content-box | no-clip}, per layer. */
    static List<BackgroundLayer.Box> maskClips(ValueReader r, ValueContext ctx) {
        List<BackgroundLayer.Box> out = new ArrayList<>();
        for (List<ComponentValue> layer : ValueReader.splitCommas(r.rest())) {
            ValueReader l = new ValueReader(layer);
            BackgroundLayer.Box box = box(l.peekIdent());
            if (box == null) return null;
            l.next();
            if (!l.atEnd()) return null;
            out.add(box);
        }
        return out.isEmpty() ? null : List.copyOf(out);
    }

    static BackgroundLayer.Box box(String id) {
        if (id == null) return null;
        return switch (id) {
            case "border-box", "no-clip" -> BackgroundLayer.Box.BORDER_BOX;
            case "padding-box" -> BackgroundLayer.Box.PADDING_BOX;
            case "content-box" -> BackgroundLayer.Box.CONTENT_BOX;
            default -> null;
        };
    }

    static boolean isCompositeName(String id) {
        return id != null && WEBKIT_COMPOSITE.containsKey(id);
    }

    static boolean isToken(ComponentValue v, Type type) {
        return v instanceof Token t && t.is(type);
    }
}
