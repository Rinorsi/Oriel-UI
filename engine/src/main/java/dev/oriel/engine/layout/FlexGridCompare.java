package dev.oriel.engine.layout;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;

import java.util.IdentityHashMap;
import java.util.Map;

/** Opt-in independent-tree migration oracle. The reference pass never clamps scroll or emits scroll events. */
final class FlexGridCompare {
    private static final ThreadLocal<Boolean> REFERENCE = new ThreadLocal<>();

    private FlexGridCompare() {}

    static String mode(String context) {
        String mode = System.getProperty("oriel.layout." + context, "old");
        if (!mode.equals("old") && !mode.equals("new") && !mode.equals("both"))
            throw new IllegalArgumentException("oriel.layout." + context + " must be old, new or both: " + mode);
        return mode.equals("both") ? (Boolean.TRUE.equals(REFERENCE.get()) ? "old" : "new") : mode;
    }

    record Reference(Box root, Map<Element, Box> principals) {}

    static Reference begin(Document document) {
        if (!"both".equals(System.getProperty("oriel.layout.flex"))) return null;
        Element html = document.documentElement();
        if (html == null) return null;
        REFERENCE.set(true);
        try {
            LayoutBox root = new BoxTreeBuilder().build(html);
            if (root == null) return null;
            float width = document.viewportWidth(), height = document.viewportHeight();
            // Text metrics are a shared, pure host cache, not a box/constraint measurement cache.
            // Reuse it so diagnostic double-runs do not duplicate font requests or change host behaviour.
            LayoutPass pass = new LayoutPass(document.layoutEngine().textMeasure(), width, height);
            pass.layout(root, pass.blockLevelWidth(root, width, height), Float.NaN, width, height);
            root.x = root.marginLeft;
            root.y = root.marginTop;
            PositionedLayout.applyRelativeOffset(root, width, height);
            new PositionedLayout(pass).layoutOutOfFlow(root);
            Map<Element, Box> principals = new IdentityHashMap<>();
            capture(html, principals);
            return new Reference(root, principals);
        } finally {
            REFERENCE.remove();
        }
    }

    private static void capture(Node node, Map<Element, Box> boxes) {
        if (node instanceof Element element) boxes.put(element, element.box);
        for (int i = 0; i < node.childCount(); i++) capture(node.childAt(i), boxes);
    }

    static void verify(Reference reference, Box actual, String url) {
        if (reference == null) return;
        compare(reference.root, actual, url + " /html");
        // Inline principal boxes do not belong to Box.children; compare them separately.
        for (var entry : reference.principals.entrySet()) {
            compareRect(entry.getValue(), entry.getKey().box, url + " " + entry.getKey());
        }
    }

    static void compare(Box expected, Box actual, String path) {
        compareRect(expected, actual, path);
        if (expected == null) return;
        if (expected.children.size() != actual.children.size() || expected.lines.size() != actual.lines.size())
            throw new IllegalStateException("Layout differential: " + path + " child/line count");
        for (int i = 0; i < expected.children.size(); i++)
            compare(expected.children.get(i), actual.children.get(i), path + "/" + i);
        for (int i = 0; i < expected.lines.size(); i++) {
            LineBox a = expected.lines.get(i), b = actual.lines.get(i);
            String at = path + " line[" + i + "]";
            same(a.x, b.x, at, "x");
            same(a.y, b.y, at, "y");
            same(a.width, b.width, at, "width");
            same(a.height, b.height, at, "height");
            same(a.baseline, b.baseline, at, "baseline");
            if (a.fragments.size() != b.fragments.size())
                throw new IllegalStateException("Layout differential: " + at + " fragment count");
            for (int j = 0; j < a.fragments.size(); j++) {
                Fragment x = a.fragments.get(j), y = b.fragments.get(j);
                same(x.x(), y.x(), at, "fragment[" + j + "].x");
                same(x.y(), y.y(), at, "fragment[" + j + "].y");
                same(x.width(), y.width(), at, "fragment[" + j + "].width");
                same(x.height(), y.height(), at, "fragment[" + j + "].height");
            }
        }
    }

    private static void compareRect(Box a, Box b, String path) {
        if (a == null || b == null) {
            if (a != b) throw new IllegalStateException("Layout differential: " + path + " presence");
            return;
        }
        if (a.element != b.element || a.kind != b.kind)
            throw new IllegalStateException("Layout differential: " + path + " box identity/kind");
        path += " " + a.element + " display=" + a.style.display;
        same(a.x, b.x, path, "x"); same(a.y, b.y, path, "y");
        same(a.width, b.width, path, "width"); same(a.height, b.height, path, "height");
        same(a.baseline, b.baseline, path, "baseline");
        same(a.marginTop, b.marginTop, path, "margin-top");
        same(a.marginRight, b.marginRight, path, "margin-right");
        same(a.marginBottom, b.marginBottom, path, "margin-bottom");
        same(a.marginLeft, b.marginLeft, path, "margin-left");
        same(a.paddingTop, b.paddingTop, path, "padding-top");
        same(a.paddingRight, b.paddingRight, path, "padding-right");
        same(a.paddingBottom, b.paddingBottom, path, "padding-bottom");
        same(a.paddingLeft, b.paddingLeft, path, "padding-left");
        same(a.borderTop, b.borderTop, path, "border-top");
        same(a.borderRight, b.borderRight, path, "border-right");
        same(a.borderBottom, b.borderBottom, path, "border-bottom");
        same(a.borderLeft, b.borderLeft, path, "border-left");
    }

    private static void same(float expected, float actual, String path, String field) {
        if (Float.compare(expected, actual) != 0)
            throw new IllegalStateException("Layout differential: " + path + " " + field
                    + " old=" + expected + " new=" + actual);
    }
}
