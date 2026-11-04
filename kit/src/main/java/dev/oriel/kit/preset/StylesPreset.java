package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

import java.util.ArrayList;
import java.util.List;

/**
 * The style showcase: eight full-screen looks (RPG, military, dungeon, arcane, fantasy, Terraria, sci-fi, xianxia),
 * each a page of plain HTML and CSS under {@code ui/styles/}. The index lists them by their sections' {@code data-group}
 * (a new look is one more section) and shows the chosen one's scene and spec; ↑/↓ change it, Enter opens it, and
 * coming back shows the look last opened. Escape goes back: from a look to the index, from the index to the gallery.
 */
public final class StylesPreset implements PageControllers.Controller {
    /** The looks: their pages are {@code ui/styles/<name>.html}. */
    public static final List<String> PAGES = List.of("rpg", "arcane", "military", "scifi", "xianxia", "fantasy", "terraria", "dungeon");
    /** The look the index shows: the one last picked, kept across visits. */
    private static volatile String last;

    private final boolean index;
    private final List<Element> looks = new ArrayList<>(), items = new ArrayList<>();
    private int current = -1;
    private Document doc;

    /** {@code index}: the index page, rather than one of the looks. */
    public StylesPreset(boolean index) {
        this.index = index;
    }

    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        if (index) buildList();
        Dom.onKey(doc, e -> {
            if (e.defaultPrevented()) return;
            switch (e.key) {
                case "Escape" -> {
                    e.preventDefault();
                    if (index) Shell.back(doc);
                    else Shell.go(doc, OrielKit.STYLES, "back");
                }
                case "ArrowDown", "ArrowUp" -> {
                    if (!index || looks.isEmpty()) return;
                    e.preventDefault();
                    show(Math.floorMod(current + (e.key.equals("ArrowDown") ? 1 : -1), looks.size()));
                }
                case "Enter" -> {
                    if (!index || current < 0) return;
                    e.preventDefault();
                    open(current);
                }
                default -> { }
            }
        });
    }

    /** The list, grouped as the sections are, and the look to show first. */
    private void buildList() {
        Element list = Dom.byId(doc, "list");
        String group = null;
        int first = 0;
        for (Element look : doc.querySelectorAll(".look")) {
            String g = look.getAttribute("data-group");
            if (g != null && !g.equals(group)) {
                list.appendChild(Dom.el(doc, "div", "grp", g));
                group = g;
            }
            int i = looks.size();
            looks.add(look);
            Element item = Dom.el(doc, "div", "item m-press", null);
            item.setAttribute("tabindex", "0");
            Element icon = Dom.el(doc, "img", "o-ico", null);
            icon.setAttribute("src", "../textures/icons/" + look.getAttribute("data-icon") + ".png");
            item.appendChild(icon);
            item.appendChild(Dom.el(doc, "span", null, look.getAttribute("data-title")));
            Element dot = Dom.el(doc, "i", null, null);
            dot.setAttribute("style", "--sw: " + look.getAttribute("data-swatch"));
            item.appendChild(dot);
            list.appendChild(item);
            items.add(item);
            Shell.activate(item, () -> show(i));
            if (look.getAttribute("data-look").equals(last)) first = i;
        }
        Element count = doc.getElementById("count");
        if (count != null) Dom.text(count, looks.size() + " 种风格");
        if (!looks.isEmpty()) show(first);
    }

    /** Shows look {@code i}: its section slides in, its item lights up, the title bar names it. */
    private void show(int i) {
        if (i == current) return;
        if (current >= 0) {
            looks.get(current).removeClass("on");
            items.get(current).removeClass("on");
        }
        current = i;
        Element look = looks.get(i);
        look.addClass("on");
        items.get(i).addClass("on");
        last = look.getAttribute("data-look");
        Element crumb = doc.getElementById("crumb");
        if (crumb != null) Dom.text(crumb, "/ " + look.getAttribute("data-title"));
        Element pane = doc.getElementById("pane");
        if (pane != null) pane.scrollTo(0, 0);
    }

    private void open(int i) {
        last = looks.get(i).getAttribute("data-look");
        Shell.go(doc, OrielKit.page("styles/" + last), "forward");
    }
}
