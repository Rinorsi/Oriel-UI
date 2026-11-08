package dev.oriel.mod.internal.client;

import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.platform.Services;
import java.nio.file.Path;

/** The user's editable folder is available in every distribution, independently of templates. */
public final class MyPages {
    public static final String NAMESPACE = "oriel_mine";
    private static Path directory;

    private MyPages() {}

    public static void init() {
        directory = Services.PLATFORM.configDir().resolve("oriel/my-pages");
        try {
            OrielPages.folder(NAMESPACE, directory);
        } catch (IllegalArgumentException e) {
            Constants.LOG.warn("Oriel: my pages are off: {}", e.getMessage());
            directory = null;
        }
    }

    static Path directory() { return directory; }
}
