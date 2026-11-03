package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.event.MouseEvent;

import java.util.function.DoubleConsumer;

/**
 * An Ore slider over a {@code .ore-slider} element (with {@code .track}, {@code .fill} and {@code .thumb} children):
 * press or drag along it to set the value, or use the arrow keys (Shift for bigger steps), Home and End when it has
 * focus. Values run from 0 to 1; {@code steps} snaps them (0 for continuous).
 */
public final class Slider {
    private final Element element;
    private final Dom.Style fill, thumb;
    private final int steps;
    private DoubleConsumer onInput = v -> {}, onCommit = v -> {};
    private double value;
    private boolean dragging;

    public Slider(Document doc, Element element, int steps) {
        this.element = element;
        this.steps = steps;
        fill = new Dom.Style(Dom.query(element, ".fill"));
        thumb = new Dom.Style(Dom.query(element, ".thumb"));
        Dom.onMouse(element, "mousedown", e -> {
            if (e.button != 0) return;
            dragging = true;
            element.addClass("dragging");
            element.focus();
            fromPointer(e);
        });
        Dom.onMouse(doc, "mousemove", e -> {
            if (dragging) fromPointer(e);
        });
        Dom.onMouse(doc, "mouseup", e -> {
            if (!dragging) return;
            dragging = false;
            element.removeClass("dragging");
            onCommit.accept(value);
        });
        Dom.onKey(element, this::key);
    }

    /** Called with every change while the user moves it. */
    public Slider onInput(DoubleConsumer listener) {
        onInput = listener;
        return this;
    }

    /** Called when the user lets go (or with each key press). */
    public Slider onCommit(DoubleConsumer listener) {
        onCommit = listener;
        return this;
    }

    public double value() {
        return value;
    }

    public boolean dragging() {
        return dragging;
    }

    /** Sets the value without telling the listeners. */
    public void set(double v) {
        value = snap(Math.max(0, Math.min(1, v)));
        String pct = Math.round(value * 1000) / 10.0 + "%";
        fill.set("width", pct).apply();
        thumb.set("left", pct).apply();
    }

    private double snap(double v) {
        return steps > 0 ? Math.round(v * steps) / (double) steps : v;
    }

    private void fromPointer(MouseEvent e) {
        float[] r = element.getBoundingClientRect();
        if (r[2] <= 0) return;
        double before = value;
        set((e.clientX - r[0]) / r[2]);
        if (value != before) onInput.accept(value);
    }

    private void key(KeyboardEvent e) {
        double step = steps > 0 ? 1.0 / steps : 0.02;
        if (e.modifiers.shift()) step *= 5;
        double next = switch (e.key) {
            case "ArrowLeft", "ArrowDown" -> value - step;
            case "ArrowRight", "ArrowUp" -> value + step;
            case "Home" -> 0;
            case "End" -> 1;
            case "PageDown" -> value - 0.1;
            case "PageUp" -> value + 0.1;
            default -> Double.NaN;
        };
        if (Double.isNaN(next)) return;
        e.preventDefault();
        set(next);
        onInput.accept(value);
        onCommit.accept(value);
    }
}
