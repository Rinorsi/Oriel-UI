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
    private static volatile Supplier<List<Entry>> entitySource = GameRegistry::entityFixture;
    private static volatile List<Entry> entities;
    private static volatile Map<String, Entry> entityById = Map.of();
    private static volatile List<Entry> cached;
    private static volatile Map<String, Entry> byId = Map.of();

    private GameRegistry() {}

    /** Sets where the entries come from (the mod: the item registry, named in the game's language). */
    public static void provide(Supplier<List<Entry>> entries) {
        source = entries;
        cached = null;
    }

    /** Sets where the entity types come from (the mod: the entity registry, named in the game's language). */
    public static void provideEntities(Supplier<List<Entry>> types) {
        entitySource = types;
        entities = null;
    }

    /** Every entity type that can be shown (vanilla and modded), read again from the source. */
    public static List<Entry> refreshEntities() {
        List<Entry> all = List.copyOf(entitySource.get());
        Map<String, Entry> map = new HashMap<>();
        for (Entry e : all) map.put(e.id(), e);
        entityById = map;
        entities = all;
        return all;
    }

    public static List<Entry> entities() {
        List<Entry> all = entities;
        return all != null ? all : refreshEntities();
    }

    /** The display name of entity type {@code id}, or the id. */
    public static String entityName(String id) {
        if (entities == null) entities();
        Entry e = entityById.get(id);
        return e == null ? id : e.name();
    }

    /** The spawn egg item for entity type {@code id} when the game has one (its icon in pickers), else null. */
    public static String spawnEgg(String id) {
        String egg = id + "_spawn_egg";
        if (cached == null) entries();
        return byId.containsKey(egg) ? egg : null;
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

    public static List<Entry> entries(dev.oriel.engine.dom.Document doc) {
        return entries().stream().map(e -> new Entry(e.id(), Lang.value(doc, e.name()), e.block())).toList();
    }

    public static List<Entry> entities(dev.oriel.engine.dom.Document doc) {
        return entities().stream().map(e -> new Entry(e.id(), Lang.value(doc, e.name()), e.block())).toList();
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

    /** Display names for both real registries and the language-independent preview fixtures. */
    public static String name(dev.oriel.engine.dom.Document doc, String id) { return Lang.value(doc, name(id)); }

    public static String entityName(dev.oriel.engine.dom.Document doc, String id) { return Lang.value(doc, entityName(id)); }

    private static List<Entry> entityFixture() {
        String[][] types = {
                {"minecraft:pig", "oriel.ui.gameregistry.pig"}, {"minecraft:cow", "oriel.ui.gameregistry.cow"}, {"minecraft:sheep", "oriel.ui.gameregistry.sheep"}, {"minecraft:chicken", "oriel.ui.gameregistry.chicken"},
                {"minecraft:fox", "oriel.ui.gameregistry.fox"}, {"minecraft:wolf", "oriel.ui.gameregistry.wolf"}, {"minecraft:cat", "oriel.ui.gameregistry.cat"}, {"minecraft:axolotl", "oriel.ui.gameregistry.axolotl"},
                {"minecraft:bee", "oriel.ui.gameregistry.bee"}, {"minecraft:allay", "oriel.ui.gameregistry.allay"}, {"minecraft:villager", "oriel.ui.gameregistry.villager"}, {"minecraft:iron_golem", "oriel.ui.gameregistry.iron_golem"},
                {"minecraft:zombie", "oriel.ui.gameregistry.zombie"}, {"minecraft:skeleton", "oriel.ui.gameregistry.skeleton"}, {"minecraft:creeper", "oriel.ui.gameregistry.creeper"}, {"minecraft:enderman", "oriel.ui.gameregistry.enderman"},
        };
        return java.util.Arrays.stream(types).map(t -> new Entry(t[0], t[1], false)).toList();
    }

    private static List<Entry> fixture() {
        String[][] items = {
                {"minecraft:diamond", "oriel.ui.gameregistry.diamond", "0"}, {"minecraft:emerald", "oriel.ui.gameregistry.emerald", "0"}, {"minecraft:gold_ingot", "oriel.ui.gameregistry.gold_ingot", "0"},
                {"minecraft:iron_ingot", "oriel.ui.gameregistry.iron_ingot", "0"}, {"minecraft:netherite_ingot", "oriel.ui.gameregistry.netherite_ingot", "0"},
                {"minecraft:diamond_sword", "oriel.ui.gameregistry.diamond_sword", "0"}, {"minecraft:netherite_sword", "oriel.ui.gameregistry.netherite_sword", "0"},
                {"minecraft:bow", "oriel.ui.gameregistry.bow", "0"}, {"minecraft:trident", "oriel.ui.gameregistry.trident", "0"}, {"minecraft:shield", "oriel.ui.gameregistry.shield", "0"},
                {"minecraft:diamond_pickaxe", "oriel.ui.gameregistry.diamond_pickaxe", "0"}, {"minecraft:elytra", "oriel.ui.gameregistry.elytra", "0"},
                {"minecraft:golden_apple", "oriel.ui.gameregistry.golden_apple", "0"}, {"minecraft:bread", "oriel.ui.gameregistry.bread", "0"}, {"minecraft:cake", "oriel.ui.gameregistry.cake", "1"},
                {"minecraft:enchanted_book", "oriel.ui.gameregistry.enchanted_book", "0"}, {"minecraft:experience_bottle", "oriel.ui.gameregistry.bottle_o_enchanting", "0"},
                {"minecraft:ender_pearl", "oriel.ui.gameregistry.ender_pearl", "0"}, {"minecraft:nether_star", "oriel.ui.gameregistry.nether_star", "0"},
                {"minecraft:totem_of_undying", "oriel.ui.gameregistry.totem_of_undying", "0"}, {"minecraft:compass", "oriel.ui.gameregistry.compass", "0"}, {"minecraft:clock", "oriel.ui.gameregistry.clock", "0"},
                {"minecraft:grass_block", "oriel.ui.gameregistry.grass_block", "1"}, {"minecraft:dirt", "oriel.ui.gameregistry.dirt", "1"}, {"minecraft:stone", "oriel.ui.gameregistry.stone", "1"},
                {"minecraft:oak_log", "oriel.ui.gameregistry.oak_log", "1"}, {"minecraft:oak_planks", "oriel.ui.gameregistry.oak_planks", "1"}, {"minecraft:glass", "oriel.ui.gameregistry.glass", "1"},
                {"minecraft:crafting_table", "oriel.ui.gameregistry.crafting_table", "1"}, {"minecraft:furnace", "oriel.ui.gameregistry.furnace", "1"}, {"minecraft:chest", "oriel.ui.gameregistry.chest", "1"},
                {"minecraft:anvil", "oriel.ui.gameregistry.anvil", "1"}, {"minecraft:enchanting_table", "oriel.ui.gameregistry.enchanting_table", "1"}, {"minecraft:beacon", "oriel.ui.gameregistry.beacon", "1"},
                {"minecraft:diamond_block", "oriel.ui.gameregistry.block_of_diamond", "1"}, {"minecraft:tnt", "oriel.ui.common.tnt", "1"}, {"minecraft:bookshelf", "oriel.ui.gameregistry.bookshelf", "1"},
                {"minecraft:lantern", "oriel.ui.gameregistry.lantern", "1"}, {"minecraft:jukebox", "oriel.ui.gameregistry.jukebox", "1"}, {"minecraft:note_block", "oriel.ui.gameregistry.note_block", "1"},
                {"minecraft:pig_spawn_egg", "oriel.ui.gameregistry.pig_spawn_egg", "0"}, {"minecraft:fox_spawn_egg", "oriel.ui.gameregistry.fox_spawn_egg", "0"}, {"minecraft:creeper_spawn_egg", "oriel.ui.gameregistry.creeper_spawn_egg", "0"},
        };
        return java.util.Arrays.stream(items).map(i -> new Entry(i[0], i[1], i[2].equals("1"))).toList();
    }
}
