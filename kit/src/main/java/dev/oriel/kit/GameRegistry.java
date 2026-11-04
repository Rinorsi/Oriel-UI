package dev.oriel.kit;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The game's items, as pickers show them: every registered item (vanilla and every mod's), with the name the game
 * shows in its current language. The mod provides the real registry at client start ({@link #provide}); without a
 * game (the previewer, tests) a small built-in list stands in.
 */
public final class GameRegistry {
    /** One item: its id, its display name, and whether it places a block (so it can be shown as a 3D block). */
    public record Entry(String id, String name, boolean block) {
        /** The id's namespace: {@code minecraft}, or the mod's. */
        public String namespace() {
            int colon = id.indexOf(':');
            return colon < 0 ? "minecraft" : id.substring(0, colon);
        }

        /** Whether {@code query} (lower case) is in the name or the id. */
        public boolean matches(String query) {
            return query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query) || id.contains(query);
        }
    }

    private static volatile Supplier<List<Entry>> source = GameRegistry::fixture;
    private static volatile List<Entry> cached;
    private static volatile Map<String, Entry> byId = Map.of();

    private GameRegistry() {}

    /** Sets where the entries come from (the mod: the item registry, named in the game's language). */
    public static void provide(Supplier<List<Entry>> entries) {
        source = entries;
        cached = null;
    }

    /** Every entry, read again from the source (names follow a language change). */
    public static List<Entry> refresh() {
        List<Entry> all = List.copyOf(source.get());
        Map<String, Entry> map = new HashMap<>();
        for (Entry e : all) map.put(e.id(), e);
        byId = map;
        cached = all;
        return all;
    }

    /** Every entry (read once, then kept until {@link #refresh}). */
    public static List<Entry> entries() {
        List<Entry> all = cached;
        return all != null ? all : refresh();
    }

    /** Whether {@code id} places a block (true when the game does not know it: a block id written by hand). */
    public static boolean isBlock(String id) {
        if (cached == null) entries();
        Entry e = byId.get(id);
        return e == null || e.block();
    }

    /** The display name for {@code id}, or the id itself when the game does not know it. */
    public static String name(String id) {
        if (cached == null) entries();
        Entry e = byId.get(id);
        return e == null ? id : e.name();
    }

    private static List<Entry> fixture() {
        String[][] items = {
                {"minecraft:diamond", "钻石", "0"}, {"minecraft:emerald", "绿宝石", "0"}, {"minecraft:gold_ingot", "金锭", "0"},
                {"minecraft:iron_ingot", "铁锭", "0"}, {"minecraft:netherite_ingot", "下界合金锭", "0"},
                {"minecraft:diamond_sword", "钻石剑", "0"}, {"minecraft:netherite_sword", "下界合金剑", "0"},
                {"minecraft:bow", "弓", "0"}, {"minecraft:trident", "三叉戟", "0"}, {"minecraft:shield", "盾牌", "0"},
                {"minecraft:diamond_pickaxe", "钻石镐", "0"}, {"minecraft:elytra", "鞘翅", "0"},
                {"minecraft:golden_apple", "金苹果", "0"}, {"minecraft:bread", "面包", "0"}, {"minecraft:cake", "蛋糕", "1"},
                {"minecraft:enchanted_book", "附魔书", "0"}, {"minecraft:experience_bottle", "附魔之瓶", "0"},
                {"minecraft:ender_pearl", "末影珍珠", "0"}, {"minecraft:nether_star", "下界之星", "0"},
                {"minecraft:totem_of_undying", "不死图腾", "0"}, {"minecraft:compass", "指南针", "0"}, {"minecraft:clock", "时钟", "0"},
                {"minecraft:grass_block", "草方块", "1"}, {"minecraft:dirt", "泥土", "1"}, {"minecraft:stone", "石头", "1"},
                {"minecraft:oak_log", "橡木原木", "1"}, {"minecraft:oak_planks", "橡木木板", "1"}, {"minecraft:glass", "玻璃", "1"},
                {"minecraft:crafting_table", "工作台", "1"}, {"minecraft:furnace", "熔炉", "1"}, {"minecraft:chest", "箱子", "1"},
                {"minecraft:anvil", "铁砧", "1"}, {"minecraft:enchanting_table", "附魔台", "1"}, {"minecraft:beacon", "信标", "1"},
                {"minecraft:diamond_block", "钻石块", "1"}, {"minecraft:tnt", "TNT", "1"}, {"minecraft:bookshelf", "书架", "1"},
                {"minecraft:lantern", "灯笼", "1"}, {"minecraft:jukebox", "唱片机", "1"}, {"minecraft:note_block", "音符盒", "1"},
        };
        return java.util.Arrays.stream(items).map(i -> new Entry(i[0], i[1], i[2].equals("1"))).toList();
    }
}
