package dev.oriel.kit;

import dev.oriel.kit.preset.CatalogPreset;
import dev.oriel.kit.preset.EditorPreset;
import dev.oriel.kit.preset.ForgePreset;
import dev.oriel.kit.preset.IconsPreset;
import dev.oriel.kit.preset.GalleryPreset;
import dev.oriel.kit.preset.InventoryPreset;
import dev.oriel.kit.preset.ItemEditorPreset;
import dev.oriel.kit.preset.MusicPreset;
import dev.oriel.kit.preset.InventoryEffectsPreset;
import dev.oriel.kit.preset.PotionHudPreset;
import dev.oriel.kit.preset.SettingsPreset;
import dev.oriel.kit.preset.StylesPreset;
import dev.oriel.engine.host.PageControllers;

import java.util.List;

/**
 * The Ore theme and the preset screens built on it. The pages are plain HTML and CSS under
 * {@code assets/oriel/ui/}; each preset's behaviour is a Java controller over fixture data, with no page scripts.
 */
public final class OrielKit {
    public static final String NAMESPACE = "oriel";
    public static final String GALLERY = page("gallery");
    public static final String INVENTORY = page("inventory");
    public static final String FORGE = page("forge");
    public static final String MUSIC = page("music");
    public static final String SETTINGS = page("settings");
    public static final String POTIONS = page("potions");
    /** The player's effects beside vanilla's inventories (the mod shows it as an overlay there). */
    public static final String INVENTORY_EFFECTS = page("inventory_effects");
    public static final String EDITOR = page("editor");
    public static final String CATALOG = page("catalog");
    public static final String ICONS = page("icons");
    /** The style showcase's index; its looks are {@code styles/<name>} ({@link StylesPreset#PAGES}). */
    public static final String STYLES = page("styles");
    /** The item data editor, opened by the server ({@code /oriel edit}). */
    public static final String ITEM_EDITOR = page("item_editor");
    /** The presets, in gallery order. */
    public static final List<String> PRESETS = List.of(INVENTORY, FORGE, MUSIC, SETTINGS, POTIONS, EDITOR, CATALOG);
    /** The message channel pages send host commands on ({@code Host.send}); the value is the command. */
    public static final String CHANNEL = "oriel";

    private static boolean registered;

    private OrielKit() {}

    public static String page(String name) {
        return NAMESPACE + ":ui/" + name + ".html";
    }

    /** Registers the presets' controllers; safe to call more than once. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        Lang.register();
        PageControllers.register(GALLERY, GalleryPreset::new);
        PageControllers.register(INVENTORY, InventoryPreset::new);
        PageControllers.register(FORGE, ForgePreset::new);
        PageControllers.register(MUSIC, MusicPreset::new);
        PageControllers.register(SETTINGS, SettingsPreset::new);
        PageControllers.register(POTIONS, PotionHudPreset::new);
        PageControllers.register(INVENTORY_EFFECTS, InventoryEffectsPreset::new);
        PageControllers.register(EDITOR, EditorPreset::new);
        PageControllers.register(CATALOG, CatalogPreset::new);
        PageControllers.register(ICONS, IconsPreset::new);
        PageControllers.register(STYLES, () -> new StylesPreset(true));
        for (String look : StylesPreset.PAGES) PageControllers.register(page("styles/" + look), () -> new StylesPreset(false));
        PageControllers.register(ITEM_EDITOR, ItemEditorPreset::new);
    }
}
