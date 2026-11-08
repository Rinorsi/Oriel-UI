package dev.oriel.mod.internal.nested;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure comparison and player-facing data. Never resolves the winner itself: the loader does that. */
public final class NestedCheck {
    private static final List<Pattern> IGNORED = ignored();
    private NestedCheck() {}

    public record Copy(String source, String parent, String version, @Nullable NestedManifest manifest) {
        public String coreHash() { return manifest == null ? "legacy" : manifest.coreHash(); }
        public String label() { return parent + " (Oriel UI " + version + ")"; }
    }
    public record Report(Copy active, List<Copy> copies, Map<String, List<String>> differences) {
        public boolean risk() { return !differences.isEmpty(); }
        public String summary() {
            return copies.stream().map(Copy::label).reduce((a, b) -> a + "; " + b).orElse("none") + "; active=" + active.label();
        }
        public String parents() { return copies.stream().map(Copy::label).distinct().reduce((a, b) -> a + ", " + b).orElse(""); }
    }

    public static Report compare(Copy active, List<Copy> copies) {
        Map<String, List<String>> differences = new LinkedHashMap<>();
        for (Copy copy : copies) {
            if (copy.source().equals(active.source())) continue;
            List<String> changed = new ArrayList<>();
            var old = copy.manifest();
            var current = active.manifest();
            if (old == null || current == null) {
                if (!copy.version().equals(active.version())) changed.add("version-only (legacy copy without manifest)");
            } else {
                if (!old.apiHash().equals(current.apiHash())) changed.add("apiHash");
                old.entries().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    // Class packages contain only core classes, filtered before aggregation at build time.
                    if (!ignored(entry.getKey()) && !entry.getValue().equals(current.entries().get(entry.getKey()))) changed.add(entry.getKey());
                });
            }
            if (!changed.isEmpty()) differences.put(copy.source(), List.copyOf(changed));
        }
        return new Report(active, List.copyOf(copies), Map.copyOf(differences));
    }

    public static String fingerprint(List<String> installed, List<Copy> copies) {
        var identities = copies.stream().map(c -> List.of(c.source(), c.parent(), c.version(), c.coreHash()))
                .sorted(Comparator.comparing(c -> new Gson().toJson(c))).toList();
        return digest(new Gson().toJson(List.of(installed.stream().sorted().toList(), identities)));
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static boolean ignored(String path) {
        // An aggregate is already filtered; applying a class-only regex to '*.class' would hide core presets.
        if (path.endsWith("/*.class")) return false;
        return IGNORED.stream().anyMatch(p -> p.matcher(path).find());
    }

    private static List<Pattern> ignored() {
        try (var stream = NestedCheck.class.getResourceAsStream("/META-INF/oriel/boundaries.json")) {
            if (stream == null) throw new IllegalStateException("Missing Oriel release boundaries");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Pattern> result = new ArrayList<>();
            for (String group : List.of("reference", "development", "metadata"))
                for (var value : root.getAsJsonArray(group)) result.add(Pattern.compile(value.getAsString()));
            return List.copyOf(result);
        } catch (IOException e) { throw new IllegalStateException("Cannot read Oriel release boundaries", e); }
    }
}
