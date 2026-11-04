package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;
import dev.oriel.mod.client.OrielScreens;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.preset.StylesPreset;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.platform.Services;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The template centre's side in the game: the player's favourites and recently viewed templates, kept in
 * {@code config/oriel/templates.json}, and their saved copies. Saving a template copies its page and stylesheet to
 * {@code config/oriel/my-pages/}, served as the folder namespace {@value #NAMESPACE}: the player edits the copy there
 * and every save reloads it. What is saved is what is in that folder.
 *
 * <p>The index page ({@link OrielKit#STYLES}) sends {@code {"op": "fav" | "save" | "recent" | "folder", "id": ...,
 * "on": ...}} on {@link OrielKit#CHANNEL} and gets the state back as its {@value StylesPreset#LIBRARY} data:
 * {@code {"favorites": [...], "saved": [...], "recent": [...], "folder": "..."}}.
 */
public final class TemplateLibrary {
    public static final String NAMESPACE = "oriel_mine";
    private static final int RECENT = 12;

    private static Path dir, file;
    private static final Set<String> favorites = new LinkedHashSet<>();
    private static final List<String> recent = new ArrayList<>();

    private TemplateLibrary() {}

    public static void init() {
        Path root = Services.PLATFORM.configDir().resolve("oriel");
        dir = root.resolve("my-pages");
        file = root.resolve("templates.json");
        try {
            OrielPages.folder(NAMESPACE, dir);
        } catch (IllegalArgumentException e) {
            Constants.LOG.warn("Oriel: saved templates are off: {}", e.getMessage());
            dir = null;
        }
        load();
        OrielScreens.onPageLoad(OrielKit.STYLES, driver -> {
            driver.patch(StylesPreset.LIBRARY, state());
            driver.onMessage(OrielKit.CHANNEL, value -> {
                if (value.isJsonObject() && handle(value.getAsJsonObject())) driver.patch(StylesPreset.LIBRARY, state());
            });
        });
    }

    /** Applies one request from the page; whether the state changed. */
    private static boolean handle(JsonObject request) {
        String op = request.has("op") ? request.get("op").getAsString() : "";
        String id = request.has("id") ? request.get("id").getAsString() : "";
        Constants.LOG.debug("Oriel: template centre asks {} {}", op, id);
        if (!op.equals("folder") && !StylesPreset.PAGES.contains(id)) return false;
        switch (op) {
            case "fav" -> {
                if (request.has("on") && request.get("on").getAsBoolean()) favorites.add(id);
                else favorites.remove(id);
                store();
            }
            case "recent" -> {
                recent.remove(id);
                recent.addFirst(id);
                while (recent.size() > RECENT) recent.removeLast();
                store();
            }
            case "save" -> save(id);
            case "folder" -> {
                if (dir != null) openFolder(dir);
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies template {@code id} into the folder: its page, with the links to the kit's textures made absolute so
     * they still resolve there, and the stylesheet the looks share. An existing copy is kept, not overwritten: the
     * player may have edited it.
     */
    private static void save(String id) {
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            Path page = dir.resolve(id + ".html");
            if (!Files.exists(page)) {
                String html = read("ui/styles/" + id + ".html").replace("../../textures/", "oriel:textures/");
                Files.writeString(page, html, StandardCharsets.UTF_8);
            }
            Path css = dir.resolve("showcase.css");
            if (!Files.exists(css)) Files.writeString(css, read("ui/styles/showcase.css"), StandardCharsets.UTF_8);
            Constants.LOG.info("Oriel: saved template '{}' to {}", id, page);
        } catch (IOException e) {
            Constants.LOG.warn("Oriel: could not save template '{}': {}", id, e.toString());
        }
    }

    /** Shows the folder in the system's file browser, as the resource pack screen does. */
    private static void openFolder(Path path) {
        //? if >=26.3 {
        com.mojang.blaze3d.Blaze3D.openPath(path);
        //?} else
        //net.minecraft.util.Util.getPlatform().openPath(path);
    }

    private static String read(String path) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(Constants.id(path))
                .orElseThrow(() -> new IOException("no " + path));
        try (InputStream in = resource.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject state() {
        JsonObject o = new JsonObject();
        o.add("favorites", array(favorites));
        o.add("recent", array(recent));
        List<String> saved = new ArrayList<>();
        if (dir != null) for (String id : StylesPreset.PAGES) if (Files.exists(dir.resolve(id + ".html"))) saved.add(id);
        o.add("saved", array(saved));
        o.addProperty("folder", dir == null ? "" : dir.toString());
        o.addProperty("namespace", NAMESPACE);
        return o;
    }

    private static JsonArray array(Iterable<String> ids) {
        JsonArray a = new JsonArray();
        for (String id : ids) a.add(id);
        return a;
    }

    private static void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("favorites")) for (JsonElement e : o.getAsJsonArray("favorites")) favorites.add(e.getAsString());
            if (o.has("recent")) for (JsonElement e : o.getAsJsonArray("recent")) recent.add(e.getAsString());
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("Oriel: could not read {}: {}", file, e.toString());
        }
    }

    private static void store() {
        JsonObject o = new JsonObject();
        o.add("favorites", array(favorites));
        o.add("recent", array(recent));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.warn("Oriel: could not write {}: {}", file, e.toString());
        }
    }
}
