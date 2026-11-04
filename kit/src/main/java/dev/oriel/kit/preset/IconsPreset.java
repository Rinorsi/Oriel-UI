package dev.oriel.kit.preset;

import dev.oriel.kit.Lang;

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
 * The icons and their names come from {@code icons.json} next to the page. Two more categories show what else can
 * be an icon: vanilla's GUI sprites ({@code sprite:} URLs: hearts, hunger, armour...) and any item ({@code <item>});
 * typing an item id in the search shows that item.
 */
public final class IconsPreset implements PageControllers.Controller {
    /** What an icon is drawn from: one of the library's PNGs, a vanilla GUI sprite, or an item. */
    enum Kind { ICON, SPRITE, ITEM }

    /** One icon: its file name (sprite or item id), its Chinese name, its category and tags. */
    record Icon(String id, String name, String cat, List<String> tags, Kind kind) {
        Icon(String id, String name, String cat, List<String> tags) {
            this(id, name, cat, tags, Kind.ICON);
        }

        /** The image URL ({@link Kind#ITEM} has none: it is an {@code <item>}). */
        String src() {
            return kind == Kind.SPRITE ? "sprite:" + id : "../textures/icons/" + id + ".png";
        }

        /** The markup that draws it. */
        String markup() {
            return switch (kind) {
                case ICON -> "<img class=\"o-ico\" src=\"../textures/icons/" + id + ".png\">";
                case SPRITE -> "<img src=\"sprite:" + id + "\">";
                case ITEM -> "<item id=\"" + id + "\"></item>";
            };
        }

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
    static final String VANILLA = "vanilla", ITEMS = "items";
    /** A selection of vanilla's GUI sprites: id (under textures/gui/sprites), name, tags. */
    static final String[][] SPRITES = {
            {"minecraft:hud/heart/full", "oriel.ui.icons.health", "oriel.ui.icons.heart_health_hp_heart"}, {"minecraft:hud/heart/half", "oriel.ui.icons.half_heart", "heart half"},
            {"minecraft:hud/heart/container", "oriel.ui.icons.heart_container", "heart empty"}, {"minecraft:hud/heart/poisoned_full", "oriel.ui.icons.poisoned_heart", "heart poison"},
            {"minecraft:hud/heart/withered_full", "oriel.ui.icons.withered_heart", "heart wither"}, {"minecraft:hud/heart/frozen_full", "oriel.ui.icons.frozen_heart", "heart frozen"},
            {"minecraft:hud/heart/absorbing_full", "oriel.ui.icons.absorption", "heart absorption gold"},
            {"minecraft:hud/food_full", "oriel.ui.icons.hunger", "oriel.ui.icons.food_hunger_chicken_leg"}, {"minecraft:hud/food_half", "oriel.ui.icons.half_a_hunger_bar", "food half"},
            {"minecraft:hud/food_empty", "oriel.ui.icons.hunger_bar", "food empty"}, {"minecraft:hud/food_full_hunger", "oriel.ui.icons.hunger_2", "food hunger effect"},
            {"minecraft:hud/armor_full", "oriel.ui.icons.armor", "armor armour"}, {"minecraft:hud/armor_half", "oriel.ui.icons.half_an_armor_bar", "armor half"},
            {"minecraft:hud/armor_empty", "oriel.ui.icons.armor_bar", "armor empty"}, {"minecraft:hud/air", "oriel.ui.icons.oxygen", "air bubble oxygen"},
            {"minecraft:hud/air_bursting", "oriel.ui.icons.oxygen_depleted", "air bubble"}, {"minecraft:hud/crosshair", "oriel.ui.icons.crosshair", "crosshair aim"},
            {"minecraft:statistics/block_mined", "oriel.ui.icons.mine", "stat mined"}, {"minecraft:statistics/item_crafted", "oriel.ui.icons.craft", "stat crafted"},
            {"minecraft:statistics/item_used", "oriel.ui.icons.use", "stat used"}, {"minecraft:statistics/item_broken", "oriel.ui.icons.damage", "stat broken"},
            {"minecraft:statistics/item_picked_up", "oriel.ui.icons.pick_up", "stat picked"}, {"minecraft:statistics/item_dropped", "oriel.ui.icons.drop", "stat dropped"},
            {"minecraft:icon/checkmark", "oriel.ui.icons.check_mark", "check ok"}, {"minecraft:icon/info", "oriel.ui.icons.information", "info"},
            {"minecraft:icon/language", "oriel.ui.icons.language", "language"}, {"minecraft:icon/accessibility", "oriel.ui.icons.accessibility", "accessibility"},
            {"minecraft:icon/search", "oriel.ui.icons.search", "search"}, {"minecraft:icon/link", "oriel.ui.icons.link", "link"},
            {"minecraft:icon/music_notes", "oriel.ui.icons.music", "music"}, {"minecraft:icon/news", "oriel.ui.icons.news", "news"},
            {"minecraft:icon/ping_5", "oriel.ui.icons.full_signal", "ping signal"}, {"minecraft:icon/ping_2", "oriel.ui.icons.weak_signal", "ping signal"},
    };
    /** A selection of items; any other item id typed in the search shows too. */
    static final String[][] ITEM_ICONS = {
            {"minecraft:diamond_sword", "oriel.ui.icons.diamond_sword"}, {"minecraft:iron_pickaxe", "oriel.ui.icons.iron_pickaxe"}, {"minecraft:bow", "oriel.ui.icons.bow"},
            {"minecraft:shield", "oriel.ui.icons.shield"}, {"minecraft:trident", "oriel.ui.icons.trident"}, {"minecraft:diamond", "oriel.ui.icons.diamond"},
            {"minecraft:emerald", "oriel.ui.icons.emerald"}, {"minecraft:gold_ingot", "oriel.ui.icons.gold_ingot"}, {"minecraft:redstone", "oriel.ui.icons.redstone"},
            {"minecraft:lapis_lazuli", "oriel.ui.icons.lapis_lazuli"}, {"minecraft:nether_star", "oriel.ui.icons.nether_star"}, {"minecraft:heart_of_the_sea", "oriel.ui.icons.heart_of_the_sea"},
            {"minecraft:apple", "oriel.ui.icons.apple"}, {"minecraft:golden_apple", "oriel.ui.icons.golden_apple"}, {"minecraft:bread", "oriel.ui.icons.bread"},
            {"minecraft:cooked_beef", "oriel.ui.icons.steak"}, {"minecraft:cake", "oriel.ui.icons.cake"}, {"minecraft:totem_of_undying", "oriel.ui.icons.totem_of_undying"},
            {"minecraft:ender_pearl", "oriel.ui.icons.ender_pearl"}, {"minecraft:experience_bottle", "oriel.ui.icons.bottle_o_enchanting"}, {"minecraft:blaze_rod", "oriel.ui.icons.blaze_rod"},
            {"minecraft:book", "oriel.ui.icons.book"}, {"minecraft:enchanted_book", "oriel.ui.icons.enchanted_book"}, {"minecraft:compass", "oriel.ui.icons.compass"},
            {"minecraft:clock", "oriel.ui.icons.clock"}, {"minecraft:map", "oriel.ui.icons.map"}, {"minecraft:elytra", "oriel.ui.icons.elytra"},
            {"minecraft:grass_block", "oriel.ui.icons.grass_block"}, {"minecraft:oak_log", "oriel.ui.icons.oak_log"}, {"minecraft:chest", "oriel.ui.icons.chest"},
            {"minecraft:crafting_table", "oriel.ui.icons.crafting_table"}, {"minecraft:tnt", "oriel.ui.common.tnt"},
    };
    private static final java.util.regex.Pattern ITEM_ID = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
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
        int library = icons.size();
        categories.add(new String[] {VANILLA, Lang.t(doc, "oriel.ui.icons.vanilla_icons")});
        for (String[] s : SPRITES) icons.add(new Icon(s[0], Lang.value(doc, s[1]), VANILLA, List.of(Lang.value(doc, s[2]).split(" ")), Kind.SPRITE));
        categories.add(new String[] {ITEMS, Lang.t(doc, "oriel.ui.icons.any_item")});
        for (String[] s : ITEM_ICONS) icons.add(new Icon(s[0], Lang.value(doc, s[1]), ITEMS, List.of(s[0].substring(s[0].indexOf(':') + 1)), Kind.ITEM));
        Element cats = Dom.byId(doc, "cats");
        cats.appendChild(category("all", Lang.t(doc, "oriel.ui.icons.all_icons_2"), icons.size()));
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
        Dom.text(Dom.byId(doc, "total"), library + Lang.t(doc, "oriel.ui.icons.icons_16_16_pixels_tinted_with_mc"));
        render();
        if (!icons.isEmpty()) choose(icons.getFirst());
    }

    private void load(String json) {
        if (json == null) return;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (var c : root.getAsJsonArray("categories")) {
            JsonObject o = c.getAsJsonObject();
            categories.add(new String[] {o.get("id").getAsString(), Lang.t(doc, "oriel.ui.icon_category." + o.get("id").getAsString())});
        }
        for (var e : root.getAsJsonObject("icons").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<String> tags = new ArrayList<>();
            for (int i = 0; i < o.getAsJsonArray("tags").size(); i++) tags.add(Lang.t(doc, "oriel.ui.icon." + e.getKey() + ".tag." + i));
            icons.add(new Icon(e.getKey(), Lang.t(doc, "oriel.ui.icon." + e.getKey() + ".name"), o.get("cat").getAsString(), tags));
        }
    }

    private Element category(String id, String name, int n) {
        Element row = Dom.el(doc, "div", "cat" + (id.equals(category) ? " on" : ""), null);
        row.setAttribute("tabindex", "0");
        row.setAttribute("data-cat", id);
        // The category's first icon stands for it.
        String face = id.equals("all") ? "star" : id.equals(VANILLA) ? "heart" : id.equals(ITEMS) ? "sword"
                : icons.stream().filter(i -> i.cat().equals(id)).map(Icon::id).findFirst().orElse("grid");
        Element img = Dom.el(doc, "img", "o-ico inherit", null);
        img.setAttribute("src", "../textures/icons/" + face + ".png");
        row.appendChild(img);
        row.appendChild(Dom.el(doc, "span", null, name));
        row.appendChild(Dom.el(doc, "span", "n", Integer.toString(n)));
        Shell.activate(row, () -> {
            category = id;
            for (Element c : Dom.byId(doc, "cats").children()) c.toggleClass("on", c == row);
            Dom.text(Dom.byId(doc, "crumb"), "/ " + name);
            Dom.byId(doc, "q").setAttribute("placeholder", id.equals(ITEMS) ? Lang.t(doc, "oriel.ui.icons.item_name_or_id_such_as_minecraft")
                    : Lang.t(doc, "oriel.ui.icons.search_names_spellings_or_tags"));
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
        // An item id typed in the search is that item, listed or not.
        if (ITEM_ID.matcher(query).matches() && (category.equals("all") || category.equals(ITEMS))
                && shown.stream().noneMatch(i -> i.kind() == Kind.ITEM && i.id().equals(query))) {
            shown.addFirst(new Icon(query, query.substring(query.indexOf(':') + 1), ITEMS, List.of(), Kind.ITEM));
        }
        Dom.text(Dom.byId(doc, "count"), shown.size() + Lang.t(doc, "oriel.ui.icons.items"));
        if (shown.isEmpty()) {
            grid.appendChild(Dom.el(doc, "div", "empty", Lang.t(doc, "oriel.ui.icons.no_results_found_for") + query + "」"));
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
        Element t = Dom.el(doc, "div", "tile" + (icon == chosen ? " on" : "") + (icon.kind() == Kind.ICON ? "" : " native"), null);
        t.setAttribute("tabindex", "0");
        t.setAttribute("data-id", icon.id());
        t.setAttribute("title", icon.name() + " · " + icon.id());
        t.appendChild(picture(icon, "o-ico inherit"));
        t.appendChild(Dom.el(doc, "span", "nm", icon.shortName()));
        Shell.activate(t, () -> choose(icon));
        return t;
    }

    void choose(Icon icon) {
        chosen = icon;
        for (Element t : Dom.byId(doc, "grid").children()) t.toggleClass("on", icon.id().equals(t.getAttribute("data-id")));
        Element stage = Dom.byId(doc, "stage");
        for (Element old : List.copyOf(stage.children())) if (old.tagName().equals("item")) old.remove();
        boolean item = icon.kind() == Kind.ITEM;
        stage.toggleClass("item", item);
        stage.toggleClass("native", icon.kind() != Kind.ICON);
        Dom.byId(doc, "detail").toggleClass("native", icon.kind() != Kind.ICON);
        if (item) {
            for (String size : List.of("big", "mid", "small")) stage.appendChild(picture(icon, size));
        } else {
            for (String id : List.of("d-big", "d-mid", "d-small")) Dom.byId(doc, id).setAttribute("src", icon.src());
        }
        Dom.text(Dom.byId(doc, "d-name"), icon.name());
        Dom.text(Dom.byId(doc, "d-id"), icon.id() + " · " + categoryName(icon.cat()));
        Element tags = Dom.byId(doc, "d-tags");
        tags.removeAllChildren();
        for (String tag : icon.tags()) tags.appendChild(Dom.el(doc, "span", "o-chip", tag));
        Dom.text(Dom.byId(doc, "d-code"), icon.markup());
    }

    /** The icon as an element with {@code classes}: an {@code <img>}, or an {@code <item>} for an item. */
    private Element picture(Icon icon, String classes) {
        if (icon.kind() == Kind.ITEM) {
            Element item = Dom.el(doc, "item", classes, null);
            item.setAttribute("id", icon.id());
            return item;
        }
        Element img = Dom.el(doc, "img", classes, null);
        img.setAttribute("src", icon.src());
        return img;
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
