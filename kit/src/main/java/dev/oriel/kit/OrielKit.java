package dev.oriel.kit;

import dev.oriel.kit.preset.GalleryPreset;
import dev.oriel.kit.preset.InventoryPreset;
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
    /** The presets, in gallery order. */
    public static final List<String> PRESETS = List.of(INVENTORY, FORGE, MUSIC, SETTINGS, POTIONS);
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
        PageControllers.register(GALLERY, GalleryPreset::new);
        PageControllers.register(INVENTORY, InventoryPreset::new);
    }
}
