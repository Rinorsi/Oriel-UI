package dev.oriel.kit;

import dev.oriel.kit.preset.AboutPreset;
import dev.oriel.kit.preset.ModSettingsPreset;
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
    /** The effects panel meant to sit beside a vanilla inventory: a design for a mod that places it; Oriel itself does not. */
    public static final String INVENTORY_EFFECTS = page("inventory_effects");
    public static final String EDITOR = page("editor");
    public static final String CATALOG = page("catalog");
    public static final String ICONS = page("icons");
    /** The style showcase's index; its looks are {@code styles/<name>} ({@link dev.oriel.kit.preset.StylesPreset#PAGES}). */
    public static final String STYLES = page("styles");
    /** The mod's own settings (the game keeps them: see ModSettingsPreset) and its information page. */
    public static final String MODSETTINGS = page("modsettings");
    public static final String ABOUT = page("about");
    /** The repair forge's container page: the server's forge menu ({@code oriel:forge}) shows it. */
    public static final String FORGE_STATION = page("forge_station");
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
        PageControllers.registerShared(() -> Shell::setup);
        PageControllers.register(MODSETTINGS, ModSettingsPreset::new);
        PageControllers.register(ABOUT, AboutPreset::new);
        PageControllers.register(page("nested-notice"), dev.oriel.kit.preset.NestedNoticePreset::new);
        if (ReferenceContent.available()) ReferenceKit.register();
    }
}
