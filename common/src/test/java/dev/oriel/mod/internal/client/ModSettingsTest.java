package dev.oriel.mod.internal.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oriel.mod.internal.OrielConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ModSettingsTest {
    @TempDir Path dir;
    private Path file;

    @BeforeEach void prepare() {
        file = dir.resolve(OrielConfig.FILE_NAME);
        OrielConfig.apply(new Properties());
    }

    @AfterEach void restore() { OrielConfig.apply(new Properties()); }

    private void request(String json) throws Exception {
        ModSettings.handle(file, JsonParser.parseString(json));
    }

    private void set(String key, Object value) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("op", "set");
        request.addProperty("key", key);
        if (value instanceof Boolean b) request.addProperty("value", b);
        else if (value instanceof Number n) request.addProperty("value", n);
        else request.addProperty("value", value.toString());
        ModSettings.handle(file, request);
    }

    @Test void whitelistRejectsUnknownAndPrivilegedKeys() throws Exception {
        for (String key : new String[]{"unknown", "limits.maxNodes", "client.inspector",
                "client.forceCloseKey", "client.messageBurst", "server.maxDataChars"}) set(key, false);
        assertFalse(Files.exists(file));
        assertFalse(OrielConfig.CLIENT_INSPECTOR.get());
        assertEquals(20, OrielConfig.CLIENT_MESSAGE_BURST.get());
    }

    @Test void malformedRequestsAndValuesLeaveCurrentValueIntact() throws Exception {
        set("client.uiSounds", false);
        String before = Files.readString(file);
        for (String json : new String[]{"null", "[]", "{}", "{\"op\":[]}",
                "{\"op\":\"set\",\"key\":null}",
                "{\"op\":\"set\",\"key\":\"client.uiSounds\"}",
                "{\"op\":\"set\",\"key\":\"client.uiSounds\",\"value\":null}",
                "{\"op\":\"set\",\"key\":\"client.uiSounds\",\"value\":{}}",
                "{\"op\":\"other\"}"}) request(json);
        set("client.uiSounds", "yes");
        set("client.uiSounds", 1);
        set("client.webLinks", "ALLOW");
        assertFalse(OrielConfig.CLIENT_UI_SOUNDS.get());
        assertEquals(before, Files.readString(file));
    }

    @Test void numericBoundsAreInclusiveAndInvalidValuesAreNeverClamped() throws Exception {
        set("client.uiSoundVolume", 0);
        assertEquals(0, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
        set("client.uiSoundVolume", 1);
        assertEquals(1, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
        set("client.uiSoundVolume", 0.35);
        for (Object invalid : new Object[]{-0.01, 1.01, "NaN", "Infinity", true, "loud"}) {
            set("client.uiSoundVolume", invalid);
            assertEquals(0.35, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
        }
        request("{\"op\":\"set\",\"key\":\"client.uiSoundVolume\",\"value\":1e999}");
        assertEquals(0.35, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
        assertEquals("0.35", read().getProperty("client.uiSoundVolume"));
    }

    @Test void kitControlStringValuesUseTheSameSettingParsers() throws Exception {
        set("client.uiSounds", "false");
        set("client.uiSoundVolume", "0.45");
        set("client.serverPages", "ASK");
        assertFalse(OrielConfig.CLIENT_UI_SOUNDS.get());
        assertEquals(0.45, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
        assertEquals(OrielConfig.ServerPages.ASK, OrielConfig.CLIENT_SERVER_PAGES.get());
        assertEquals("false", read().getProperty("client.uiSounds"));
        assertEquals("0.45", read().getProperty("client.uiSoundVolume"));
        assertEquals("ask", read().getProperty("client.serverPages"));
    }

    @Test void persistencePreservesCommentsOtherKeysAndLineEndings() throws Exception {
        String before = "# 玩家配置\r\n! keep me\r\nserver.maxDataChars : 1234\r\n\r\nclient.uiSounds = true\r\nother=some\\\r\n  continued value\r\n";
        Files.writeString(file, before);
        set("client.uiSounds", false);
        assertEquals(before.replace("client.uiSounds = true", "client.uiSounds=false"), Files.readString(file));
        set("client.uiSoundVolume", 0.5);
        assertTrue(Files.readString(file).endsWith("client.uiSoundVolume=0.5\r\n"));
        assertEquals("somecontinued value", read().getProperty("other"));
    }

    @Test void escapedDuplicateAndContinuedKeysCannotOverrideAnUpdate() throws Exception {
        Files.writeString(file, "# comment\\\nclient.uiSounds=true\nclient\\.uiSounds:tr\\\n ue\nclient.uiSoundVolume=0.2");
        set("client.uiSounds", false);
        assertEquals("false", read().getProperty("client.uiSounds"));
        assertTrue(Files.readString(file).startsWith("# comment\\\n"));
        set("client.typingNotice", false);
        assertEquals("0.2", read().getProperty("client.uiSoundVolume"));
        assertEquals("false", read().getProperty("client.typingNotice"));
    }

    @Test void resetChangesOnlyWhitelistAndPersistsDefaults() throws Exception {
        Files.writeString(file, "# untouched\nclient.inspector=true\nlimits.maxNodes=100\n");
        set("client.uiSounds", false);
        set("client.uiSoundVolume", 0.4);
        set("client.serverPages", "BLOCK");
        set("client.webLinks", "BLOCK");
        set("client.reducedMotion", true);
        set("client.typingNotice", false);
        set("client.shapeShader", false);
        request("{\"op\":\"reset\"}");
        var items = ModSettings.state().getAsJsonArray("items");
        for (var item : items) assertEquals(item.getAsJsonObject().get("default"), item.getAsJsonObject().get("value"));
        Properties saved = read();
        assertEquals(9, saved.size());
        assertEquals("true", saved.getProperty("client.inspector"));
        assertEquals("100", saved.getProperty("limits.maxNodes"));
        assertEquals("allow", saved.getProperty("client.serverPages"));
        assertTrue(Files.readString(file).startsWith("# untouched\n"));
    }

    @Test void stateContainsTypedValuesBoundsOptionsAndLiveFlags() {
        JsonObject state = ModSettings.state();
        assertEquals(1, state.size());
        var items = state.getAsJsonArray("items");
        assertEquals(7, items.size());
        Map<String, JsonObject> byKey = new LinkedHashMap<>();
        for (var item : items) {
            var entry = item.getAsJsonObject();
            assertNull(byKey.put(entry.get("key").getAsString(), entry));
            assertTrue(entry.get("live").getAsBoolean());
            assertTrue(entry.has("default"));
            assertTrue(entry.get("options").isJsonArray());
        }
        assertEquals("bool", byKey.get("client.uiSounds").get("type").getAsString());
        assertTrue(byKey.get("client.uiSounds").getAsJsonPrimitive("value").isBoolean());
        var volume = byKey.get("client.uiSoundVolume");
        assertEquals("number", volume.get("type").getAsString());
        assertEquals(0, volume.get("min").getAsDouble());
        assertEquals(1, volume.get("max").getAsDouble());
        assertEquals(0.05, volume.get("step").getAsDouble());
        assertEquals(JsonParser.parseString("[\"ALLOW\",\"ASK\",\"BLOCK\"]"), byKey.get("client.serverPages").get("options"));
        assertEquals(JsonParser.parseString("[\"ASK\",\"BLOCK\"]"), byKey.get("client.webLinks").get("options"));
    }

    @Test void parallelUpdatesDoNotLoseEitherKeyOrDamageFile() throws Exception {
        Files.writeString(file, "# keep\n");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); set("client.uiSounds", false); return null; });
            var second = executor.submit(() -> { start.await(); set("client.uiSoundVolume", 0.25); return null; });
            start.countDown();
            first.get();
            second.get();
        }
        assertEquals("false", read().getProperty("client.uiSounds"));
        assertEquals("0.25", read().getProperty("client.uiSoundVolume"));
        assertTrue(Files.readString(file).startsWith("# keep\n"));
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }

    @Test void failedPersistenceDoesNotPublishUnsavedValues() throws Exception {
        Files.createDirectory(file);
        assertThrows(java.io.IOException.class, () -> set("client.uiSounds", false));
        assertTrue(OrielConfig.CLIENT_UI_SOUNDS.get());
    }

    @Test void oversizedOrNestedPayloadCannotWriteSettings() throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("op", "reset");
        request.addProperty("padding", "x".repeat(1025));
        ModSettings.handle(file, request);
        request.remove("padding");
        request.add("extra", new JsonObject());
        ModSettings.handle(file, request);
        assertFalse(Files.exists(file));
    }

    private Properties read() throws Exception {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        return p;
    }
}
