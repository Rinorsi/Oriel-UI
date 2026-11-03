package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.kit.fx.Particles;

/**
 * What every kit page shares, wired from markup so a page needs no code for it:
 * <ul>
 *   <li>the theme: {@code data-theme} on {@code <html>} ({@link #theme()}, kept across pages);</li>
 *   <li>{@code data-go="forge"}: opens that kit page (Enter / Space too);</li>
 *   <li>{@code data-action="close" | "back" | "theme"}: closes the screen, returns to the gallery, flips the theme;</li>
 *   <li>{@code canvas[data-fx]}: particles ({@link Particles}).</li>
 * </ul>
 * Controllers call {@link #setup} first and keep the returned particles for bursts.
 */
public final class Shell {
    private static volatile String theme = "dark";

    private Shell() {}

    /** The current theme, {@code dark} or {@code light}. */
    public static String theme() {
        return theme;
    }

    /** Sets the theme for this page and the pages opened after it. */
    public static void theme(Document doc, String name) {
        theme = name.equals("light") ? "light" : "dark";
        Element root = doc.documentElement();
        if (root != null) root.setAttribute("data-theme", theme);
        for (Element e : doc.querySelectorAll("[data-action=theme]")) e.toggleClass("on", theme.equals("light"));
    }

    /** Applies the theme, wires the shared attributes and starts the page's particles. */
    public static Particles setup(Document doc) {
        theme(doc, theme);
        for (Element e : doc.querySelectorAll("[data-go]")) {
            String page = e.getAttribute("data-go");
            activate(e, () -> doc.navigate(OrielKit.page(page)));
        }
        for (Element e : doc.querySelectorAll("[data-action]")) {
            switch (e.getAttribute("data-action")) {
                case "close" -> activate(e, () -> doc.host().close());
                case "back" -> activate(e, () -> doc.navigate(OrielKit.GALLERY));
                case "theme" -> activate(e, () -> theme(doc, theme.equals("light") ? "dark" : "light"));
                default -> { }
            }
        }
        return Particles.attach(doc);
    }

    /** Runs {@code action} on a click, and on Enter or Space while focused. */
    public static void activate(Element e, Runnable action) {
        Dom.onClick(e, ev -> action.run());
        Dom.onKey(e, ev -> {
            if (ev.key.equals("Enter") || ev.key.equals(" ")) {
                ev.preventDefault();
                action.run();
            }
        });
    }
}
