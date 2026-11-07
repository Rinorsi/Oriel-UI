package dev.oriel.engine.input;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element.ScrollAlign;
import dev.oriel.engine.dom.Element.ScrollBehavior;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.style.Visibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keyboard focus: sequential navigation (Tab / Shift+Tab) in tabindex order, the {@code :focus-visible} flag,
 * {@code autofocus}, and scrolling newly focused elements into view (except when focused by a pointer press, which
 * is already on screen).
 */
final class FocusNavigator {
    private final Document document;
    private boolean visible;
    private boolean autofocusDone;
    /** True while a pointer press moves focus. */
    private boolean byPointer;
    private String source = "initial";
    private boolean byKeyboard;

    String source() { return source; }

    FocusNavigator(Document document) {
        this.document = document;
    }

    /** True when focus last moved by keyboard, false after pointer focus. */
    boolean visible() {
        return visible;
    }

    /**
     * The sequential navigation order: elements with a positive tabindex ascending, then those with tabindex 0, each
     * in tree order. Skips elements that are disabled, not rendered (no box: also the content of a closed
     * {@code <details>}), hidden, and unchecked radios of a group that has a checked one (arrow keys move within a
     * group).
     */
    List<Element> order() {
        if (document.input().backend() == InputBackend.OLD) return legacyOrder();
        List<Element> result = new ArrayList<>();
        document.forEachElement(e -> {
            if (e.isFocusable() && e.tabIndex() >= 0 && isNavigable(e) && isRadioTabStop(e)) result.add(e);
        });
        result.sort((a, b) -> {
            int left = a.tabIndex(), right = b.tabIndex();
            if (left == right) return 0;
            if (left == 0) return 1;
            if (right == 0) return -1;
            return Integer.compare(left, right);
        });
        if (document.input().backend() == InputBackend.BOTH && !result.equals(legacyOrder()))
            throw new IllegalStateException("input focus order differs");
        return result;
    }

    private List<Element> legacyOrder() {
        List<Element> positive = new ArrayList<>(), zero = new ArrayList<>();
        document.forEachElement(e -> {
            if (e.isFocusable() && e.tabIndex() >= 0 && isNavigable(e) && isRadioTabStop(e)) {
                (e.tabIndex() > 0 ? positive : zero).add(e);
            }
        });
        positive.sort(Comparator.comparingInt(Element::tabIndex)); // stable: ties stay in tree order
        positive.addAll(zero);
        return positive;
    }

    /** Moves focus to the next (or previous) element in tab order, wrapping. False when nothing is focusable. */
    boolean move(boolean backward) {
        List<Element> order = order();
        if (order.isEmpty()) return false;
        int i = order.indexOf(document.focusedElement());
        int next = i < 0 ? (backward ? order.size() - 1 : 0) : Math.floorMod(i + (backward ? -1 : 1), order.size());
        Element target = order.get(next);
        focusByKeyboard(target);
        if (target.tagName().equals("input") && target.isTextControl()) TextField.of(target).selectAll();
        return true;
    }

    /** Focuses an element by keyboard, making focus visible. */
    void focusByKeyboard(Element e) {
        setVisible(true);
        source = "keyboard";
        byKeyboard = true;
        try { document.setFocus(e); } finally { byKeyboard = false; }
    }

    /** Focuses an element (or blurs, for null) because of a pointer press. */
    void focusByPointer(Element e) {
        source = "pointer";
        setVisible(false);
        byPointer = true;
        try {
            document.setFocus(e);
        } finally {
            byPointer = false;
        }
    }

    /** Focus moved to {@code e} (by keyboard, script or pointer): scrolls it into view unless the pointer did it. */
    void focusChanged(Element e) {
        if (!byPointer && !byKeyboard) source = "programmatic";
        if (e != null && !byPointer) e.scrollIntoView(ScrollAlign.NEAREST, ScrollAlign.NEAREST, ScrollBehavior.INSTANT);
    }

    /** Focuses the first rendered focusable {@code autofocus} element, once, unless something already has focus. */
    void autofocus() {
        if (autofocusDone) return;
        autofocusDone = true;
        if (document.focusedElement() != null) return;
        Element first = document.firstDescendant(e -> e.hasAttribute("autofocus") && e.isFocusable() && isNavigable(e));
        if (first != null) focusByKeyboard(first);
    }

    private void setVisible(boolean v) {
        if (visible == v) return;
        visible = v;
        document.invalidateStyle(); // :focus-visible
    }

    private static boolean isNavigable(Element e) {
        return !InputEligibility.inert(e) && e.box != null && (e.style == null || e.style.visibility == Visibility.VISIBLE);
    }

    /** Geometric navigation is an explicit host action, never an implicit override of text editing keys. */
    boolean spatial(String direction) {
        int dx = 0, dy = 0;
        switch (direction) {
            case "left" -> dx = -1;
            case "right" -> dx = 1;
            case "up" -> dy = -1;
            case "down" -> dy = 1;
            default -> throw new IllegalArgumentException("navigation direction must be left, right, up or down");
        }
        Element current = document.focusedElement();
        if (current == null || !isNavigable(current)) return move(false);
        float[] from = current.getBoundingClientRect();
        double cx = from[0] + from[2] / 2, cy = from[1] + from[3] / 2;
        Element best = null;
        double score = Double.POSITIVE_INFINITY;
        for (Element e : order()) {
            if (e == current) continue;
            float[] r = e.getBoundingClientRect();
            double x = r[0] + r[2] / 2 - cx, y = r[1] + r[3] / 2 - cy;
            double forward = x * dx + y * dy;
            if (forward <= 0) continue;
            double lateral = x * dy - y * dx;
            double candidate = forward * forward + lateral * lateral * 4;
            if (candidate < score) { best = e; score = candidate; }
        }
        if (best == null) return false;
        focusByKeyboard(best);
        source = "navigation-action";
        return true;
    }

    private static boolean isRadioTabStop(Element e) {
        if (!e.inputType().equals("radio") || e.checked()) return true;
        for (Element r : e.radioGroup()) if (r.checked()) return false;
        return true;
    }
}
