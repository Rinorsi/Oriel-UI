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
import dev.oriel.kit.LocalPage;
import dev.oriel.kit.Lang;
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
    /** Pages found in the player's folder (paths below it, as the game lists them) and their cards. */
    private record Page(String path, String title, String subtitle, String cover, long modified, long size) {}

    private final List<Page> pages = new ArrayList<>();
    private final List<Element> pageCards = new ArrayList<>();
    private final List<Element> demoCards = new ArrayList<>();

    /** Finished demo pages the centre lists next to the templates: id, the page they open (cover: textures/ui/demos/id.png). */
    private static final String[][] DEMOS = {
            {"title", "oriel:oriel/showcase/title.html"}, {"hud", "oriel:oriel/showcase/hud.html"}, {"shop", "oriel:oriel/showcase/shop.html"},
            {"mobdex", "oriel:oriel/showcase/mobdex.html"}, {"journal", "oriel:oriel/showcase/journal.html"},
            {"console", "oriel:oriel/showcase/console.html"}, {"models", "oriel:oriel/showcase/models.html"},
            {"scene", "oriel:oriel/showcase/scene.html"}, {"layout", "oriel:oriel/demo/layout.html"},
            {"animation", "oriel:oriel/demo/animation.html"}, {"livedata", "oriel:oriel/demo/templates.html"},
            {"forms", "oriel:oriel/demo/settings.html"}, {"achievement", "oriel:oriel/showcase/achievement.html"}};
    /** The view the centre opens on next time (set by {@link #startAt}), e.g. "demos"; then back to "all". */
    private static volatile String startView;
    /** Where the player was in the centre when a page opened from it: Escape on that page comes back here, not to the top of All. */
    private record Place(String view, String sort, String query, String tag, float scroll, long at) {}
    private static volatile Place place;
    private static final long PLACE_MS = 10 * 60 * 1000L;
    private String namespace = "oriel_mine", folder = "config/oriel/my-pages/";
    private String view = "all", sort = "pick", query = "", tag = "";
    private Element tagbar;
    private Template open;
    private Element grid, drawer, veil, specHome;

    private void rememberPlace() {
        Element list = Dom.byId(doc, "scroll");
        place = new Place(view, sort, query, tag, list == null ? 0 : list.scrollTop(), System.currentTimeMillis());
    }

    /** The next template centre opens on {@code view} ("demos", "pages", "fav", ...) instead of All. */
    public static void startAt(String view) {
        startView = view;
    }

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
        Dom.on(Dom.byId(doc, "scroll"), "scroll", ev -> loadCovers());
        tagbar = Dom.byId(doc, "tagbar");
        drawer = Dom.byId(doc, "drawer");
        veil = Dom.byId(doc, "veil");
        specHome = Dom.byId(doc, "tpls");
        buildDemoCards();
        String start = startView;
        startView = null;
        if (start != null) view = start;
        Place back = place;
        place = null;
        float restoreScroll = -1;
        if (start == null && back != null && System.currentTimeMillis() - back.at() < PLACE_MS) {
            view = back.view();
            sort = back.sort();
            query = back.query();
            tag = back.tag();
            restoreScroll = back.scroll();
        }
        int order = 0;
        for (Element e : doc.querySelectorAll(".tpl")) {
            String id = e.getAttribute("data-id");
            List<String> tags = List.of(attr(e, "data-tags").split(","));
            Template t = new Template(id, attr(e, "data-title"), attr(e, "data-author"), attr(e, "data-source"), attr(e, "data-group"),
                    attr(e, "data-ver"), attr(e, "data-date"), tags, attr(e, "data-desc"), e, null, order++);
            templates.add(new Template(t.id, t.title, t.author, t.source, t.group, t.ver, t.date, t.tags, t.desc, t.spec, card(t), t.order));
        }
        buildTagbar();
        for (Element chip : tagbar.querySelectorAll("span")) chip.toggleClass("on", chip.getAttribute("data-tag").equals(tag));
        for (Element s : doc.querySelectorAll("#sort [data-sort]")) s.toggleClass("on", s.getAttribute("data-sort").equals(sort));
        for (Element item : doc.querySelectorAll("#side .item")) item.toggleClass("on", item.getAttribute("data-view").equals(view));
        for (Element item : doc.querySelectorAll("#side .item")) {
            Shell.activate(item, () -> {
                view = item.getAttribute("data-view");
                for (Element i : doc.querySelectorAll("#side .item")) i.toggleClass("on", i == item);
                if (view.equals("pages")) send("refresh", "", false); // files may have been copied in since the page opened
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
        if (!query.isEmpty()) q.setValue(query);
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
        if (doc.initialData() != null) {
            // The game hands the library over as {"library": {...}}; the preview harness passes the bare object.
            JsonElement first = JsonParser.parseString(doc.initialData());
            library(first.isJsonObject() && first.getAsJsonObject().has(LIBRARY) ? first.getAsJsonObject().get(LIBRARY) : first);
        }
        doc.addDataListener((key, json) -> {
            if (json == null) return;
            JsonElement value = JsonParser.parseString(json);
            if (LIBRARY.equals(key)) library(value);
            else if (key == null && value.isJsonObject() && value.getAsJsonObject().has(LIBRARY)) library(value.getAsJsonObject().get(LIBRARY));
        });
        render();
        String backTo = returnTo;
        returnTo = null;
        if (backTo != null) for (Template t : templates) if (t.id.equals(backTo)) openDetails(t);
        if (restoreScroll > 0) {
            // The list may still be filling (the game sends the folder's pages a moment after the page opens): try twice.
            float y = restoreScroll;
            Element list = Dom.byId(doc, "scroll");
            doc.scheduler().setTimeout(() -> list.scrollTo(0, y), 120);
            doc.scheduler().setTimeout(() -> list.scrollTo(0, y), 400);
        }
    }

    /** A template's card: its cover, badges, favourite, title, author and tags. */
    private Element card(Template t) {
        Element card = Dom.html(doc, "<div class=\"card o-tile m-spot\" tabindex=\"0\"><div class=\"cover\"><img class=\"shot\">"
                + "<i class=\"sheen\"></i><span class=\"badge\"></span><div class=\"fav\" title=\"" + Lang.html(doc, "oriel.ui.styles.drawer.favorite") + "\"><img class=\"o-ico\" "
                + "src=\"../textures/icons/heart.png\"></div><span class=\"saved-mark\"><img class=\"o-ico\" src=\"../textures/icons/check.png\">" + Lang.html(doc, "oriel.ui.styles.side.saved") + "</span>"
                + "</div><div class=\"meta\"><b></b><div class=\"by\"><img src=\"../textures/ui/emblem.png\"><span></span><em></em></div>"
                + "<div class=\"tags\"></div></div></div>");
        Dom.query(card, "img.shot").setAttribute("src", "../textures/ui/templates/" + t.id + ".png");
        Element badge = Dom.query(card, ".badge");
        boolean official = t.source.equals("official");
        Dom.text(badge, Lang.t(doc, official ? "oriel.ui.styles.badge.official" : "oriel.ui.styles.badge.community"));
        badge.toggleClass("official", official);
        Dom.text(Dom.query(card, ".meta b"), t.title);
        Dom.text(Dom.query(card, ".by span"), t.author);
        Dom.text(Dom.query(card, ".by em"), t.ver);
        Element tags = Dom.query(card, ".tags");
        for (String name : t.tags) {
            Element chip = Dom.el(doc, "span", null, name);
            Dom.onClick(chip, ev -> {
                ev.stopPropagation();
                selectTag(name);
            });
            tags.appendChild(chip);
        }
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
        for (Element c : pageCards) c.remove();
        for (Element c : demoCards) c.remove();
        if (view.equals("pages")) for (Element c : ordered(pageCards)) if (pageMatches(c)) grid.appendChild(c);
        if (view.equals("demos")) for (Element c : ordered(demoCards)) if (pageMatches(c)) grid.appendChild(c);
        tagbar.toggleClass("off", view.equals("pages") || view.equals("demos") || view.equals("guide"));
        for (Template t : shown) {
            grid.appendChild(t.card);
            t.card.toggleClass("faved", favorites.contains(t.id));
            Dom.query(t.card, ".fav").toggleClass("on", favorites.contains(t.id));
            Dom.query(t.card, ".saved-mark").setAttribute("style", saved.contains(t.id) ? "" : "display: none");
        }
        boolean pagesView = view.equals("pages"), demosView = view.equals("demos");
        boolean mine = view.equals("fav") || view.equals("saved") || view.equals("recent") || pagesView;
        show("empty-community", community && shown.isEmpty() && query.isEmpty());
        show("empty-mine", mine && (pagesView ? pages.isEmpty() : shown.isEmpty()) && query.isEmpty());
        Dom.text(Dom.byId(doc, "empty-mine-text"), Lang.t(doc, pagesView ? "oriel.ui.styles.empty.pages_text" : "oriel.ui.styles.empty.mine.text"));
        show("empty-search", !guide && (pagesView ? pageCount(pageCards) == 0 : demosView ? pageCount(demoCards) == 0 : shown.isEmpty()) && !query.isEmpty());
        show("guide", guide);
        Dom.text(Dom.byId(doc, "empty-mine-title"), Lang.t(doc, "oriel.ui.styles.empty." + switch (view) {
            case "fav" -> "fav";
            case "saved" -> "saved";
            case "pages" -> "pages";
            default -> "recent";
        }));
        if (guide) Dom.text(Dom.byId(doc, "guide-folder"), folder);
        Dom.text(Dom.byId(doc, "view-title"), title(view));
        Dom.text(Dom.byId(doc, "view-sub"), Lang.t(doc, "oriel.ui.styles.view." + (view.startsWith("group:") ? "all" : view) + ".sub"));
        Dom.text(Dom.byId(doc, "count"), guide ? "" : Lang.plural(doc, "oriel.ui.styles.count", pagesView ? pageCount(pageCards) : demosView ? pageCount(demoCards) : shown.size()));
        Dom.text(Dom.byId(doc, "crumb"), Lang.t(doc, "oriel.ui.styles.crumb.view", title(view)));
        Dom.text(Dom.byId(doc, "foot-count"), Lang.plural(doc, "oriel.ui.styles.foot_count", templates.size()));
        counts();
        loadCovers();
    }

    private static final int COVERS_PER_STEP = 3, COVER_STEP_MS = 50;
    private boolean coversQueued;

    /** Page covers load a few at a time, for the cards that show or are about to; a folder of thirty pages opens at once and the pictures follow. */
    private void loadCovers() {
        if (coversQueued) return;
        coversQueued = true;
        doc.scheduler().setTimeout(this::coverStep, COVER_STEP_MS);
    }

    private void coverStep() {
        coversQueued = false;
        Element list = Dom.byId(doc, "scroll");
        if (list == null) return;
        float[] port = list.getBoundingClientRect();
        int loaded = 0;
        boolean more = false;
        for (Element card : pageCards) {
            Element img = Dom.query(card, "img.shot");
            String url = img == null ? null : img.getAttribute("data-cover");
            if (url == null || url.isEmpty()) continue;
            float[] r = card.getBoundingClientRect();
            if (r[3] <= 0 || r[1] > port[1] + port[3] + r[3] || r[1] + r[3] < port[1] - r[3]) continue; // hidden or more than a row away
            if (loaded == COVERS_PER_STEP) { more = true; break; }
            img.setAttribute("src", url);
            img.setAttribute("data-cover", "");
            loaded++;
        }
        if (more) loadCovers();
    }

    private void counts() {
        for (Element item : doc.querySelectorAll("#side .item")) {
            Element n = item.querySelector(".n");
            if (n == null) continue;
            String v = item.getAttribute("data-view");
            int count = 0;
            if (v.equals("pages")) count = pages.size();
            else if (v.equals("demos")) count = DEMOS.length;
            else for (Template t : templates) if (inView(t, v)) count++;
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
            case "guide", "pages", "demos" -> false;
            default -> true;
        };
    }

    private boolean matches(Template t) {
        if (!tag.isEmpty() && !t.tags.contains(tag)) return false;
        if (query.isEmpty()) return true;
        if (t.title.toLowerCase(Locale.ROOT).contains(query) || t.author.toLowerCase(Locale.ROOT).contains(query)
                || t.desc.toLowerCase(Locale.ROOT).contains(query) || t.id.contains(query)) return true;
        for (String tag : t.tags) if (tag.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }

    /** A view's title in the headline and the crumb: the sidebar's label, in full where that is shortened. */
    private String title(String view) {
        String v = view.startsWith("group:") ? view.substring(6) : view;
        return Lang.t(doc, "oriel.ui.styles.view." + (switch (v) {
            case "official", "community", "fav", "saved", "recent", "pages", "demos", "guide", "hud", "menu", "theme" -> v;
            default -> "all";
        }) + ".title");
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
        Dom.text(Dom.query(save, "span"), Lang.t(doc, isSaved ? "oriel.ui.styles.drawer.open_copy" : "oriel.ui.styles.drawer.save"));
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
            rememberPlace();
            doc.navigate(namespace + ":" + t.id + ".html");
            return;
        }
        if (!send("save", t.id, false)) saved.add(t.id);
        Dom.text(Dom.byId(doc, "d-note"), Lang.t(doc, "oriel.ui.styles.saved_to", folder + t.id + ".html"));
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
        if (o.has("pages")) {
            pages.clear();
            for (JsonElement e : o.getAsJsonArray("pages")) {
                if (e.isJsonObject()) {
                    JsonObject p = e.getAsJsonObject();
                    String path = p.get("path").getAsString();
                    pages.add(new Page(path, p.has("title") ? p.get("title").getAsString() : fileName(path), str(p, "subtitle"), str(p, "cover"),
                            p.has("modified") ? p.get("modified").getAsLong() : 0, p.has("size") ? p.get("size").getAsLong() : 0));
                } else {
                    pages.add(new Page(e.getAsString(), fileName(e.getAsString()), "", "", 0, 0));
                }
            }
            rebuildPageCards();
        }
        if (o.has("namespace")) namespace = o.get("namespace").getAsString();
        if (o.has("folder") && !o.get("folder").getAsString().isEmpty()) {
            folder = o.get("folder").getAsString().replace('\\', '/');
            if (!folder.endsWith("/")) folder += "/";
        }
        render();
        refreshDetails();
    }

    /** One plain card per page in the folder; opening it loads the page itself, so what the player sees is the file. */
    private void rebuildPageCards() {
        for (Element c : pageCards) c.remove();
        pageCards.clear();
        for (Page page : pages) {
            String path = page.path();
            boolean covered = !page.cover().isEmpty();
            // A cover is the page's own picture; without one the card shows a plain icon.
            Element card = Dom.html(doc, "<div class=\"card o-tile m-spot\" tabindex=\"0\"><div class=\"cover\">" + (covered
                    ? "<img class=\"shot\">"
                    : "<img class=\"o-ico\" src=\"../textures/icons/layers.png\" style=\"position: absolute; left: 40%; top: 35%; width: 20%; height: 30%\">")
                    + "</div><div class=\"meta\"><b></b><div class=\"by\"><span></span></div></div></div>");
            // The picture is loaded when the card is about to show (see loadCovers), not for every page in the folder at once.
            if (covered) Dom.query(card, "img.shot").setAttribute("data-cover", namespace + ":" + page.cover());
            card.setAttribute("data-path", (page.title() + " " + page.subtitle() + " " + path).toLowerCase(Locale.ROOT));
            card.setAttribute("data-title", page.title().toLowerCase(Locale.ROOT));
            card.setAttribute("data-mod", Long.toString(page.modified()));
            card.setAttribute("title", tooltip(page));
            Dom.text(Dom.query(card, ".meta b"), page.title());
            Dom.text(Dom.query(card, ".by span"), page.subtitle().isEmpty() ? LocalPage.cut(path, LocalPage.SUBTITLE_MAX) : page.subtitle());
            Shell.activate(card, () -> {
                returnTo = null;
                rememberPlace();
                doc.navigate(namespace + ":" + path);
            });
            pageCards.add(card);
        }
    }

    /** The hover text of a page card: title, subtitle, where the file is, when it changed and how big it is; every line is cut short. */
    private String tooltip(Page page) {
        StringBuilder text = new StringBuilder(LocalPage.cut(page.title(), LocalPage.TITLE_MAX));
        if (!page.subtitle().isEmpty()) text.append('\n').append(LocalPage.cut(page.subtitle(), LocalPage.SUBTITLE_MAX));
        text.append('\n').append(LocalPage.cut(folder + page.path(), 72));
        if (page.modified() > 0) {
            String when = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(page.modified()), java.time.ZoneId.systemDefault()));
            text.append('\n').append(when).append(" · ").append(page.size() < 1024 ? page.size() + " B" : String.format(Locale.ROOT, "%.1f KB", page.size() / 1024.0));
        }
        return text.toString();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static String fileName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private boolean pageMatches(Element card) {
        return query.isEmpty() || card.getAttribute("data-path").toLowerCase(Locale.ROOT).contains(query);
    }

    /** The commonest tags (at most twelve), as chips above the grid; one is selected at a time, a second click clears it. */
    private void buildTagbar() {
        java.util.Map<String, Integer> count = new java.util.TreeMap<>();
        for (Template t : templates) for (String name : t.tags) if (!name.isBlank()) count.merge(name, 1, Integer::sum);
        List<String> names = new ArrayList<>(count.keySet());
        names.sort(Comparator.comparingInt((String n) -> -count.get(n)).thenComparing(n -> n));
        for (String name : names.subList(0, Math.min(12, names.size()))) {
            Element chip = Dom.el(doc, "span", null, name);
            chip.setAttribute("data-tag", name);
            Dom.onClick(chip, ev -> selectTag(name));
            tagbar.appendChild(chip);
        }
    }

    private void selectTag(String name) {
        tag = tag.equals(name) ? "" : name;
        for (Element chip : tagbar.querySelectorAll("span")) chip.toggleClass("on", chip.getAttribute("data-tag").equals(tag));
        render();
    }

    /** Page and demo cards in the order the sort asks: by title with 01, 02, 10 in number order, or by when the file changed. */
    private List<Element> ordered(List<Element> cards) {
        List<Element> out = new ArrayList<>(cards);
        Comparator<Element> byTitle = (a, b) -> naturalCompare(attr(a, "data-title"), attr(b, "data-title"));
        switch (sort) {
            case "name" -> out.sort(byTitle);
            case "new" -> out.sort(Comparator.comparingLong((Element e) -> modified(e)).reversed().thenComparing(byTitle));
            default -> {
                if (cards == pageCards) out.sort(byTitle);   // the files have no order of their own; demos keep the declared one
            }
        }
        return out;
    }

    private long modified(Element card) {
        try {
            return Long.parseLong(attr(card, "data-mod"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Compares text so that runs of digits go by value: "2" before "10", "01" before "02". */
    static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                java.math.BigInteger na = new java.math.BigInteger(a.substring(si, i)), nb = new java.math.BigInteger(b.substring(sj, j));
                int c = na.compareTo(nb);
                if (c != 0) return c;
            } else {
                if (x != y) return Character.compare(x, y);
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private int pageCount(List<Element> cards) {
        int n = 0;
        for (Element c : cards) if (pageMatches(c)) n++;
        return n;
    }

    /** One card per demo page: its cover, title and a line about it; opening it loads the page itself. */
    private void buildDemoCards() {
        for (String[] d : DEMOS) {
            String title = Lang.t(doc, "oriel.ui.styles.demo." + d[0] + ".title"), desc = Lang.t(doc, "oriel.ui.styles.demo." + d[0] + ".desc");
            Element card = Dom.html(doc, "<div class=\"card o-tile m-spot\" tabindex=\"0\"><div class=\"cover\"><img class=\"shot\"><i class=\"sheen\"></i><span class=\"badge\"></span></div>"
                    + "<div class=\"meta\"><b></b><div class=\"by\"><span></span></div></div></div>");
            Dom.text(Dom.query(card, ".badge"), Lang.t(doc, "oriel.ui.styles.badge.legacy"));
            Dom.query(card, "img.shot").setAttribute("src", "../textures/ui/demos/" + d[0] + ".png");
            card.setAttribute("data-path", (title + " " + desc + " " + d[0]).toLowerCase(Locale.ROOT));
            card.setAttribute("data-title", title.toLowerCase(Locale.ROOT));
            Dom.text(Dom.query(card, ".meta b"), title);
            Dom.text(Dom.query(card, ".by span"), desc);
            String url = d[1];
            Shell.activate(card, () -> {
                returnTo = null;
                rememberPlace();
                doc.navigate(url);
            });
            demoCards.add(card);
        }
    }

    private static void replace(Set<String> set, JsonArray ids) {
        set.clear();
        for (JsonElement e : ids) set.add(e.getAsString());
    }

    private Template find(String id) {
        for (Template t : templates) if (t.id.equals(id)) return t;
        return null;
    }

    /** A template's metadata; the library's own templates give keys, resolved in the page's language. */
    private String attr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null ? "" : Lang.value(doc, v);
    }
}
