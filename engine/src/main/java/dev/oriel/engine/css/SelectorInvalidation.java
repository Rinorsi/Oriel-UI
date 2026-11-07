package dev.oriel.engine.css;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Conservative reverse dependencies for attribute-only changes. Unknown shapes retain a full rematch. */
final class SelectorInvalidation {
    private static final int LOCAL = 1, PARENT = 2, ANCESTORS = 4, PREVIOUS = 8, PREVIOUS_ALL = 16, SIBLINGS = 32, FULL = 64;
    private final Map<String, Integer> effects = new HashMap<>();

    void add(Selector selector) { collect(selector, LOCAL); }

    private void collect(Selector selector, int effect) {
        for (int i = 0; i < selector.compounds.length; i++) {
            int actual = i == selector.compounds.length - 1 ? effect : FULL;
            for (Selector.Simple simple : selector.compounds[i].simples()) collect(simple, actual);
        }
    }

    private void collect(Selector.Simple simple, int effect) {
        if (effect == FULL) { record(simple, FULL); return; }
        if (simple instanceof Selector.Logical logical) {
            for (Selector nested : logical.selectors()) {
                if (nested.compounds.length == 1) collect(nested, effect);
                else record(simple, FULL);
            }
        } else if (simple instanceof Selector.Has has) {
            if (effect != LOCAL) { record(simple, FULL); return; }
            for (Selector relative : has.relative()) {
                if (relative.compounds.length != 2) { record(simple, FULL); continue; }
                int reverse = switch (relative.combinators[0]) {
                    case '>' -> PARENT;
                    case ' ' -> ANCESTORS;
                    case '+' -> PREVIOUS;
                    case '~' -> PREVIOUS_ALL;
                    default -> FULL;
                };
                for (Selector.Simple nested : relative.subject().simples()) {
                    // Filters and functional paths may read siblings or ancestors of the relative subject.
                    if (nested instanceof Selector.Logical || nested instanceof Selector.Nth || nested instanceof Selector.Has) record(nested, FULL);
                    else collect(nested, reverse);
                }
            }
        } else if (simple instanceof Selector.Nth nth && nth.of() != null) {
            if (effect != LOCAL) { record(simple, FULL); return; }
            for (Selector filter : nth.of()) {
                if (filter.compounds.length == 1) collect(filter, SIBLINGS);
                else record(simple, FULL);
            }
        } else {
            // Disabled state can be inherited from fieldset/optgroup ancestors.
            if (simple == Selector.PseudoClass.DISABLED || simple == Selector.PseudoClass.ENABLED
                    || simple == Selector.PseudoClass.CHECKED) effect = FULL;
            record(simple, effect);
        }
    }

    private void record(Selector.Simple simple, int effect) {
        Set<String> attributes = new HashSet<>();
        simple.attributesRead(attributes);
        for (String attribute : attributes) effects.merge(attribute, effect, (a, b) -> a | b);
    }

    /** Returns false when stylesheet discovery or a nonlocal dependency needs the original full pass. */
    boolean mark(Element element, String attribute, Set<Element> affected) {
        if ((element.tagName().equals("link") && (attribute.equals("rel") || attribute.equals("href") || attribute.equals("media")))
                || element.tagName().equals("style") && attribute.equals("media")) return false;
        int effect = effects.getOrDefault(attribute, 0);
        if ((effect & FULL) != 0) return false;
        if ((effect & LOCAL) != 0) affected.add(element);
        Element parent = element.parentElement();
        if ((effect & PARENT) != 0 && parent != null) affected.add(parent);
        if ((effect & ANCESTORS) != 0) {
            for (Element ancestor = parent; ancestor != null; ancestor = ancestor.parentElement()) affected.add(ancestor);
        }
        Node container = element.parentNode();
        if ((effect & (PREVIOUS | PREVIOUS_ALL | SIBLINGS)) != 0 && container != null) {
            Element previous = null;
            boolean before = true;
            for (int i = 0; i < container.childCount(); i++) {
                if (!(container.childAt(i) instanceof Element sibling)) continue;
                if (sibling == element) {
                    if ((effect & PREVIOUS) != 0 && previous != null) affected.add(previous);
                    before = false;
                }
                if ((effect & SIBLINGS) != 0 || before && (effect & PREVIOUS_ALL) != 0) affected.add(sibling);
                previous = sibling;
            }
        }
        return true;
    }
}
