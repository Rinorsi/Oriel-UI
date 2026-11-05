package dev.oriel.preview.host;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.host.PixelSurface;
import dev.oriel.engine.host.ReplacedContent;
import dev.oriel.engine.script.ScriptRuntime;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import dev.oriel.preview.render.PreviewSurface;
import dev.oriel.preview.render.Texture;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The previewer's {@link Host}. Text and images come from the {@link MinecraftAssets} stack. A page that lives under
 * {@code <root>/assets/<ns>/} is addressed as {@code ns:path} with {@code <root>} on the stack, so its {@code ns:}
 * references resolve as in game; other pages are addressed by file path and resolve relative paths on disk.
 * Translations use the selected language files with an {@code en_us} fallback; canvases are images registered with the assets.
 */
public final class PreviewHost implements Host {
    private static final float[] UNKNOWN = new float[0];

    private final MinecraftAssets assets;
    private final MinecraftFont fonts;
    private final Map<String, Function<Element, ReplacedContent>> replacedElements;
    private final Map<String, float[]> imageSizes = new ConcurrentHashMap<>(), spriteSizes = new ConcurrentHashMap<>();
    private Map<String, String> translations;
    private final Map<String, Map<String, String>> localeTranslations = new java.util.HashMap<>();
    private String language = "en_us";

    public void setLanguage(String language) {
        String normalized = language.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid language: " + language);
        this.language = normalized;
        translations = null;
        localeTranslations.clear();
    }

    @Override public String language() { return language; }

    @Override public String translation(String key) {
        return translation(language, key);
    }

    @Override public String translation(String locale, String key) {
        if (key.startsWith("oriel.")) return localeTranslations.computeIfAbsent(locale, assets::translations).get(key);
        // Native Minecraft data keeps its existing host fallback, independently of library copy.
        if (!locale.equals(language)) return localeTranslations.computeIfAbsent(locale, assets::translations).get(key);
        if (translations == null) {
            translations = new java.util.HashMap<>(assets.translations("en_us"));
            if (!language.equals("en_us")) translations.putAll(assets.translations(language));
        }
        return translations.get(key);
    }
    private final Set<Path> loadedFiles = ConcurrentHashMap.newKeySet();
    private Consumer<String> navigator = url -> {};
    /** The clipboard when there is no system clipboard (headless). */
    private String clipboard = "";

    public PreviewHost(MinecraftAssets assets, MinecraftFont fonts) {
        this.assets = assets;
        this.fonts = fonts;
        this.replacedElements = ReplacedElements.of(assets, fonts);
    }

    public MinecraftAssets assets() { return assets; }

    /** Files read through {@link #loadText}, so the previewer can watch them. */
    public Set<Path> loadedFiles() { return loadedFiles; }

    /** Where {@code <a href>} and {@code location.href} navigation goes. */
    public void onNavigate(Consumer<String> navigator) {
        this.navigator = navigator;
    }

    // ---- Page addressing ----

    /** The URL of a page file: {@code ns:path} when it lives under {@code <root>/assets/<ns>/}, else its path. */
    public static String pageUrl(Path page) {
        Path file = page.toAbsolutePath().normalize();
        int assetsDir = assetsDirIndex(file);
        if (assetsDir < 0) return file.toString();
        String path = file.subpath(assetsDir + 2, file.getNameCount()).toString();
        return file.getName(assetsDir + 1) + ":" + path.replace(file.getFileSystem().getSeparator(), "/");
    }

    /** The resource root a page under {@code <root>/assets/<ns>/} needs on the asset stack. */
    public static Optional<Path> packRoot(Path page) {
        Path file = page.toAbsolutePath().normalize();
        int assetsDir = assetsDirIndex(file);
        if (assetsDir < 0) return Optional.empty();
        return Optional.of(assetsDir == 0 ? file.getRoot() : file.getRoot().resolve(file.subpath(0, assetsDir)));
    }

    /** Index of the innermost {@code assets} directory followed by a valid namespace directory, or -1. */
    private static int assetsDirIndex(Path file) {
        for (int i = file.getNameCount() - 3; i >= 0; i--) {
            if (file.getName(i).toString().equals("assets") && MinecraftAssets.isAssetId(file.getName(i + 1) + ":_")) return i;
        }
        return -1;
    }

    // ---- Host ----

    @Override
    public MinecraftFont fonts() { return fonts; }

    @Override
    public String loadText(String url) {
        byte[] bytes = loadBytes(url);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] loadBytes(String url) {
        return assets.locate(url).map(file -> {
            if (file.getFileSystem() == FileSystems.getDefault()) loadedFiles.add(file);
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                log(LogLevel.WARN, "Cannot read " + file + ": " + e);
                return null; // unreadable is missing, as Host.loadText promises
            }
        }).orElse(null);
    }

    /** {@code ns:} ids resolve as in game; references from a file-path page resolve on the file system. */
    @Override
    public String resolveUrl(String base, String relative) {
        if (base == null || relative == null || relative.isEmpty()
                || MinecraftAssets.isAssetId(base) || MinecraftAssets.isAssetId(relative)) {
            return Host.super.resolveUrl(base, relative);
        }
        return Path.of(base).resolveSibling(relative).normalize().toString();
    }

    @Override
    public Map<String, Function<Element, ReplacedContent>> replacedElements() {
        return replacedElements;
    }

    @Override
    public float[] imageSize(String url) {
        float[] size = known(imageSizes.computeIfAbsent(url, u -> size(assets.texture(u))));
        if (size == null) missing.add(url);
        return size;
    }

    /** Images asked for that did not resolve, in order ({@code --check}). */
    public java.util.Set<String> missingImages() {
        return missing;
    }

    @Override
    public boolean imageFailed(String url) {
        return missing.contains(url);
    }

    /** Every diagnostic so far, formatted ({@code --check}). */
    public List<String> diagnosticLines() {
        return diagnosticLines;
    }

    /** Structured diagnostics for the MCP workflow; no parsing of formatted log lines. */
    public List<Diagnostic> diagnosticRecords() { return List.copyOf(diagnosticRecords); }
    public List<String> runtimeErrors() { return List.copyOf(runtimeErrors); }

    /** Quiet: diagnostics and info logs are kept, not printed ({@code --check} prints its own report). */
    public void quiet(boolean on) {
        quiet = on;
    }

    private final java.util.Set<String> missing = new java.util.LinkedHashSet<>();
    private final List<String> diagnosticLines = new java.util.ArrayList<>();
    private final List<Diagnostic> diagnosticRecords = new java.util.ArrayList<>();
    private final List<String> runtimeErrors = new java.util.ArrayList<>();
    private boolean quiet;

    @Override
    public float[] spriteSize(String id) {
        return known(spriteSizes.computeIfAbsent(id, i -> size(assets.sprite(i))));
    }

    private static float[] size(Optional<Texture> texture) {
        return texture.map(t -> new float[] {t.naturalWidth(), t.naturalHeight()}).orElse(UNKNOWN);
    }

    private static float[] known(float[] size) {
        return size == UNKNOWN ? null : size;
    }

    @Override
    public PixelSurface createSurface(int width, int height) {
        return new PreviewSurface(assets, width, height);
    }

    /** Selected language from every namespace on the stack, with Minecraft's {@code %s} / {@code %1$s} arguments. */
    @Override
    public String translate(String key, String... args) {
        String text = translation(key);
        if (text == null) return key;
        try {
            return String.format(Locale.ROOT, text, (Object[]) args);
        } catch (IllegalFormatException e) {
            return text;
        }
    }

    @Override
    public ScriptRuntime createScriptRuntime(Document document) {
        return dev.oriel.engine.script.Scripting.rhino().apply(document);
    }

    /** Diagnostics at warning level or above so far, for {@code --strict}. */
    private int problems;

    @Override
    public void diagnostic(Diagnostic d) {
        diagnosticRecords.add(d);
        diagnosticLines.add(d.format());
        if (!quiet) System.err.println("[diagnostic] " + d.format());
        if (d.severity() != Diagnostic.Severity.INFO) problems++;
    }

    public int problems() {
        return problems;
    }

    @Override
    public void log(LogLevel level, String message) {
        if (quiet && level != LogLevel.ERROR) return;
        System.out.println("[oriel " + level.name().toLowerCase() + "] " + message);
    }

    @Override
    public void reportError(String message, Throwable error) {
        runtimeErrors.add(message + ": " + error);
        log(LogLevel.ERROR, message);
        error.printStackTrace(System.out);
    }

    @Override
    public void playSound(String id, float volume, float pitch) {
        log(LogLevel.DEBUG, "sound " + id);
    }

    @Override
    public String getClipboard() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor) instanceof String s ? s : "";
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException e) {
            return clipboard;
        }
    }

    @Override
    public void setClipboard(String text) {
        clipboard = text;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (HeadlessException | IllegalStateException e) {
            // keep the in-process copy
        }
    }

    @Override
    public void close() {
        log(LogLevel.INFO, "close() requested");
    }

    @Override
    public boolean send(String channel, String json) {
        log(LogLevel.INFO, "send " + channel + " " + json);
        return true;
    }

    @Override
    public void navigate(String url) {
        navigator.accept(url);
    }
}
