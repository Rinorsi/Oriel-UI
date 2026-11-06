package dev.oriel.mod.client;

import java.nio.file.Path;

/**
 * Pages that live in a folder the player can edit, such as one under {@code config/}, instead of in your mod's jar. Register the
 * folder once at client setup under a namespace of its own; the page {@code ns:hud.html} is then the file {@code hud.html} in that
 * folder, its relative stylesheets, scripts and PNG images resolve next to it, and saving any file a page read reloads the page,
 * in every environment (not only a development run).
 *
 * <pre>{@code
 * OrielPages.folder("hudsmith_hud", configDir.resolve("hudsmith"));
 * OrielHud.register(HUD, "hudsmith_hud:hud.html");
 * }</pre>
 *
 * <p>The folder owns its namespace outright: an id in it never falls back to resource packs. Pick a namespace of its own, not your
 * mod id, so your bundled pages keep coming from your jar. Pages only read files inside the folder, and pages a server opens cannot
 * read them. See {@code docs/zh/pages.md}. (To hot-reload your <em>bundled</em> pages while developing, see {@code docs/zh/hot-reload.md}.)
 */
public final class OrielPages {
    private OrielPages() {}

    /**
     * Serves the namespace {@code namespace} from {@code dir}, which is created if missing. Call it once, at client setup.
     *
     * @throws IllegalArgumentException if the namespace is not a valid identifier namespace, is already registered, or is
     *     {@code minecraft}, {@code oriel} or a loaded mod's id; or if the folder cannot be used
     */
    public static void folder(String namespace, Path dir) {
        dev.oriel.mod.internal.client.OrielPages.folder(namespace, dir);
    }

    /** Whether {@code namespace} is served from a folder registered with {@link #folder}. */
    public static boolean isFolder(String namespace) {
        return dev.oriel.mod.internal.client.OrielPages.isFolder(namespace);
    }
}
