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

/** Packaged template identities. The numbers are folder names only; the library shows names, never numbers. */
public final class TemplateCatalog {
    private static final String ROOT = "ui/templates/";
    private static final List<JsonObject> ENTRIES = load();

    private static final Map<String, String> AVATARS = loadAvatars();

    private TemplateCatalog() {}

    private static Map<String, String> loadAvatars() {
        try (InputStream in = TemplateCatalog.class.getResourceAsStream("/assets/oriel/" + ROOT + "avatars/authors.json")) {
            Map<String, String> avatars = new LinkedHashMap<>();
            if (in != null) {
                JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                        .entrySet().forEach(e -> avatars.put(e.getKey(), e.getValue().getAsString()));
            }
            return Map.copyOf(avatars);
        } catch (IOException | RuntimeException e) {
            return Map.of(); // an author without an avatar shows the Oriel emblem
        }
    }

    /** One sidebar category: its key, its names by language and the templates in it. */
    public record Category(String key, Map<String, String> names, List<String> ids) {
        public String name(String language) {
            String l = normalize(language);
            for (String k : List.of(l, l.length() > 2 ? l.substring(0, 2) : l, "en_us", "zh_cn")) {
                String v = names.get(k);
                if (v != null && !v.isBlank()) return v;
            }
            return names.values().stream().findFirst().orElse(key);
        }
    }

    private static final List<Category> CATEGORIES = loadCategories();

    private static List<Category> loadCategories() {
        try (InputStream in = TemplateCatalog.class.getResourceAsStream("/assets/oriel/" + ROOT + "categories.json")) {
            if (in == null) return List.of();
            List<Category> out = new ArrayList<>();
            for (var value : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = value.getAsJsonObject();
                Map<String, String> names = new LinkedHashMap<>();
                o.getAsJsonObject("names").entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
                List<String> ids = new ArrayList<>();
                for (var id : o.getAsJsonArray("ids")) ids.add(id.getAsString());
                out.add(new Category(o.get("key").getAsString(), Map.copyOf(names), List.copyOf(ids)));
            }
            return List.copyOf(out);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** The sidebar categories, in order: how a player looks for a template, not how the templates were made. */
    public static List<Category> categories() {
        return CATEGORIES;
    }

    /** An author's avatar, a picture kept with the templates; the Oriel emblem when there is none. */
    public static String avatar(String author) {
        String file = AVATARS.get(author == null ? "" : author.trim());
        return file == null ? "oriel:textures/ui/emblem.png" : "oriel:" + ROOT + "avatars/" + file;
    }

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
        JsonObject e = entry(id);
        // A template that is one of the kit's own pages (the inventory, the settings...) opens that page itself.
        if (e.has("page")) return e.get("page").getAsString();
        return "oriel:" + ROOT + e.get("entry").getAsString();
    }

    /** Whether a copy can be saved to the player's folder: a template made of the kit's own pages has no files to copy. */
    public static boolean saveable(String id) {
        return !entry(id).has("page");
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

    /** What a template needs and the host does not have: the thing ("oriel" or "minecraft"), the range it asks for, and what runs. */
    public record Unmet(String what, String range, String have) {}

    /**
     * The first requirement of the template's {@code requires} ({@code {"oriel": ">=0.11.0", "minecraft": ">=26.1"}}) that
     * the given versions do not meet, or null when it can be used. A version the host does not know never fails.
     */
    public static Unmet unmet(JsonObject metadata, String oriel, String minecraft) {
        if (!metadata.has("requires") || !metadata.get("requires").isJsonObject()) return null;
        JsonObject requires = metadata.getAsJsonObject("requires");
        String[][] checks = {{"oriel", oriel}, {"minecraft", minecraft}};
        for (String[] check : checks) {
            if (!requires.has(check[0]) || !requires.get(check[0]).isJsonPrimitive()) continue;
            String range = requires.get(check[0]).getAsString();
            if (!Versions.satisfies(check[1], range)) return new Unmet(check[0], range, check[1]);
        }
        return null;
    }

    /** What the template is made for, as shown: "Oriel 0.11+ · Minecraft 26.1+" (a part it does not ask for is left out). */
    public static String fit(JsonObject metadata) {
        if (!metadata.has("requires") || !metadata.get("requires").isJsonObject()) return "";
        JsonObject requires = metadata.getAsJsonObject("requires");
        List<String> parts = new ArrayList<>();
        if (requires.has("oriel")) parts.add("Oriel " + Versions.describe(requires.get("oriel").getAsString()));
        if (requires.has("minecraft")) parts.add("Minecraft " + Versions.describe(requires.get("minecraft").getAsString()));
        return String.join(" · ", parts);
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
