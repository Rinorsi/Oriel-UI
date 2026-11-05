package dev.oriel.engine.host;

import dev.oriel.engine.dom.Document;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;

/** Shared file-save policy; a mixed batch always takes the full page lifecycle. */
public final class HotReload {
    private HotReload() {}

    public static boolean cssOnly(Collection<Path> changed) {
        return !changed.isEmpty() && changed.stream().allMatch(p ->
                p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css"));
    }

    /** Keeps the live document for CSS; the host owns full reload and controller state handoff. */
    public static void apply(Document document, Collection<Path> changed, Runnable fullReload) {
        if (cssOnly(changed) && document != null && document.error() == null) document.reloadStyles();
        else fullReload.run();
    }
}
