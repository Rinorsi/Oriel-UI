package dev.oriel.kit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

/** Packaged template identities. Numbered entries keep their original numbers, including reference 44. */
public final class TemplateCatalog {
    private static final String ROOT = "ui/templates/";
    private static final List<JsonObject> ENTRIES = load();

    private TemplateCatalog() {}

    private static List<JsonObject> load() {
        try (InputStream in = TemplateCatalog.class.getResourceAsStream("/assets/oriel/" + ROOT + "catalog.json")) {
            if (in == null) throw new IOException("Missing template catalog");
            List<JsonObject> entries = new ArrayList<>();
            for (var value : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                entries.add(value.getAsJsonObject());
            }
            return List.copyOf(entries);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load official templates", e);
        }
    }

    public static List<String> ids() {
        return ENTRIES.stream().map(e -> e.get("id").getAsString()).toList();
    }

    private static JsonObject entry(String id) {
        return ENTRIES.stream().filter(e -> e.get("id").getAsString().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + id));
    }

    public static boolean numbered(String id) {
        return entry(id).get("source").getAsString().equals("official");
    }

    public static String page(String id) {
        return "oriel:" + ROOT + entry(id).get("entry").getAsString();
    }

    public static String cover(String id) {
        return entry(id).get("cover").getAsString();
    }

    public static String savedPath(String id) {
        return "templates/" + entry(id).get("entry").getAsString();
    }

    /** Loads display metadata from the template itself, using the current host resources. */
    public static JsonObject metadata(String id, Function<String, String> readText) {
        String page = entry(id).get("entry").getAsString();
        String url = "oriel:" + ROOT + page.substring(0, page.lastIndexOf('/') + 1) + "template.json";
        String json = readText.apply(url);
        if (json == null) throw new IllegalStateException("Missing template metadata: " + url);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    public record Text(String name, String description, String category, List<String> tags, String status) {}

    private static String normalize(String language) {
        return language == null ? "" : language.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** Each nonempty field uses current locale, English, Chinese, then other available locales. */
    public static Text localized(JsonObject metadata, String language) {
        Map<String, JsonObject> available = new LinkedHashMap<>();
        if (metadata.has("locales") && metadata.get("locales").isJsonObject()) {
            metadata.getAsJsonObject("locales").entrySet().forEach(e -> {
                if (e.getValue().isJsonObject()) available.put(normalize(e.getKey()), e.getValue().getAsJsonObject());
            });
        }
        LinkedHashSet<String> order = new LinkedHashSet<>();
        order.add(normalize(language));
        order.add("en_us"); order.add("en");
        available.keySet().stream().filter(k -> k.startsWith("en_")).sorted().forEach(order::add);
        order.add("zh_cn"); order.add("zh");
        available.keySet().stream().filter(k -> k.startsWith("zh_")).sorted().forEach(order::add);
        available.keySet().stream().sorted().forEach(order::add);
        List<JsonObject> choices = order.stream().filter(available::containsKey).map(available::get).toList();
        String name = field(choices, "name"), category = field(choices, "category");
        if (name.isEmpty()) name = metadata.has("id") ? metadata.get("id").getAsString() : "";
        List<String> tags = List.of();
        for (JsonObject choice : choices) if (choice.has("tags") && choice.get("tags").isJsonArray()) {
            List<String> candidate = new ArrayList<>();
            for (var value : choice.getAsJsonArray("tags")) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank()) candidate.add(value.getAsString());
            }
            if (!candidate.isEmpty()) { tags = List.copyOf(candidate); break; }
        }
        if (tags.isEmpty() && !category.isEmpty()) tags = List.of(category);
        return new Text(name, field(choices, "description"), category, tags, field(choices, "status"));
    }

    private static String field(List<JsonObject> choices, String field) {
        for (JsonObject choice : choices) {
            var value = choice.get(field);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && !value.getAsString().isBlank()) return value.getAsString();
        }
        return "";
    }

    @FunctionalInterface
    public interface Resources {
        byte[] read(String resource) throws IOException;
    }

    /** Copy a complete numbered page without replacing a user's edits or exposing a partially written page. */
    public static void saveNumbered(Path folder, String id, Resources resources) throws IOException {
        if (!numbered(id)) throw new IllegalArgumentException("Not a numbered template: " + id);
        saveCopy(folder, id, resources);
    }

    /** New copies of legacy templates also keep their metadata beside the page. */
    public static void saveCopy(Path folder, String id, Resources resources) throws IOException {
        String relative = entry(id).get("entry").getAsString();
        String directory = relative.substring(0, relative.lastIndexOf('/'));
        Files.createDirectories(folder);
        Path root = folder.toRealPath(), parent = root;
        String targetRelative = "templates/" + directory;
        Path target = root.resolve(targetRelative);
        for (Path part : root.relativize(target.getParent())) {
            parent = parent.resolve(part);
            if (Files.isSymbolicLink(parent)) throw new IOException("Linked template directory: " + parent);
            Files.createDirectories(parent);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        String prefix = ROOT + directory + "/";
        var list = JsonParser.parseString(new String(resources.read(prefix + "files.json"), StandardCharsets.UTF_8)).getAsJsonArray();
        Path staging = Files.createTempDirectory(root, ".template-");
        try {
            for (var value : list) {
                String name = value.getAsString();
                if (name.isBlank() || name.contains(":") || name.contains("\\") || name.startsWith("/")
                        || List.of(name.split("/")).contains("..")) throw new IOException("Invalid template path: " + name);
                Path to = staging.resolve(name).normalize();
                if (!to.startsWith(staging) || to.equals(staging)) throw new IOException("Invalid template path: " + name);
                Files.createDirectories(to.getParent());
                Files.write(to, resources.read(prefix + name));
            }
            if (!Files.isRegularFile(staging.resolve("index.html"))) throw new IOException("Template has no entry page");
            // No REPLACE_EXISTING: a concurrent copy must not replace an existing page directory.
            Files.move(staging, target);
        } finally {
            if (Files.exists(staging)) try (var walk = Files.walk(staging)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
