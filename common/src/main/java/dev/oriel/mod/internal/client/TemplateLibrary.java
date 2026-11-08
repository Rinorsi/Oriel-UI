package dev.oriel.mod.internal.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.kit.LocalPage;
import dev.oriel.kit.TemplateCatalog;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.preset.StylesPreset;
import dev.oriel.mod.client.OrielScreens;
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
 * <p>The index page ({@link OrielKit#STYLES}) sends {@code {"op": "fav" | "save" | "recent" | "folder" | "refresh", "id": ...,
 * "on": ...}} on {@link OrielKit#CHANNEL} and gets the state back as its {@value StylesPreset#LIBRARY} data:
 * {@code {"favorites": [...], "saved": [...], "recent": [...], "folder": "..."}}.
 */
public final class TemplateLibrary {
    public static final String NAMESPACE = MyPages.NAMESPACE;
    private static final int RECENT = 12;

    private static Path dir, file;
    private static final Set<String> favorites = new LinkedHashSet<>();
    private static final List<String> recent = new ArrayList<>();

    private TemplateLibrary() {}

    public static void init() {
        dir = MyPages.directory();
        file = Services.PLATFORM.configDir().resolve("oriel/templates.json");
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
        if (!op.equals("folder") && !op.equals("refresh") && !TemplateCatalog.ids().contains(id)) return false;
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
            case "refresh" -> { } // the state sent back lists the folder as it is now
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
            if (!TemplateCatalog.numbered(id) && Files.exists(dir.resolve(id + ".html"))) return;
            TemplateCatalog.saveCopy(dir, id, TemplateLibrary::readBytes);
            Constants.LOG.info("Oriel: saved template '{}' to {}", id, dir.resolve(TemplateCatalog.savedPath(id)));
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
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String path) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(Constants.id(path))
                .orElseThrow(() -> new IOException("no " + path));
        try (InputStream in = resource.open()) {
            return in.readAllBytes();
        }
    }

    private static JsonObject state() {
        JsonObject o = new JsonObject();
        o.add("favorites", array(favorites));
        o.add("recent", array(recent));
        List<String> saved = new ArrayList<>();
        JsonObject paths = new JsonObject();
        if (dir != null) for (String id : TemplateCatalog.ids()) {
            String path = TemplateCatalog.savedPath(id);
            if (Files.exists(dir.resolve(path))) { saved.add(id); paths.addProperty(id, path); }
            else if (!TemplateCatalog.numbered(id) && Files.exists(dir.resolve(id + ".html"))) {
                saved.add(id); paths.addProperty(id, id + ".html");
            }
        }
        o.add("saved", array(saved));
        o.add("savedPaths", paths);
        o.add("pages", pages());
        o.addProperty("folder", dir == null ? "" : dir.toString());
        o.addProperty("namespace", NAMESPACE);
        return o;
    }

    /**
     * Every page in the folder, below it and with / separators: what a player dropped there or a tool copied in, each as
     * {@code {"path", "title", "subtitle", "cover", "modified", "size"}} (see {@link LocalPage}); the subtitle and the cover are empty when the page has none.
     */
    private static JsonArray pages() {
        JsonArray found = new JsonArray();
        if (dir == null || !Files.isDirectory(dir)) return found;
        try (var walk = Files.walk(dir, 5)) {
            walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".html"))
                    .sorted().limit(300).forEach(p -> {
                        LocalPage.Info info = LocalPage.read(dir, p, Minecraft.getInstance().getLanguageManager().getSelected());
                        JsonObject page = new JsonObject();
                        page.addProperty("path", info.path());
                        page.addProperty("title", info.title());
                        page.addProperty("subtitle", info.subtitle());
                        page.addProperty("cover", info.cover());
                        page.addProperty("modified", info.modified());
                        page.addProperty("size", info.size());
                        found.add(page);
                    });
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("Oriel: could not list {}: {}", dir, e.toString());
        }
        return found;
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
