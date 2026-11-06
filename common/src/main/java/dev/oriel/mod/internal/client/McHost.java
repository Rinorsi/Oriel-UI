package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.host.PixelSurface;
import dev.oriel.engine.host.ReplacedContent;
import dev.oriel.engine.script.ScriptRuntime;
import dev.oriel.engine.script.Scripting;
import dev.oriel.engine.style.Cursor;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.TokenBucket;
import dev.oriel.mod.internal.OrielConfig;
import dev.oriel.mod.internal.client.render.McFontMetrics;
import dev.oriel.mod.internal.client.render.McImages;
import dev.oriel.mod.internal.client.render.McSurface;
import dev.oriel.mod.internal.client.replaced.McReplaced;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The engine's {@link Host} in Minecraft, one per document: resources, fonts, image sizes, canvas textures, Minecraft
 * elements and text, sounds, clipboard and translations here; closing, navigation, messages and the cursor go to the document's {@link DocumentDriver}.
 */
public final class McHost implements Host {
    private final DocumentDriver driver;
    private final Set<String> reported = new HashSet<>();
    private @Nullable TokenBucket sounds;
    private double soundRate;
    private int errors;

    public int errorCount() { return errors; }

    public McHost(DocumentDriver driver) {
        this.driver = driver;
    }

    @Override
    public @Nullable Object inspection() {
        return driver.inspection();
    }

    @Override
    public FontMetrics fonts() {
        return McFontMetrics.INSTANCE;
    }

    @Override
    public @Nullable String loadText(String url) {
        return readable(url) ? OrielResources.loadText(url) : null;
    }

    @Override
    public byte @Nullable [] loadBytes(String url) {
        return readable(url) ? OrielResources.loadBytes(url) : null;
    }

    /** A page a server opened can't read the player's files: it could send them back with oriel.send. */
    private boolean readable(String url) {
        if (!driver.serverSession() || !OrielPages.isFolderUrl(url)) return true;
        if (reported.add("folder " + url)) diagnostic(new Diagnostic(Diagnostic.Severity.ERROR, "server-folder-access",
                url, 0, "A server page cannot read a folder on this computer.", "Use a resource-pack page instead."));
        return false;
    }

    @Override
    public Map<String, Function<Element, ReplacedContent>> replacedElements() {
        return McReplaced.ELEMENTS;
    }

    @Override
    public float @Nullable [] imageSize(String url) {
        return McImages.textureSize(url);
    }

    @Override
    public boolean imageFailed(String url) {
        return McImages.textureSize(url) == null;
    }

    @Override
    public float @Nullable [] spriteSize(String id) {
        return McImages.spriteSize(id);
    }

    @Override
    public PixelSurface createSurface(int width, int height) {
        return new McSurface(width, height);
    }

    @Override
    public @Nullable List<TextRun> formatText(String json) {
        return McText.runs(json);
    }

    @Override
    public @Nullable ScriptRuntime createScriptRuntime(Document document) {
        return Scripting.rhino().apply(document);
    }

    @Override
    public void log(LogLevel level, String message) {
        switch (level) {
            case DEBUG -> Constants.LOG.debug("[{}] {}", driver.name(), message);
            case INFO -> Constants.LOG.info("[{}] {}", driver.name(), message);
            case WARN -> Constants.LOG.warn("[{}] {}", driver.name(), message);
            case ERROR -> {
                errors++;
                Constants.LOG.error("[{}] {}", driver.name(), message);
            }
        }
    }

    /** Script and resource errors: the page keeps working, so log each distinct message once. */
    @Override
    public void reportError(String message, Throwable error) {
        if (reported.add(message)) {
            errors++;
            Constants.LOG.error("[{}] {}", driver.name(), message, error);
        }
    }

    @Override
    public void setCursor(Cursor cursor) {
        driver.setCursor(cursor);
    }

    /**
     * Plays a sound the game knows (registered, or defined by a resource pack's sounds.json), at most
     * {@code client.soundsPerSecond} per driver (this host lives as long as its driver, so reloading the page doesn't
     * reset the count) and no louder than {@code client.maxSoundVolume}. Unknown ids are dropped: vanilla would log a
     * warning for each.
     */
    @Override
    public void playSound(String id, float volume, float pitch) {
        if (!OrielConfig.CLIENT_UI_SOUNDS.get() || OrielConfig.CLIENT_UI_SOUND_VOLUME.get() == 0) return;
        var resolved = dev.oriel.kit.Sounds.resolve(id, volume, pitch);
        id = resolved.id();
        volume = resolved.volume();
        pitch = resolved.pitch();
        Identifier sound = Identifier.tryParse(id);
        var manager = Minecraft.getInstance().getSoundManager();
        if (sound == null || manager.getSoundEvent(sound) == null || !(volume > 0) || !sounds().tryTake()) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(sound).orElseGet(() -> SoundEvent.createVariableRangeEvent(sound));
        float loudest = OrielConfig.CLIENT_MAX_SOUND_VOLUME.get().floatValue();
        manager.play(SimpleSoundInstance.forUI(event, Float.isFinite(pitch) ? Math.clamp(pitch, 0.5f, 2f) : 1f,
                Math.min(volume, loudest) * OrielConfig.CLIENT_UI_SOUND_VOLUME.get().floatValue()));
    }

    private TokenBucket sounds() {
        double rate = OrielConfig.CLIENT_SOUNDS_PER_SECOND.get();
        if (sounds == null || soundRate != rate) {
            sounds = new TokenBucket(Math.max(1, rate), rate);
            soundRate = rate;
            if (rate == 0) sounds = new TokenBucket(0, 0);
        }
        return sounds;
    }

    @Override
    public String getClipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public void close() {
        driver.requestClose();
    }

    @Override
    public boolean send(String channel, String json) {
        return driver.send(channel, json);
    }

    @Override
    public String translate(String key, String... args) {
        return I18n.get(key, (Object[]) args);
    }

    @Override
    public String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    @Override
    public String translation(String key) {
        return translation(language(), key);
    }

    @Override
    public String translation(String locale, String key) {
        if (key.startsWith("oriel.")) return McTranslations.lookup(locale, key);
        var language = net.minecraft.locale.Language.getInstance();
        return locale.equals(language()) && language.has(key) ? language.getOrDefault(key) : null;
    }

    @Override
    public boolean prefersReducedMotion() {
        return OrielConfig.CLIENT_REDUCED_MOTION.get();
    }

    @Override
    public void navigate(String url) {
        driver.navigate(url);
    }
}
