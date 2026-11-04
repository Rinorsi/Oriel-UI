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
            {"minecraft:hud/heart/full", "生命", "heart health hp 心"}, {"minecraft:hud/heart/half", "半颗心", "heart half"},
            {"minecraft:hud/heart/container", "心槽", "heart empty"}, {"minecraft:hud/heart/poisoned_full", "中毒的心", "heart poison"},
            {"minecraft:hud/heart/withered_full", "凋零的心", "heart wither"}, {"minecraft:hud/heart/frozen_full", "冰冻的心", "heart frozen"},
            {"minecraft:hud/heart/absorbing_full", "伤害吸收", "heart absorption gold"},
            {"minecraft:hud/food_full", "饱食度", "food hunger 鸡腿"}, {"minecraft:hud/food_half", "半格饱食度", "food half"},
            {"minecraft:hud/food_empty", "饱食度槽", "food empty"}, {"minecraft:hud/food_full_hunger", "饥饿", "food hunger effect"},
            {"minecraft:hud/armor_full", "护甲", "armor armour"}, {"minecraft:hud/armor_half", "半格护甲", "armor half"},
            {"minecraft:hud/armor_empty", "护甲槽", "armor empty"}, {"minecraft:hud/air", "氧气", "air bubble oxygen"},
            {"minecraft:hud/air_bursting", "氧气破裂", "air bubble"}, {"minecraft:hud/crosshair", "准星", "crosshair aim"},
            {"minecraft:statistics/block_mined", "挖掘", "stat mined"}, {"minecraft:statistics/item_crafted", "合成", "stat crafted"},
            {"minecraft:statistics/item_used", "使用", "stat used"}, {"minecraft:statistics/item_broken", "损坏", "stat broken"},
            {"minecraft:statistics/item_picked_up", "拾取", "stat picked"}, {"minecraft:statistics/item_dropped", "丢弃", "stat dropped"},
            {"minecraft:icon/checkmark", "对勾", "check ok"}, {"minecraft:icon/info", "信息", "info"},
            {"minecraft:icon/language", "语言", "language"}, {"minecraft:icon/accessibility", "无障碍", "accessibility"},
            {"minecraft:icon/search", "搜索", "search"}, {"minecraft:icon/link", "链接", "link"},
            {"minecraft:icon/music_notes", "音乐", "music"}, {"minecraft:icon/news", "新闻", "news"},
            {"minecraft:icon/ping_5", "信号满格", "ping signal"}, {"minecraft:icon/ping_2", "信号弱", "ping signal"},
    };
    /** A selection of items; any other item id typed in the search shows too. */
    static final String[][] ITEM_ICONS = {
            {"minecraft:diamond_sword", "钻石剑"}, {"minecraft:iron_pickaxe", "铁镐"}, {"minecraft:bow", "弓"},
            {"minecraft:shield", "盾牌"}, {"minecraft:trident", "三叉戟"}, {"minecraft:diamond", "钻石"},
            {"minecraft:emerald", "绿宝石"}, {"minecraft:gold_ingot", "金锭"}, {"minecraft:redstone", "红石"},
            {"minecraft:lapis_lazuli", "青金石"}, {"minecraft:nether_star", "下界之星"}, {"minecraft:heart_of_the_sea", "海洋之心"},
            {"minecraft:apple", "苹果"}, {"minecraft:golden_apple", "金苹果"}, {"minecraft:bread", "面包"},
            {"minecraft:cooked_beef", "牛排"}, {"minecraft:cake", "蛋糕"}, {"minecraft:totem_of_undying", "不死图腾"},
            {"minecraft:ender_pearl", "末影珍珠"}, {"minecraft:experience_bottle", "附魔之瓶"}, {"minecraft:blaze_rod", "烈焰棒"},
            {"minecraft:book", "书"}, {"minecraft:enchanted_book", "附魔书"}, {"minecraft:compass", "指南针"},
            {"minecraft:clock", "时钟"}, {"minecraft:map", "地图"}, {"minecraft:elytra", "鞘翅"},
            {"minecraft:grass_block", "草方块"}, {"minecraft:oak_log", "橡木原木"}, {"minecraft:chest", "箱子"},
            {"minecraft:crafting_table", "工作台"}, {"minecraft:tnt", "TNT"},
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
        categories.add(new String[] {VANILLA, "原版图标"});
        for (String[] s : SPRITES) icons.add(new Icon(s[0], s[1], VANILLA, List.of(s[2].split(" ")), Kind.SPRITE));
        categories.add(new String[] {ITEMS, "任意物品"});
        for (String[] s : ITEM_ICONS) icons.add(new Icon(s[0], s[1], ITEMS, List.of(s[0].substring(s[0].indexOf(':') + 1)), Kind.ITEM));
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
        Dom.text(Dom.byId(doc, "total"), library + " 个图标 · 16×16 像素 · 用 -mc-tint 着色 · 也可以用原版图标和任意物品");
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
            Dom.byId(doc, "q").setAttribute("placeholder", id.equals(ITEMS) ? "物品名称或 id，如 minecraft:totem_of_undying"
                    : "搜索名称、拼写或标签");
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
