package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * FLIP animation ("first, last, invert, play"): elements remember where they were, the DOM changes, and each element
 * that moved starts translated back to its old place, then transitions to its new one. Used for reordering and
 * moving items so a change of layout reads as motion.
 */
public final class Flip {
    private final Document doc;
    private final Map<Element, float[]> first = new HashMap<>();
    private final Function<Element, Dom.Style> styles;

    /** Records where {@code elements} are now; {@code styles} gives each element's inline style holder. */
    public Flip(Document doc, List<Element> elements, Function<Element, Dom.Style> styles) {
        this.doc = doc;
        this.styles = styles;
        for (Element e : elements) {
            styles.apply(e).set("transform", null).set("transition", null).apply();
        }
        for (Element e : elements) first.put(e, e.getBoundingClientRect());
    }

    /** After the DOM changed: inverts every element that moved, then plays them home over {@code seconds}. */
    public void play(float seconds) {
        Map<Element, float[]> moved = new HashMap<>();
        for (Map.Entry<Element, float[]> entry : first.entrySet()) {
            Element e = entry.getKey();
            if (!e.isConnected()) continue;
            float[] last = e.getBoundingClientRect();
            float dx = entry.getValue()[0] - last[0], dy = entry.getValue()[1] - last[1];
            if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) continue;
            moved.put(e, last);
            styles.apply(e).set("transition", "none").set("transform", "translate(" + dx + "px, " + dy + "px)").apply();
        }
        if (moved.isEmpty()) return;
        // The inverted transforms are styled in the next frame; the frame after starts the transitions from them.
        doc.scheduler().requestAnimationFrame(t -> doc.scheduler().requestAnimationFrame(t2 -> {
            for (Element e : moved.keySet()) {
                styles.apply(e).set("transition", "transform " + seconds + "s cubic-bezier(0.2, 0.8, 0.2, 1)")
                        .set("transform", null).apply();
            }
        }));
    }
}
