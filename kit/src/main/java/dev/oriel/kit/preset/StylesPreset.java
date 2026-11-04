package dev.oriel.kit.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.event.KeyboardEvent;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.Shell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The template centre and its templates: full-screen looks of plain HTML and CSS under {@code ui/styles/}. The
 * centre ({@link OrielKit#STYLES}) is built from its {@code .tpl} entries (a new template is one more entry): a
 * sidebar of views (all, official, community, favourites, saved, recent, one category, the contribution guide), a
 * search and a sort over a grid of covers, and a template's details in a drawer, with a full-screen preview,
 * one-click save and a favourite. The game keeps the player's library and sends it as {@value #LIBRARY} data
 * (TemplateLibrary in the mod); without a game (the previewer) the page keeps it for itself. Escape closes the
 * details, then goes back to the gallery; on a template it goes back to the centre, with that template's details
 * open again.
 */
public final class StylesPreset implements PageControllers.Controller {
    /** The templates: their pages are {@code ui/styles/<id>.html}, their covers {@code textures/ui/templates/<id>.png}. */
    public static final List<String> PAGES = List.of("rpg", "arcane", "military", "scifi", "xianxia", "fantasy", "terraria", "dungeon",
            "mcdungeons");
    /** The data key the game sends the player's library on. */
    public static final String LIBRARY = "library";
    /** The template whose details were open when one was previewed: shown again on coming back. */
    private static volatile String returnTo;

    private record Template(String id, String title, String author, String source, String group, String ver, String date,
                            List<String> tags, String desc, Element spec, Element card, int order) {}

    private final boolean index;
    private Document doc;
    private final List<Template> templates = new ArrayList<>();
    private final Set<String> favorites = new LinkedHashSet<>(), saved = new LinkedHashSet<>();
    private final List<String> recent = new ArrayList<>();
    private String namespace = "oriel_mine", folder = "config/oriel/my-pages/";
    private String view = "all", sort = "pick", query = "";
    private Template open;
    private Element grid, drawer, veil, specHome;

    /** {@code index}: the centre, rather than one of the templates. */
    public StylesPreset(boolean index) {
        this.index = index;
    }

    @Override
    public void attach(Document document) {
        doc = document;
        Shell.setup(doc);
        if (index) buildCentre();
        Dom.onKey(doc, this::key);
    }

    private void key(KeyboardEvent e) {
        if (e.defaultPrevented()) return;
        Element focus = doc.focusedElement();
        if (focus != null && focus.isTextControl() && !e.key.equals("Escape")) return;
        switch (e.key) {
            case "Escape" -> {
                e.preventDefault();
                if (!index) Shell.go(doc, OrielKit.STYLES, "back");
                else if (open != null) closeDetails();
                else Shell.back(doc);
            }
            case "Enter" -> {
                if (!index || open == null) return;
                e.preventDefault();
                preview(open);
            }
            case "s", "S" -> {
                if (!index || open == null) return;
                e.preventDefault();
                save(open);
            }
            default -> { }
        }
    }

    // ---- The centre ----

    private void buildCentre() {
        grid = Dom.byId(doc, "grid");
        drawer = Dom.byId(doc, "drawer");
        veil = Dom.byId(doc, "veil");
        specHome = Dom.byId(doc, "tpls");
        int order = 0;
        for (Element e : doc.querySelectorAll(".tpl")) {
            String id = e.getAttribute("data-id");
            List<String> tags = List.of(attr(e, "data-tags").split(","));
            Template t = new Template(id, attr(e, "data-title"), attr(e, "data-author"), attr(e, "data-source"), attr(e, "data-group"),
                    attr(e, "data-ver"), attr(e, "data-date"), tags, attr(e, "data-desc"), e, null, order++);
            templates.add(new Template(t.id, t.title, t.author, t.source, t.group, t.ver, t.date, t.tags, t.desc, t.spec, card(t), t.order));
        }
        for (Element item : doc.querySelectorAll("#side .item")) {
            Shell.activate(item, () -> {
                view = item.getAttribute("data-view");
                for (Element i : doc.querySelectorAll("#side .item")) i.toggleClass("on", i == item);
                render();
            });
        }
        for (Element s : doc.querySelectorAll("#sort [data-sort]")) {
            Shell.activate(s, () -> {
                sort = s.getAttribute("data-sort");
                for (Element o : doc.querySelectorAll("#sort [data-sort]")) o.toggleClass("on", o == s);
                render();
            });
        }
        Element q = Dom.byId(doc, "q");
        Dom.on(q, "input", ev -> {
            query = q.value().trim().toLowerCase(Locale.ROOT);
            render();
        });
        Shell.activate(Dom.byId(doc, "d-close"), this::closeDetails);
        Dom.onClick(veil, ev -> closeDetails());
        Shell.activate(Dom.byId(doc, "d-open"), () -> preview(open));
        Shell.activate(Dom.byId(doc, "d-save"), () -> save(open));
        Shell.activate(Dom.byId(doc, "d-fav"), () -> favorite(open, !favorites.contains(open.id)));
        Shell.activate(Dom.byId(doc, "open-folder"), () -> send("folder", "", false));
        if (doc.initialData() != null) library(JsonParser.parseString(doc.initialData()));
        doc.addDataListener((key, json) -> {
            if (json == null) return;
            JsonElement value = JsonParser.parseString(json);
            if (LIBRARY.equals(key)) library(value);
            else if (key == null && value.isJsonObject() && value.getAsJsonObject().has(LIBRARY)) library(value.getAsJsonObject().get(LIBRARY));
        });
        render();
        String back = returnTo;
        returnTo = null;
        if (back != null) for (Template t : templates) if (t.id.equals(back)) openDetails(t);
    }

    /** A template's card: its cover, badges, favourite, title, author and tags. */
    private Element card(Template t) {
        Element card = Dom.html(doc, "<div class=\"card o-tile m-spot\" tabindex=\"0\"><div class=\"cover\"><img class=\"shot\">"
                + "<i class=\"sheen\"></i><span class=\"badge\"></span><div class=\"fav\" title=\"收藏\"><img class=\"o-ico\" "
                + "src=\"../textures/icons/heart.png\"></div><span class=\"saved-mark\"><img class=\"o-ico\" src=\"../textures/icons/check.png\">已保存</span>"
                + "</div><div class=\"meta\"><b></b><div class=\"by\"><img src=\"../textures/ui/emblem.png\"><span></span><em></em></div>"
                + "<div class=\"tags\"></div></div></div>");
        Dom.query(card, "img.shot").setAttribute("src", "../textures/ui/templates/" + t.id + ".png");
        Element badge = Dom.query(card, ".badge");
        boolean official = t.source.equals("official");
        Dom.text(badge, official ? "官方" : "社区");
        badge.toggleClass("official", official);
        Dom.text(Dom.query(card, ".meta b"), t.title);
        Dom.text(Dom.query(card, ".by span"), t.author);
        Dom.text(Dom.query(card, ".by em"), t.ver);
        Element tags = Dom.query(card, ".tags");
        for (String tag : t.tags) tags.appendChild(Dom.el(doc, "span", null, tag));
        Element fav = Dom.query(card, ".fav");
        Dom.onClick(fav, ev -> {
            ev.stopPropagation();
            favorite(find(t.id), !favorites.contains(t.id));
        });
        Shell.activate(card, () -> openDetails(find(t.id)));
        grid.appendChild(card);
        return card;
    }

    /** Shows the cards the view, the search and the sort pick, and the empty state when none are. */
    private void render() {
        boolean guide = view.equals("guide"), community = view.equals("community");
        List<Template> shown = new ArrayList<>();
        for (Template t : templates) if (!guide && inView(t) && matches(t)) shown.add(t);
        switch (sort) {
            case "new" -> shown.sort(Comparator.comparing(Template::date).reversed().thenComparing(Template::order));
            case "name" -> shown.sort(Comparator.comparing(Template::title));
            default -> {
                if (view.equals("recent")) shown.sort(Comparator.comparingInt(t -> recent.indexOf(t.id)));
            }
        }
        for (Template t : templates) t.card.remove();
        for (Template t : shown) {
            grid.appendChild(t.card);
            t.card.toggleClass("faved", favorites.contains(t.id));
            Dom.query(t.card, ".fav").toggleClass("on", favorites.contains(t.id));
            Dom.query(t.card, ".saved-mark").setAttribute("style", saved.contains(t.id) ? "" : "display: none");
        }
        boolean mine = view.equals("fav") || view.equals("saved") || view.equals("recent");
        show("empty-community", community && shown.isEmpty() && query.isEmpty());
        show("empty-mine", mine && shown.isEmpty() && query.isEmpty());
        show("empty-search", !guide && shown.isEmpty() && !query.isEmpty());
        show("guide", guide);
        Dom.text(Dom.byId(doc, "empty-mine-title"), switch (view) {
            case "fav" -> "还没有收藏";
            case "saved" -> "还没有保存过模板";
            default -> "还没有浏览过模板";
        });
        if (guide) Dom.text(Dom.byId(doc, "guide-folder"), folder);
        Dom.text(Dom.byId(doc, "view-title"), title(view));
        Dom.text(Dom.byId(doc, "view-sub"), subtitle(view));
        Dom.text(Dom.byId(doc, "count"), guide ? "" : shown.size() + " 个模板");
        Dom.text(Dom.byId(doc, "crumb"), "/ 模板中心 / " + title(view));
        Dom.text(Dom.byId(doc, "foot-count"), templates.size() + " 个官方模板");
        counts();
    }

    private void counts() {
        for (Element item : doc.querySelectorAll("#side .item")) {
            Element n = item.querySelector(".n");
            if (n == null) continue;
            String v = item.getAttribute("data-view");
            int count = 0;
            for (Template t : templates) if (inView(t, v)) count++;
            Dom.text(n, count == 0 && !v.equals("all") ? "" : Integer.toString(count));
        }
    }

    private boolean inView(Template t) {
        return inView(t, view);
    }

    private boolean inView(Template t, String v) {
        if (v.startsWith("group:")) return t.group.equals(v.substring(6));
        return switch (v) {
            case "official" -> t.source.equals("official");
            case "community" -> t.source.equals("community");
            case "fav" -> favorites.contains(t.id);
            case "saved" -> saved.contains(t.id);
            case "recent" -> recent.contains(t.id);
            case "guide" -> false;
            default -> true;
        };
    }

    private boolean matches(Template t) {
        if (query.isEmpty()) return true;
        if (t.title.toLowerCase(Locale.ROOT).contains(query) || t.author.toLowerCase(Locale.ROOT).contains(query)
                || t.desc.toLowerCase(Locale.ROOT).contains(query) || t.id.contains(query)) return true;
        for (String tag : t.tags) if (tag.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }

    private static String title(String view) {
        if (view.startsWith("group:")) return switch (view.substring(6)) {
            case "hud" -> "战斗 HUD";
            case "menu" -> "界面与菜单";
            default -> "主题氛围";
        };
        return switch (view) {
            case "official" -> "官方模板";
            case "community" -> "社区模板";
            case "fav" -> "收藏";
            case "saved" -> "已保存";
            case "recent" -> "最近浏览";
            case "guide" -> "投稿指南";
            default -> "全部模板";
        };
    }

    private String subtitle(String view) {
        return switch (view) {
            case "official" -> "优易库维护的模板，跟着库一起更新";
            case "community" -> "玩家和模组作者分享的模板";
            case "fav" -> "你收藏的模板";
            case "saved" -> "保存过的副本可以直接改，存盘后游戏里立刻刷新";
            case "recent" -> "最近打开过的模板";
            case "guide" -> "把你做的界面分享出去";
            default -> "完整的界面风格，都用 HTML 与 CSS 写成，可以直接保存来改";
        };
    }

    private void show(String id, boolean on) {
        Element e = doc.getElementById(id);
        if (e != null) e.toggleClass("on", on);
    }

    // ---- Details ----

    private void openDetails(Template t) {
        if (t == null) return;
        if (open != null) parkSpec(open);
        open = t;
        Dom.byId(doc, "d-cover").setAttribute("src", "../textures/ui/templates/" + t.id + ".png");
        Dom.text(Dom.byId(doc, "d-title"), t.title);
        Dom.text(Dom.byId(doc, "d-author"), t.author);
        Dom.text(Dom.byId(doc, "d-ver"), t.ver + " · " + t.date);
        Dom.text(Dom.byId(doc, "d-desc"), t.desc);
        Dom.text(Dom.byId(doc, "d-note"), "");
        Element spec = Dom.byId(doc, "d-spec");
        // The entry's spec moves into the drawer while it is open, and back after.
        while (t.spec.childCount() > 0) spec.appendChild(t.spec.childAt(0));
        Element scroll = drawer.querySelector(".dscroll");
        if (scroll != null) scroll.scrollTo(0, 0);
        refreshDetails();
        drawer.addClass("on");
        veil.addClass("on");
        send("recent", t.id, false);
    }

    private void parkSpec(Template t) {
        Element spec = Dom.byId(doc, "d-spec");
        while (spec.childCount() > 0) t.spec.appendChild(spec.childAt(0));
    }

    private void closeDetails() {
        if (open == null) return;
        parkSpec(open);
        open = null;
        drawer.removeClass("on");
        veil.removeClass("on");
    }

    private void refreshDetails() {
        if (open == null) return;
        boolean isSaved = saved.contains(open.id);
        Element save = Dom.byId(doc, "d-save");
        Dom.text(Dom.query(save, "span"), isSaved ? "打开我的副本" : "一键保存");
        save.toggleClass("primary", isSaved);
        Dom.byId(doc, "d-fav").toggleClass("on", favorites.contains(open.id));
    }

    private void preview(Template t) {
        if (t == null) return;
        returnTo = t.id;
        Shell.go(doc, OrielKit.page("styles/" + t.id), "forward");
    }

    /** Saves a copy into the player's folder; a template already saved opens its copy instead. */
    private void save(Template t) {
        if (t == null) return;
        if (saved.contains(t.id)) {
            returnTo = t.id;
            doc.navigate(namespace + ":" + t.id + ".html");
            return;
        }
        if (!send("save", t.id, false)) saved.add(t.id);
        Dom.text(Dom.byId(doc, "d-note"), "已保存到 " + folder + t.id + ".html，改完存盘游戏里就会刷新");
        render();
        refreshDetails();
    }

    private void favorite(Template t, boolean on) {
        if (t == null) return;
        if (!send("fav", t.id, on)) {
            if (on) favorites.add(t.id);
            else favorites.remove(t.id);
        } else if (on) {
            favorites.add(t.id); // shown at once; the game's state confirms it
        } else {
            favorites.remove(t.id);
        }
        render();
        refreshDetails();
    }

    /** Asks the game to change the library; false without a game, where the page keeps the change itself. */
    private boolean send(String op, String id, boolean on) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.addProperty("id", id);
        o.addProperty("on", on);
        boolean sent = doc.host().send(OrielKit.CHANNEL, o.toString());
        if (!sent && op.equals("recent")) {
            recent.remove(id);
            recent.addFirst(id);
        }
        return sent;
    }

    /** The game's library: favourites, saved copies, the recently viewed, and where the copies live. */
    private void library(JsonElement value) {
        if (!value.isJsonObject()) return;
        JsonObject o = value.getAsJsonObject();
        if (o.has("favorites")) replace(favorites, o.getAsJsonArray("favorites"));
        if (o.has("saved")) replace(saved, o.getAsJsonArray("saved"));
        if (o.has("recent")) {
            recent.clear();
            for (JsonElement e : o.getAsJsonArray("recent")) recent.add(e.getAsString());
        }
        if (o.has("namespace")) namespace = o.get("namespace").getAsString();
        if (o.has("folder") && !o.get("folder").getAsString().isEmpty()) {
            folder = o.get("folder").getAsString().replace('\\', '/');
            if (!folder.endsWith("/")) folder += "/";
        }
        render();
        refreshDetails();
    }

    private static void replace(Set<String> set, JsonArray ids) {
        set.clear();
        for (JsonElement e : ids) set.add(e.getAsString());
    }

    private Template find(String id) {
        for (Template t : templates) if (t.id.equals(id)) return t;
        return null;
    }

    private static String attr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null ? "" : v;
    }
}
