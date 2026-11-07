package dev.oriel.engine.layout;

import com.google.gson.Gson;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.paint.Coordinates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Immutable, on-demand geometry for diagnostics and exact migration comparisons. */
public record LayoutSnapshot(List<Entry> boxes) {
    public LayoutSnapshot { boxes = List.copyOf(boxes); }

    /** Numeric values use Float.toString, including NaN and infinities, so the JSON is always valid. */
    public record Entry(String id, String parent, String element, String kind, Map<String, String> values,
                        List<Line> lines) {
        public Entry {
            values = Map.copyOf(values);
            lines = List.copyOf(lines);
        }
    }

    public record Line(List<String> geometry, List<Piece> fragments) {
        public Line { geometry = List.copyOf(geometry); fragments = List.copyOf(fragments); }
    }

    public record Piece(String kind, String box, List<String> geometry, String content) {
        public Piece { geometry = List.copyOf(geometry); }
    }

    public String toJson() { return new Gson().toJson(this); }

    /** Fails at the first differing box/property, with the page and caller-provided operation. */
    public void requireSame(LayoutSnapshot actual, String context) {
        if (boxes.size() != actual.boxes.size()) {
            throw new IllegalStateException(context + ": box count " + boxes.size() + " != " + actual.boxes.size());
        }
        for (int i = 0; i < boxes.size(); i++) {
            Entry expected = boxes.get(i), found = actual.boxes.get(i);
            if (expected.equals(found)) continue;
            String prefix = context + " box=" + expected.id + " element=" + expected.element;
            if (!expected.id.equals(found.id) || !expected.parent.equals(found.parent) || !expected.kind.equals(found.kind)) {
                throw new IllegalStateException(prefix + " tree differs: " + found.id + " parent=" + found.parent);
            }
            for (var value : expected.values.entrySet()) {
                String other = found.values.get(value.getKey());
                if (!value.getValue().equals(other)) {
                    throw new IllegalStateException(prefix + " property=" + value.getKey() + " old=" + value.getValue() + " new=" + other);
                }
            }
            throw new IllegalStateException(prefix + " line fragments or metadata differ");
        }
    }

    /** Weak element keys keep identities stable without retaining detached DOM subtrees. */
    static final class Ids {
        private final Map<Element, Long> elements = new WeakHashMap<>();
        private long next = 1;

        long element(Element element) {
            return elements.computeIfAbsent(element, ignored -> next++);
        }
    }

    static LayoutSnapshot capture(Box root, Ids ids, float viewportWidth, float viewportHeight) {
        if (root == null) return new LayoutSnapshot(List.of());
        Map<Box, String> names = new IdentityHashMap<>();
        Map<Element, Integer> anonymous = new IdentityHashMap<>();
        List<Box> ordered = new ArrayList<>();
        collect(root, ids, names, anonymous, ordered);
        for (Element element : root.element.descendants(e -> e.box != null)) {
            collect(element.box, ids, names, anonymous, ordered);
        }
        List<Entry> result = new ArrayList<>(ordered.size());
        for (Box box : ordered) {
            Map<String, String> values = new LinkedHashMap<>();
            add(values, "borderBox", box.x, box.y, box.width, box.height);
            add(values, "margin", box.marginTop, box.marginRight, box.marginBottom, box.marginLeft);
            add(values, "border", box.borderTop, box.borderRight, box.borderBottom, box.borderLeft);
            add(values, "padding", box.paddingTop, box.paddingRight, box.paddingBottom, box.paddingLeft);
            add(values, "scroll", box.scrollWidth, box.scrollHeight, box.scrollLeft(), box.scrollTop());
            add(values, "baseline", box.baseline);
            add(values, "hostOffset", box.hostOffsetX, box.hostOffsetY);
            add(values, "viewport", Coordinates.boundingRect(box));
            float[] visible = Coordinates.visibleRect(box, viewportWidth, viewportHeight);
            if (visible != null) add(values, "visibleBounds", visible);
            values.put("visible", Boolean.toString(visible != null));
            values.put("outOfFlow", Boolean.toString(box.outOfFlow));
            values.put("atomicInline", Boolean.toString(box.atomicInline));
            values.put("containingBlock", names.getOrDefault(box.containingBlock, "viewport"));
            values.put("contentParent", names.getOrDefault(box.contentParent(), "viewport"));
            values.put("overflowX", box.style.overflowX.name());
            values.put("overflowY", box.style.overflowY.name());
            if (box instanceof LayoutBox layout) add(values, "overflowExtent", layout.overflowRight, layout.overflowBottom);
            List<Line> lines = new ArrayList<>();
            for (LineBox line : box.lines) {
                List<Piece> fragments = new ArrayList<>();
                for (Fragment fragment : line.fragments) {
                    String ref = "", content = "";
                    if (fragment instanceof Fragment.InlineBox inline) {
                        ref = names.get(inline.box());
                        content = inline.first() + ":" + inline.last() + ":" + inline.end();
                    } else if (fragment instanceof Fragment.Atomic atomic) {
                        ref = names.get(atomic.box());
                    } else if (fragment instanceof Fragment.TextRun text) {
                        content = text.text() + "|source=" + Arrays.toString(text.source());
                        if (text.spaced() != null) content += "|parts=" + Arrays.toString(text.spaced().parts())
                                + "|offsets=" + Arrays.toString(text.spaced().x());
                    }
                    fragments.add(new Piece(fragment.getClass().getSimpleName(), ref,
                            numbers(fragment.x(), fragment.y(), fragment.width(), fragment.height()), content));
                }
                lines.add(new Line(numbers(line.x, line.y, line.width, line.height, line.baseline), fragments));
            }
            result.add(new Entry(names.get(box), names.getOrDefault(box.parent, "viewport"),
                    box.element.toString(), box.kind.name(), values, lines));
        }
        return new LayoutSnapshot(result);
    }

    private static void collect(Box box, Ids ids, Map<Box, String> names, Map<Element, Integer> anonymous, List<Box> ordered) {
        if (names.containsKey(box)) return;
        String role = switch (box.kind) {
            case ANONYMOUS -> "anonymous-" + anonymous.merge(box.element, 1, Integer::sum);
            case PSEUDO -> box.style == box.element.beforeStyle ? "before" : "after";
            default -> "principal";
        };
        names.put(box, "e" + ids.element(box.element) + ":" + role);
        ordered.add(box);
        for (Box child : box.children) collect(child, ids, names, anonymous, ordered);
        if (box instanceof LayoutBox layout && layout.inline != null) {
            for (InlineContent.Item item : layout.inline.items) {
                if (item instanceof InlineContent.Open open && open.span().box != null) {
                    collect(open.span().box, ids, names, anonymous, ordered);
                } else if (item instanceof InlineContent.Break br && br.box() != null) {
                    collect(br.box(), ids, names, anonymous, ordered);
                }
            }
        }
        for (LineBox line : box.lines) {
            for (Fragment fragment : line.fragments) {
                if (fragment instanceof Fragment.InlineBox inline) collect(inline.box(), ids, names, anonymous, ordered);
            }
        }
    }

    private static List<String> numbers(float... numbers) {
        List<String> values = new ArrayList<>(numbers.length);
        for (float value : numbers) values.add(Float.toString(value));
        return values;
    }

    private static void add(Map<String, String> values, String prefix, float... numbers) {
        for (int i = 0; i < numbers.length; i++) values.put(prefix + "[" + i + "]", Float.toString(numbers[i]));
    }
}
