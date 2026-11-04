package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

/**
 * Telling the player something without a dialog: a {@link #bar bar} along the bottom of the window (what happened, an
 * optional action, a close button; one at a time) and {@link #toast toasts} that stack in the corner and leave by
 * themselves. Kinds: {@code info}, {@code success}, {@code warn}, {@code error} (the colour and icon).
 *
 * <p>Markup works without code: a button with {@code data-toast="标题|副标题"} (and {@code data-kind}) shows a toast,
 * one with {@code data-notice="文字"} shows the bar. The classes are {@code .o-notice} and {@code .o-toast}
 * ({@code ore.css}); {@code .o-notice.inline} is the same bar inside the flow of a page.
 */
public final class Notice {
    private Notice() {}

    /**
     * Shows the bottom bar ({@code action} and {@code onAction} may be null), replacing one already there, and
     * returns it. It stays until closed or replaced.
     */
    public static Element bar(Document doc, String kind, String text, String action, Runnable onAction) {
        closeBar(doc);
        Element host = host(doc);
        Element bar = Dom.html(doc, "<div class=\"o-notice " + kind(kind) + "\" id=\"o-notice\"><img class=\"o-ico\" src=\"oriel:textures/icons/"
                + icon(kind) + ".png\"><span class=\"t\"></span><div class=\"x\" title=\"关闭\">×</div></div>");
        Dom.text(bar.querySelector(".t"), text);
        if (action != null) {
            Element button = Dom.el(doc, "div", "ore-btn primary", action);
            button.setAttribute("tabindex", "0");
            bar.insertBefore(button, bar.querySelector(".x"));
            Shell.activate(button, () -> {
                if (onAction != null) onAction.run();
            });
        }
        Dom.onClick(bar.querySelector(".x"), e -> bar.remove());
        host.appendChild(bar);
        return bar;
    }

    /** Removes the bottom bar, if any. */
    public static void closeBar(Document doc) {
        Element old = doc.getElementById("o-notice");
        if (old != null) old.remove();
    }

    /** A toast with a title and an optional second line, gone after {@code ms} (or when its × is pressed). */
    public static Element toast(Document doc, String kind, String title, String sub, int ms) {
        Element stack = doc.querySelector(".o-toasts");
        if (stack == null) {
            stack = Dom.el(doc, "div", "o-toasts", null);
            host(doc).appendChild(stack);
        }
        Element t = Dom.html(doc, "<div class=\"o-toast " + kind(kind) + "\" style=\"--life: " + ms + "ms\"><img class=\"o-ico\" src=\"oriel:textures/icons/"
                + icon(kind) + ".png\"><div class=\"b\"><span class=\"h\"></span></div><div class=\"x\">×</div><i class=\"life\"></i></div>");
        Dom.text(t.querySelector(".h"), title);
        if (sub != null && !sub.isEmpty()) t.querySelector(".b").appendChild(Dom.el(doc, "span", "s", sub));
        // Newest at the bottom, nearest the bar; at most four, the oldest leaves first.
        while (stack.children().size() >= 4) stack.children().getFirst().remove();
        stack.appendChild(t);
        Runnable leave = () -> {
            if (!t.isConnected() || t.hasClass("leaving")) return;
            t.addClass("leaving");
            doc.scheduler().setTimeout(t::remove, 200);
        };
        Dom.onClick(t.querySelector(".x"), e -> leave.run());
        doc.scheduler().setTimeout(leave, ms);
        return t;
    }

    /** Wires {@code data-toast} and {@code data-notice} buttons (Shell does it for every page). */
    static void attach(Document doc) {
        for (Element e : doc.querySelectorAll("[data-toast]")) {
            Shell.activate(e, () -> {
                String[] parts = e.getAttribute("data-toast").split("[|]", 2);
                toast(doc, e.getAttribute("data-kind"), parts[0], parts.length > 1 ? parts[1] : null, 3200);
            });
        }
        for (Element e : doc.querySelectorAll("[data-notice]")) {
            Shell.activate(e, () -> bar(doc, e.getAttribute("data-kind"), e.getAttribute("data-notice"), e.getAttribute("data-notice-action"), null));
        }
    }

    private static Element host(Document doc) {
        Element host = doc.querySelector(".o-window");
        if (host == null) host = doc.querySelector(".o-app");
        return host != null ? host : doc.body();
    }

    private static String kind(String kind) {
        return kind == null || kind.isEmpty() ? "info" : kind;
    }

    private static String icon(String kind) {
        return switch (kind(kind)) {
            case "success" -> "check";
            case "warn" -> "warning";
            case "error" -> "ban";
            default -> "info";
        };
    }
}
