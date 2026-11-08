package dev.oriel.mod.internal.nested;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Startup coordinator shared by both physical sides, with no Minecraft or client references. */
public final class NestedRuntime {
    private static final Logger LOG = LoggerFactory.getLogger("Oriel/Nested");
    private static NestedCache cache;
    private static NestedCheck.Report report;
    private NestedRuntime() {}

    public static void start(Path config, List<String> installed, List<Path> archives, String selected, NestedCache.Scan scan) {
        try {
            List<String> stamps = new ArrayList<>(installed);
            stamps.add("selected=" + selected);
            for (Path archive : archives) stamps.add(archive.toAbsolutePath() + ":" + Files.size(archive) + ":" + Files.getLastModifiedTime(archive));
            // The selected build also invalidates a cache after changing code in a development directory.
            try (var stream = NestedRuntime.class.getResourceAsStream("/" + NestedManifest.PATH)) {
                if (stream != null) stamps.add(NestedCheck.digest(new String(NestedArchive.bounded(stream, NestedManifest.MAX_BYTES), StandardCharsets.UTF_8)));
            }
            Path file = config.resolve("oriel/nested-check.json");
            try { cache = new NestedCache(file); }
            catch (IOException e) {
                LOG.warn("Oriel nested cache cannot be read; rebuilding: {}", e.toString());
                Files.move(file, file.resolveSibling("nested-check.invalid.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                cache = new NestedCache(file);
            }
            var state = cache.check(NestedCheck.digest(String.join("\n", stamps.stream().sorted().toList())), installed, scan);
            report = state.report();
            LOG.info("Oriel nested summary: {}", report.summary());
            if (report.risk()) {
                List<String> affected = new ArrayList<>();
                affected.add(report.active().label());
                for (var copy : report.copies()) if (report.differences().containsKey(copy.source())) affected.add(copy.label());
                LOG.warn("Oriel nested risk: {}; UI styles may differ; gameplay is unaffected", String.join("; ", affected));
                report.differences().forEach((source, differences) -> LOG.debug("Oriel nested differences {}: {}", source, differences));
            }
        } catch (IOException | RuntimeException e) { LOG.warn("Oriel nested check failed: {}", e.toString(), e); }
    }

    public static NestedCheck.Report notice(boolean enabled) {
        return cache != null && cache.shouldNotice(enabled) ? report : null;
    }

    public static void noticed() {
        if (cache == null) return;
        try { cache.markNoticed(); }
        catch (IOException e) { LOG.warn("Oriel nested notice could not be saved: {}", e.toString()); }
    }
}
