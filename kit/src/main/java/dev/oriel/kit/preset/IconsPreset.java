package dev.oriel.kit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Shell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The icon library page: the categories, a filterable grid of every icon (built a batch per frame, so a long list
 * opens at once), and the chosen icon at three sizes, in the theme's colours, with its name, tags and markup.
 * The icons and their names come from {@code icons.json} next to the page.
 */
public final class IconsPreset implements PageControllers.Controller {
    /** One icon: its file name, its Chinese name, its category and tags. */
    record Icon(String id, String name, String cat, List<String> tags) {
        String shortName() {
            int slash = name.indexOf(" / ");
            return slash < 0 ? name : name.substring(0, slash);
        }

        boolean matches(String q) {
            if (q.isEmpty()) return true;
            if (id.contains(q) || name.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (String t : tags) if (t.contains(q)) return true;
            return false;
        }
    }

    static final int BATCH = 40;
    static final Map<String, String> TINTS = Map.of("text", "var(--o-text)", "green", "#86d562", "blue", "#55c8ff", "purple", "#c46aff",
            "gold", "#ffb52b", "red", "#ff5a6a");
    static final List<String> TINT_ORDER = List.of("text", "green", "blue", "purple", "gold", "red");

    final List<Icon> icons = new ArrayList<>();
    final List<String[]> categories = new ArrayList<>();
    String category = "all", query = "";
    Icon chosen;
    private final List<Icon> shown = new ArrayList<>();
    private int generation;
    private Document doc;

    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        load(doc.host().loadText(doc.resolveUrl("icons.json")));
        Element cats = Dom.byId(doc, "cats");
        cats.appendChild(category("all", "全部图标", icons.size()));
        for (String[] c : categories) {
            long n = icons.stream().filter(i -> i.cat().equals(c[0])).count();
            cats.appendChild(category(c[0], c[1], (int) n));
        }
        Element q = Dom.byId(doc, "q");
        q.addEventListener("input", e -> {
            query = q.value().trim().toLowerCase(Locale.ROOT);
            render();
        });
        Element tints = Dom.byId(doc, "tints");
        for (String t : TINT_ORDER) {
            Element sw = Dom.el(doc, "span", t.equals("text") ? "on" : null, null);
            sw.setAttribute("style", "--c: " + TINTS.get(t));
            sw.setAttribute("data-tint", t);
            Shell.activate(sw, () -> {
                for (Element s : tints.children()) s.toggleClass("on", s == sw);
                new Dom.Style(Dom.byId(doc, "stage")).set("--tint", TINTS.get(t)).apply();
            });
            tints.appendChild(sw);
        }
        Dom.onKey(doc, this::key);
        Dom.text(Dom.byId(doc, "total"), icons.size() + " 个图标 · 16×16 像素 · 用 -mc-tint 着色");
        render();
        if (!icons.isEmpty()) choose(icons.getFirst());
    }

    private void load(String json) {
        if (json == null) return;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (var c : root.getAsJsonArray("categories")) {
            JsonObject o = c.getAsJsonObject();
            categories.add(new String[] {o.get("id").getAsString(), o.get("zh").getAsString()});
        }
        for (var e : root.getAsJsonObject("icons").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<String> tags = new ArrayList<>();
            for (var t : o.getAsJsonArray("tags")) tags.add(t.getAsString());
            icons.add(new Icon(e.getKey(), o.get("zh").getAsString(), o.get("cat").getAsString(), tags));
        }
    }

    private Element category(String id, String name, int n) {
        Element row = Dom.el(doc, "div", "cat" + (id.equals(category) ? " on" : ""), null);
        row.setAttribute("tabindex", "0");
        row.setAttribute("data-cat", id);
        // The category's first icon stands for it.
        String face = id.equals("all") ? "star" : icons.stream().filter(i -> i.cat().equals(id)).map(Icon::id).findFirst().orElse("grid");
        Element img = Dom.el(doc, "img", "o-ico inherit", null);
        img.setAttribute("src", "../textures/icons/" + face + ".png");
        row.appendChild(img);
        row.appendChild(Dom.el(doc, "span", null, name));
        row.appendChild(Dom.el(doc, "span", "n", Integer.toString(n)));
        Shell.activate(row, () -> {
            category = id;
            for (Element c : Dom.byId(doc, "cats").children()) c.toggleClass("on", c == row);
            Dom.text(Dom.byId(doc, "crumb"), "/ " + name);
            render();
        });
        return row;
    }

    /** Refills the grid with the icons that match, a batch per frame (a newer render stops an older one). */
    void render() {
        Element grid = Dom.byId(doc, "grid");
        grid.removeAllChildren();
        shown.clear();
        for (Icon i : icons) if ((category.equals("all") || i.cat().equals(category)) && i.matches(query)) shown.add(i);
        Dom.text(Dom.byId(doc, "count"), shown.size() + " 个");
        if (shown.isEmpty()) {
            grid.appendChild(Dom.el(doc, "div", "empty", "没有找到「" + query + "」"));
            return;
        }
        int gen = ++generation;
        int[] next = {0};
        Runnable batch = new Runnable() {
            @Override
            public void run() {
                if (gen != generation) return;
                int end = Math.min(shown.size(), next[0] + BATCH);
                for (; next[0] < end; next[0]++) grid.appendChild(tile(shown.get(next[0])));
                if (next[0] < shown.size()) doc.scheduler().requestAnimationFrame(t -> run());
            }
        };
        batch.run();
    }

    private Element tile(Icon icon) {
        Element t = Dom.el(doc, "div", "tile" + (icon == chosen ? " on" : ""), null);
        t.setAttribute("tabindex", "0");
        t.setAttribute("data-id", icon.id());
        t.setAttribute("title", icon.name() + " · " + icon.id());
        Element img = Dom.el(doc, "img", "o-ico inherit", null);
        img.setAttribute("src", "../textures/icons/" + icon.id() + ".png");
        t.appendChild(img);
        t.appendChild(Dom.el(doc, "span", "nm", icon.shortName()));
        Shell.activate(t, () -> choose(icon));
        return t;
    }

    void choose(Icon icon) {
        chosen = icon;
        for (Element t : Dom.byId(doc, "grid").children()) t.toggleClass("on", icon.id().equals(t.getAttribute("data-id")));
        String src = "../textures/icons/" + icon.id() + ".png";
        for (String id : List.of("d-big", "d-mid", "d-small")) Dom.byId(doc, id).setAttribute("src", src);
        Dom.text(Dom.byId(doc, "d-name"), icon.name());
        Dom.text(Dom.byId(doc, "d-id"), icon.id() + " · " + categoryName(icon.cat()));
        Element tags = Dom.byId(doc, "d-tags");
        tags.removeAllChildren();
        for (String tag : icon.tags()) tags.appendChild(Dom.el(doc, "span", "o-chip", tag));
        Dom.text(Dom.byId(doc, "d-code"), "<img class=\"o-ico\" src=\"../textures/icons/" + icon.id() + ".png\">");
    }

    private String categoryName(String id) {
        for (String[] c : categories) if (c[0].equals(id)) return c[1];
        return id;
    }

    private void key(KeyboardEvent e) {
        Element focus = doc.focusedElement();
        if (focus != null && focus.isTextControl() || shown.isEmpty()) return;
        int at = chosen == null ? -1 : shown.indexOf(chosen);
        if (e.key.equals("ArrowRight")) choose(shown.get(Math.min(shown.size() - 1, at + 1)));
        else if (e.key.equals("ArrowLeft")) choose(shown.get(Math.max(0, at - 1)));
        else return;
        e.preventDefault();
    }
}
