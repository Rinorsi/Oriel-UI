package dev.oriel.mod.internal;

import java.util.List;

/**
 * The sub-commands of {@code /oriel} that only the client runs. Both sides need the names: the client registers them,
 * and the server lists them too (as stubs), because a client command that shares its root with a server command is
 * dropped from the tree the client highlights and completes with, which would show a working command in red.
 * No client classes here, so a dedicated server can load it.
 */
public final class CommandNames {
    /** The client-side demo pages, under {@code assets/oriel/oriel/demo/} (the gallery is {@code index.html}). */
    public static final List<String> DEMOS = List.of("settings", "layout", "animation", "templates", "hud");
    /** The words under {@code /oriel showcase} that open one of those pages: {@code livedata} is {@code templates}, {@code forms} is {@code settings}, {@code titlecard} is {@code hud}. */
    public static final List<String> BASICS = List.of("layout", "animation", "livedata", "forms", "titlecard");
    /** Demos run by the server ({@code OrielCommand}); the client forwards them itself. */
    public static final List<String> SERVER_DEMOS = List.of("chest", "live");
    /** The show-off pages under {@code assets/oriel/oriel/showcase/}; the same pages are cards in the template centre's Demos view. */
    public static final List<String> SHOWCASE = List.of("title", "hud", "shop", "mobdex", "journal", "console", "models", "scene");
    /** Kit presets under {@code /oriel showcase}: a page each, and {@code potions}, which toggles the potion HUD overlay. */
    public static final List<String> PRESETS = List.of("inventory", "forge", "music", "settings", "potions", "editor", "catalog", "icons", "templates");
    /** The demo overlay that {@code /oriel showcase toast} toggles. */
    public static final String TOAST = "toast";

    private CommandNames() {}
}
