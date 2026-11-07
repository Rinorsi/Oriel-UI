package dev.oriel.mod.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class OrielConfigUpdateTest {
    @TempDir Path dir;

    @BeforeEach void prepare() { OrielConfig.apply(new Properties()); }
    @AfterEach void restore() { OrielConfig.apply(new Properties()); }

    @Test void failedBatchValidationLeavesDiskAndMemoryUnchanged() throws IOException {
        Path file = dir.resolve("oriel.properties");
        Files.writeString(file, "# keep\nclient.uiSounds=true\n");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("client.uiSounds", "false");
        changes.put("client.uiSoundVolume", "NaN");
        assertNotNull(OrielConfig.update(file, changes));
        assertTrue(OrielConfig.CLIENT_UI_SOUNDS.get());
        assertEquals("# keep\nclient.uiSounds=true\n", Files.readString(file));
        assertNotNull(OrielConfig.update(file, Map.of("unknown", "true")));
    }

    @Test void startupStillFallsBackToDefaultsButInteractiveUpdateRetainsCurrentValue() throws IOException {
        Path file = dir.resolve("oriel.properties");
        assertNull(OrielConfig.update(file, Map.of("client.uiSoundVolume", "0.3")));
        assertNotNull(OrielConfig.update(file, Map.of("client.uiSoundVolume", "2")));
        assertEquals(0.3, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
        Properties invalid = new Properties();
        invalid.setProperty("client.uiSoundVolume", "2");
        assertFalse(OrielConfig.apply(invalid).isEmpty());
        assertEquals(1, OrielConfig.CLIENT_UI_SOUND_VOLUME.get());
    }

    @Test void malformedExistingFileIsNotOverwritten() throws IOException {
        Path file = dir.resolve("oriel.properties");
        Files.writeString(file, "untouched=\\uNOPE\n");
        assertThrows(IOException.class, () -> OrielConfig.update(file, Map.of("client.uiSounds", "false")));
        assertEquals("untouched=\\uNOPE\n", Files.readString(file));
        assertTrue(OrielConfig.CLIENT_UI_SOUNDS.get());
    }

    @Test void missingKeyAppendedAfterTrailingContinuationRemainsASeparateProperty() throws IOException {
        for (String suffix : new String[]{"\\", "\\\n", "\\\r\n"}) {
            Path file = dir.resolve("oriel.properties");
            Files.writeString(file, "other=value" + suffix);
            assertNull(OrielConfig.update(file, Map.of("client.uiSounds", "false")));
            Properties saved = new Properties();
            try (var reader = Files.newBufferedReader(file)) { saved.load(reader); }
            assertEquals("value", saved.getProperty("other"));
            assertEquals("false", saved.getProperty("client.uiSounds"));
        }
    }
}
