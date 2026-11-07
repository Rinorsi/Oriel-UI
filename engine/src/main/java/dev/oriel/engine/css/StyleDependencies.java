package dev.oriel.engine.css;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Reverse selector dependencies compiled into conservative paths from a mutation to possible subjects. */
final class StyleDependencies {
    private final Map<String, Set<String>> paths = new HashMap<>();

    void add(Selector selector) { selector(selector, ""); }

    private void selector(Selector selector, String continuation) {
        for (int i = 0; i < selector.compounds.length; i++) {
            String path = new String(selector.combinators, i, selector.combinators.length - i) + continuation;
            for (Selector.Simple simple : selector.compounds[i].simples()) simple(simple, path);
        }
    }

    private void simple(Selector.Simple simple, String path) {
        if (simple instanceof Selector.Logical logical) {
            for (Selector nested : logical.selectors()) selector(nested, path);
        } else if (simple instanceof Selector.Has has) {
            for (Selector relative : has.relative()) {
                String reverse = "";
                for (int i = 1; i < relative.compounds.length; i++) {
                    reverse = reverse(relative.combinators[i - 1]) + reverse;
                    for (Selector.Simple nested : relative.compounds[i].simples()) simple(nested, reverse + path);
                }
            }
        } else if (simple instanceof Selector.Nth nth && nth.of() != null) {
            for (Selector filter : nth.of()) selector(filter, "S" + path);
        } else {
            Set<String> attributes = new HashSet<>();
            simple.attributesRead(attributes);
            String effect = simple == Selector.PseudoClass.DISABLED || simple == Selector.PseudoClass.ENABLED
                    || simple == Selector.PseudoClass.CHECKED ? "!" : path;
            for (String attribute : attributes) add(attribute, effect);
            if (simple == Selector.PseudoClass.HOVER) add("@hover", path);
            if (simple == Selector.PseudoClass.ACTIVE) add("@active", path);
            if (simple == Selector.PseudoClass.FOCUS || simple == Selector.PseudoClass.FOCUS_VISIBLE) add("@focus", path);
            if (simple == Selector.PseudoClass.FOCUS_WITHIN) add("@focus", "I" + path);
        }
    }

    private void add(String key, String path) { paths.computeIfAbsent(key, unused -> new HashSet<>()).add(path); }

    private static char reverse(char axis) {
        return switch (axis) { case '>' -> 'P'; case ' ' -> 'A'; case '+' -> 'p'; case '~' -> 'q';
            default -> throw new IllegalArgumentException("Unknown selector axis: " + axis); };
    }

    /** False requests the full compatibility path; over-invalidation is allowed, missed subjects are not. */
    boolean mark(Element element, String key, Set<Element> affected) {
        if ((element.tagName().equals("link") && (key.equals("rel") || key.equals("href") || key.equals("media")))
                || element.tagName().equals("style") && key.equals("media")) return false;
        Set<String> effects = paths.get(key);
        if (effects == null) return true;
        if (effects.contains("!")) return false;
        for (String path : effects) {
            if (path.isEmpty()) { affected.add(element); continue; }
            Set<Element> current = Set.of(element);
            for (int i = 0; i < path.length(); i++) {
                Set<Element> next = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Element from : current) step(from, path.charAt(i), next);
                current = next;
            }
            affected.addAll(current);
        }
        return true;
    }

    private static void step(Element element, char axis, Set<Element> out) {
        switch (axis) {
            case '>' -> children(element, out, false);
            case ' ' -> children(element, out, true);
            case 'P' -> { if (element.parentElement() != null) out.add(element.parentElement()); }
            case 'A', 'I' -> {
                for (Element e = axis == 'I' ? element : element.parentElement(); e != null; e = e.parentElement()) out.add(e);
            }
            default -> siblings(element, axis, out);
        }
    }

    private static void children(Element element, Set<Element> out, boolean recursive) {
        for (int i = 0; i < element.childCount(); i++) {
            if (element.childAt(i) instanceof Element child) {
                out.add(child);
                if (recursive) children(child, out, true);
            }
        }
    }

    private static void siblings(Element element, char axis, Set<Element> out) {
        Node parent = element.parentNode();
        if (parent == null) return;
        Element previous = null;
        boolean after = false;
        for (int i = 0; i < parent.childCount(); i++) {
            if (!(parent.childAt(i) instanceof Element sibling)) continue;
            if (sibling == element) {
                if (axis == 'p' && previous != null) out.add(previous);
                after = true;
                if (axis == 'S') out.add(sibling);
            } else if (axis == 'S' || after && (axis == '+' || axis == '~') || !after && axis == 'q') {
                out.add(sibling);
                if (axis == '+') return;
            }
            previous = sibling;
        }
    }
}
