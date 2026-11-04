package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default behaviour of the kit's controls written straight into markup, so a page (or a demo) works with no
 * code: tabs ({@code .ore-tabs > .ore-tab}) and segments ({@code .o-seg > *}) select the one clicked, a toggle
 * ({@code .ore-toggle}) and a hot chip ({@code .o-chip.hot}) flip {@code on}, and a slider ({@code .ore-slider}) can be
 * dragged, starting from its {@code .fill} width, and a select ({@code .ore-select} with {@code .value} and a {@code .menu}
 * of {@code .option}s) opens, picks (the arrow keys and Enter too) and closes (Escape, or a press elsewhere). A {@code .m-indicator} inside a tab or segment group glides under
 * the selected one.
 *
 * <p>Toggles, chips and sliders with an {@code id} are left to the page's controller (it wires them itself, from its
 * own state), as are segments whose items open pages or pick the look ({@code data-go}, {@code data-action},
 * {@code data-style-pick}, {@code data-theme-pick}). Selecting in a tab or segment group is idempotent, so a controller
 * that also listens to the same items sets the same class.
 */
public final class Widgets {
    private static final Pattern WIDTH = Pattern.compile("width\\s*:\\s*([0-9.]+)%");

    private Widgets() {}

    public static void attach(Document doc) {
        for (Element tabs : doc.querySelectorAll(".ore-tabs")) group(tabs, "ore-tab", "selected");
        for (Element seg : doc.querySelectorAll(".o-seg")) group(seg, null, "on");
        for (Element t : doc.querySelectorAll(".ore-toggle")) {
            if (!t.hasAttribute("id")) Shell.activate(t, () -> t.toggleClass("on", !t.hasClass("on")));
        }
        for (Element c : doc.querySelectorAll(".o-chip.hot")) {
            if (!c.hasAttribute("id")) Shell.activate(c, () -> c.toggleClass("on", !c.hasClass("on")));
        }
        for (Element sel : doc.querySelectorAll(".ore-select")) {
            if (!sel.hasAttribute("id") && sel.querySelector(".value") != null && sel.querySelector(".menu") != null) select(doc, sel);
        }
        for (Element s : doc.querySelectorAll(".ore-slider")) {
            if (s.hasAttribute("id") || s.querySelector(".fill") == null || s.querySelector(".thumb") == null) continue;
            Slider slider = new Slider(doc, s, 0);
            String style = s.querySelector(".fill").getAttribute("style");
            Matcher m = style == null ? null : WIDTH.matcher(style);
            slider.set(m != null && m.find() ? Double.parseDouble(m.group(1)) / 100 : 0.5);
        }
    }

    private static void select(Document doc, Element sel) {
        Element value = sel.querySelector(".value");
        List<Element> options = sel.querySelector(".menu").children();
        if (!sel.hasAttribute("tabindex")) sel.setAttribute("tabindex", "0");
        Runnable close = () -> sel.removeClass("open");
        java.util.function.Consumer<Element> choose = o -> {
            for (Element c : options) c.toggleClass("chosen", c == o);
            Dom.text(value, o.textContent());
            close.run();
        };
        Dom.onMouse(value, "mousedown", e -> {
            boolean open = !sel.hasClass("open");
            sel.toggleClass("open", open);
            for (Element c : options) c.toggleClass("active", c.hasClass("chosen"));
        });
        for (Element o : options) {
            Dom.onMouse(o, "mousedown", e -> {
                e.stopPropagation();
                choose.accept(o);
            });
        }
        Dom.onMouse(doc, "mousedown", e -> {
            if (sel.hasClass("open") && Dom.closest(e, ".ore-select") != sel) close.run();
        });
        Dom.onKey(sel, e -> {
            int at = 0;
            for (int i = 0; i < options.size(); i++) if (options.get(i).hasClass("active")) at = i;
            switch (e.key) {
                case "Enter", " " -> {
                    e.preventDefault();
                    if (sel.hasClass("open")) choose.accept(options.get(at));
                    else {
                        sel.addClass("open");
                        for (Element c : options) c.toggleClass("active", c.hasClass("chosen"));
                    }
                }
                case "ArrowDown", "ArrowUp" -> {
                    if (!sel.hasClass("open")) return;
                    e.preventDefault();
                    int next = Math.floorMod(at + (e.key.equals("ArrowDown") ? 1 : -1), options.size());
                    for (int i = 0; i < options.size(); i++) options.get(i).toggleClass("active", i == next);
                }
                case "Escape" -> {
                    if (sel.hasClass("open")) {
                        e.preventDefault();
                        close.run();
                    }
                }
                default -> { }
            }
        });
    }

    /** One of a group selected at a time (items with {@code cls}, or all but the indicator). */
    private static void group(Element box, String cls, String selected) {
        List<Element> items = box.children().stream()
                .filter(c -> !c.hasClass("m-indicator") && (cls == null || c.hasClass(cls))).toList();
        if (items.isEmpty() || items.stream().anyMatch(Widgets::claimed)) return;
        Element indicator = box.children().stream().filter(c -> c.hasClass("m-indicator")).findFirst().orElse(null);
        for (Element item : items) {
            Shell.activate(item, () -> {
                for (Element c : items) c.toggleClass(selected, c == item);
                if (indicator != null) Motion.indicator(indicator, item);
            });
        }
        Element on = items.stream().filter(c -> c.hasClass(selected)).findFirst().orElse(null);
        if (indicator != null && on != null) {
            box.ownerDocument().scheduler().requestAnimationFrame(t -> Motion.indicator(indicator, on));
        }
    }

    private static boolean claimed(Element e) {
        return e.hasAttribute("data-go") || e.hasAttribute("data-action") || e.hasAttribute("data-style-pick")
                || e.hasAttribute("data-theme-pick");
    }
}
