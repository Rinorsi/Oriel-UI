package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.event.Event;
import dev.oriel.engine.event.EventListener;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.event.MouseEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Small DOM helpers for controllers: lookups that fail loudly, change-only writes, and typed listeners. */
public final class Dom {
    private Dom() {}

    /** The element with {@code id}; a page without it is a broken page, so this throws. */
    public static Element byId(Document doc, String id) {
        return Objects.requireNonNull(doc.getElementById(id), () -> doc.url() + ": no element #" + id);
    }

    public static Element query(Node root, String selector) {
        Element e = root instanceof Document d ? d.querySelector(selector) : ((Element) root).querySelector(selector);
        return Objects.requireNonNull(e, () -> "no element matches " + selector);
    }

    /** Sets the text when it differs, so unchanged frames do not invalidate layout. */
    public static void text(Element e, String text) {
        if (!text.equals(e.textContent())) e.setTextContent(text);
    }

    public static void attr(Element e, String name, String value) {
        if (!value.equals(e.getAttribute(name))) e.setAttribute(name, value);
    }

    /** A new element with classes and optional text. */
    public static Element el(Document doc, String tag, String classes, String text) {
        Element e = doc.createElement(tag);
        if (classes != null && !classes.isEmpty()) e.setAttribute("class", classes);
        if (text != null) e.setTextContent(text);
        return e;
    }

    /** The first element of a markup fragment. */
    public static Element html(Document doc, String markup) {
        Element holder = doc.createElement("div");
        holder.setInnerHTML(markup);
        Element first = holder.children().getFirst();
        first.remove();
        return first;
    }

    public static void onClick(Node n, Consumer<MouseEvent> handler) {
        n.addEventListener("click", e -> handler.accept((MouseEvent) e));
    }

    public static void onMouse(Node n, String type, Consumer<MouseEvent> handler) {
        n.addEventListener(type, e -> handler.accept((MouseEvent) e));
    }

    public static void onKey(Node n, Consumer<KeyboardEvent> handler) {
        n.addEventListener("keydown", e -> handler.accept((KeyboardEvent) e));
    }

    public static void on(Node n, String type, EventListener listener) {
        n.addEventListener(type, listener);
    }

    /** The nearest element at or above the event's target matching {@code selector}, or null. */
    public static Element closest(Event e, String selector) {
        return e.target() instanceof Element el ? el.closest(selector) : e.target() instanceof Node n
                && n.parentElement() != null ? n.parentElement().closest(selector) : null;
    }

    /**
     * Inline style kept as ordered properties, written as one attribute only when it changed: controllers set
     * individual properties every frame without re-parsing styles that did not move. A new one picks up the
     * element's inline style first; keep one per element when setting properties every frame.
     */
    public static final class Style {
        private final Element element;
        private final Map<String, String> props = new LinkedHashMap<>();

        /** Starts from the element's current inline style, so setting one property keeps the others. */
        public Style(Element element) {
            this.element = element;
            String css = element.getAttribute("style");
            if (css == null) return;
            for (String decl : css.split(";")) {
                int colon = decl.indexOf(':');
                if (colon > 0) props.put(decl.substring(0, colon).trim(), decl.substring(colon + 1).trim());
            }
        }

        public Style set(String property, String value) {
            if (value == null) props.remove(property);
            else props.put(property, value);
            return this;
        }

        public Style px(String property, float value) {
            return set(property, Math.round(value * 100) / 100f + "px");
        }

        public void apply() {
            StringBuilder sb = new StringBuilder();
            props.forEach((k, v) -> sb.append(k).append(": ").append(v).append("; "));
            String css = sb.toString().trim();
            if (css.isEmpty()) {
                if (element.hasAttribute("style")) element.removeAttribute("style");
            } else {
                attr(element, "style", css);
            }
        }
    }
}
