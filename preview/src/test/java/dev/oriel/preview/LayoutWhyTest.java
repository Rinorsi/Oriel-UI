package dev.oriel.preview;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LayoutWhyTest {
    @TempDir Path directory;

    @Test
    void stdoutContainsOnlyStructuredEventsAndPropertiesAreRestored() throws Exception {
        Path page = directory.resolve("page.html");
        Files.writeString(page, "<div style='display:flex;width:100px'><div style='display:grid;grid-template-columns:20px 30px'><div>one</div><div>two</div></div></div>");
        String flex = System.getProperty("oriel.layout.flex");
        String grid = System.getProperty("oriel.layout.grid");
        PrintStream before = System.out;
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            assertEquals(0, LayoutWhy.run(new String[] {page.toString(), "--why-size", "--why-position"}));
        } finally { System.setOut(before); }
        var lines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
        assertFalse(lines.isEmpty());
        long previous = 0;
        for (String line : lines) {
            var event = JsonParser.parseString(line).getAsJsonObject();
            assertEquals(1, event.get("schemaVersion").getAsInt());
            assertEquals("preview", event.get("environment").getAsString());
            long sequence = event.get("seq").getAsLong();
            assertTrue(sequence > previous);
            previous = sequence;
            assertTrue(event.getAsJsonObject("data").has("reason"));
        }
        assertTrue(lines.stream().anyMatch(s -> s.contains("layout.flex.")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("layout.grid.")));
        assertEquals(flex, System.getProperty("oriel.layout.flex"));
        assertEquals(grid, System.getProperty("oriel.layout.grid"));
    }

    @Test
    void rejectsMixedHumanAndJsonOutputModes() {
        assertThrows(IllegalArgumentException.class, () -> LayoutWhy.run(new String[] {"page.html", "--why-size", "--narrate"}));
        assertThrows(IllegalArgumentException.class, () -> LayoutWhy.run(new String[] {"--canvas-test", "--why-position"}));
    }

    @Test
    void missingPageDoesNotPolluteJsonStdout() throws Exception {
        PrintStream before = System.out;
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            assertEquals(1, LayoutWhy.run(new String[] {directory.resolve("missing.html").toString(), "--why-position"}));
        } finally { System.setOut(before); }
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }
}
