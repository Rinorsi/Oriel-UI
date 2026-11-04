package dev.oriel.kit;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default behaviour of the kit's controls written straight into markup, so a page (or a demo) works with no
 * code: tabs ({@code .ore-tabs > .ore-tab}) and segments ({@code .o-seg > *}) select the one clicked, a toggle
 * ({@code .ore-toggle}); an item with {@code data-show="#pane"} also shows that pane and hides its siblings' panes) and a hot chip ({@code .o-chip.hot}) flip {@code on}, and a slider ({@code .ore-slider}) can be
 * dragged, starting from its {@code .fill} width, and a select ({@code .ore-select} with {@code .value} and a {@code .menu}
 * of {@code .option}s) opens, picks (the arrow keys and Enter too) and closes (Escape, or a press elsewhere), and a
 * menu bar ({@code .o-menubar}) opens its menus, {@code data-context} opens a right-click menu, a drawer's
 * {@code .o-drawer-handle} folds the {@code .o-drawer}, {@code data-pop} opens a popover and an {@code .o-mcfield} gets
 * its Minecraft formatting bar ({@link McField}). A {@code .m-indicator} inside a tab or segment group glides under
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
        for (Element bar : doc.querySelectorAll(".o-menubar")) menubar(doc, bar);
        if (!doc.querySelectorAll(".o-context").isEmpty()) contextMenus(doc);
        for (Element drawer : doc.querySelectorAll(".o-drawer, .o-sheet")) {
            Element handle = drawer.querySelector(".o-drawer-handle");
            if (handle != null) Shell.activate(handle, () -> drawer.toggleClass("shut", !drawer.hasClass("shut")));
        }
        if (!doc.querySelectorAll("[data-pop]").isEmpty()) popovers(doc);
        List<Element> fits = doc.querySelectorAll(".o-fit");
        if (!fits.isEmpty()) {
            // Every frame (sizes change with the window): a child whose box pokes out of its box's content area hides.
            doc.scheduler().requestAnimationFrame(new java.util.function.DoubleConsumer() {
                @Override
                public void accept(double t) {
                    for (Element box : fits) {
                        if (!box.isConnected()) continue;
                        float[] r = box.getBoundingClientRect();
                        float right = r[0] + box.clientWidth() + 0.5f, bottom = r[1] + box.clientHeight() - 2f;
                        for (Element c : box.children()) {
                            float[] cr = c.getBoundingClientRect();
                            c.toggleClass("o-out", cr[1] + cr[3] > bottom || cr[0] + cr[2] > right);
                        }
                    }
                    doc.scheduler().requestAnimationFrame(this);
                }
            });
        }
        McField.attach(doc);
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

    /**
     * A menu bar: a title opens its list; while one is open, pointing at another title opens that one; choosing an
     * item, pressing elsewhere or Escape closes it. The items' own listeners (the page's) do the work.
     */
    private static void menubar(Document doc, Element bar) {
        List<Element> menus = bar.children().stream().filter(c -> c.hasClass("o-menu")).toList();
        Element[] open = {null};
        java.util.function.Consumer<Element> show = m -> {
            for (Element x : menus) x.toggleClass("open", x == m);
            open[0] = m;
        };
        for (Element m : menus) {
            Element title = m.querySelector(".title");
            if (title == null) continue;
            Dom.onMouse(title, "mousedown", e -> show.accept(open[0] == m ? null : m));
            Dom.onMouse(title, "mouseenter", e -> {
                if (open[0] != null && open[0] != m) show.accept(m);
            });
            for (Element item : m.querySelectorAll(".item")) Dom.onClick(item, e -> show.accept(null));
        }
        Dom.onMouse(doc, "mousedown", e -> {
            if (open[0] != null && Dom.closest(e, ".o-menubar") != bar) show.accept(null);
        });
        Dom.onKey(doc, e -> {
            if (open[0] != null && e.key.equals("Escape")) {
                e.preventDefault();
                show.accept(null);
            }
        });
    }

    /**
     * Right-click menus: a right press on (or in) an element with {@code data-context="id"} opens the
     * {@code .o-context} with that id at the pointer, kept inside its parent; choosing an item, pressing elsewhere or
     * Escape closes it. Items are {@code .item}s like a menu bar's; the page's own listeners do the work.
     */
    private static void contextMenus(Document doc) {
        Element[] open = {null};
        Runnable close = () -> {
            if (open[0] != null) open[0].removeClass("open");
            open[0] = null;
        };
        for (Element menu : doc.querySelectorAll(".o-context")) {
            for (Element item : menu.querySelectorAll(".item")) Dom.onClick(item, e -> close.run());
        }
        Dom.onMouse(doc, "mousedown", e -> {
            if (open[0] != null && Dom.closest(e, ".o-context") != open[0]) close.run();
            if (e.button != 2) return;
            Element host = Dom.closest(e, "[data-context]");
            Element menu = host == null ? null : doc.getElementById(host.getAttribute("data-context"));
            if (menu == null) return;
            e.preventDefault();
            float[] box = menu.parentElement().getBoundingClientRect();
            menu.addClass("open");
            open[0] = menu;
            float[] size = menu.getBoundingClientRect();
            float x = Math.min(e.clientX - box[0], box[2] - size[2] - 2), y = Math.min(e.clientY - box[1], box[3] - size[3] - 2);
            new Dom.Style(menu).px("left", Math.max(2, x)).px("top", Math.max(2, y)).apply();
        });
        Dom.onKey(doc, e -> {
            if (open[0] != null && e.key.equals("Escape")) {
                e.preventDefault();
                close.run();
            }
        });
    }

    /**
     * Popovers: an element with {@code data-pop="id"} toggles the {@code .o-pop} with that id, placed under it (or
     * above, when there is no room below) inside the popover's parent; a press outside both, or Escape, closes it.
     */
    private static void popovers(Document doc) {
        Element[] open = {null, null};
        Runnable close = () -> {
            if (open[0] != null) open[0].removeClass("open");
            open[0] = open[1] = null;
        };
        for (Element trigger : doc.querySelectorAll("[data-pop]")) {
            Element pop = doc.getElementById(trigger.getAttribute("data-pop"));
            if (pop == null) continue;
            Shell.activate(trigger, () -> {
                boolean again = open[0] == pop;
                close.run();
                if (again) return;
                pop.addClass("open");
                open[0] = pop;
                open[1] = trigger;
                float[] box = pop.parentElement().getBoundingClientRect(), t = trigger.getBoundingClientRect(), size = pop.getBoundingClientRect();
                float x = Math.max(2, Math.min(t[0] - box[0], box[2] - size[2] - 2));
                float below = t[1] + t[3] - box[1] + 3, above = t[1] - box[1] - size[3] - 3;
                float y = below + size[3] <= box[3] || above < 0 ? below : above;
                new Dom.Style(pop).px("left", x).px("top", y).apply();
            });
        }
        Dom.onMouse(doc, "mousedown", e -> {
            if (open[0] == null) return;
            Element in = Dom.closest(e, ".o-pop"), on = Dom.closest(e, "[data-pop]");
            if (in != open[0] && on != open[1]) close.run();
        });
        Dom.onKey(doc, e -> {
            if (open[0] != null && e.key.equals("Escape")) {
                e.preventDefault();
                close.run();
            }
        });
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
            o.click(); // the page's own listener on the option (a data-font-pick, say) acts on the choice
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
        Document doc = box.ownerDocument();
        for (Element item : items) {
            Shell.activate(item, () -> {
                for (Element c : items) c.toggleClass(selected, c == item);
                if (indicator != null) Motion.indicator(indicator, item);
                showPanes(doc, items, item);
            });
        }
        items.stream().filter(c -> c.hasClass(selected)).findFirst().ifPresent(on -> showPanes(doc, items, on));
        Element on = items.stream().filter(c -> c.hasClass(selected)).findFirst().orElse(null);
        if (indicator != null && on != null) {
            box.ownerDocument().scheduler().requestAnimationFrame(t -> Motion.indicator(indicator, on));
        }
    }

    /** Items with {@code data-show="#id"} switch panes: the chosen item's shows, the others' get {@code o-hidden}. */
    private static void showPanes(Document doc, List<Element> items, Element chosen) {
        for (Element c : items) {
            String target = c.getAttribute("data-show");
            Element pane = target == null ? null : doc.querySelector(target);
            if (pane != null) pane.toggleClass("o-hidden", c != chosen);
        }
    }

    private static boolean claimed(Element e) {
        return e.hasAttribute("data-go") || e.hasAttribute("data-action") || e.hasAttribute("data-style-pick")
                || e.hasAttribute("data-theme-pick") || e.hasAttribute("data-icons-pick") || e.hasAttribute("data-font-pick");
    }
}
