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
    public static final List<String> DEMOS = List.of("gallery", "settings", "layout", "animation", "templates", "hud");
    /** Demos run by the server ({@code OrielCommand}); the client forwards them itself. */
    public static final List<String> SERVER_DEMOS = List.of("chest", "live");
    /** The show-off pages under {@code assets/oriel/oriel/showcase/}. */
    public static final List<String> SHOWCASE = List.of("title", "hud", "shop", "mobdex", "journal", "console", "models", "scene");
    /** The presets {@code /oriel demo} opens: a page each, and the potion HUD. */
    public static final List<String> PRESETS = List.of("inventory", "forge", "music", "settings", "hud", "editor", "catalog", "icons");

    private CommandNames() {}
}
