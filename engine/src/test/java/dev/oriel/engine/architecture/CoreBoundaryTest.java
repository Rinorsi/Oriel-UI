package dev.oriel.engine.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ratchets the inherited host-specific dependencies down without permitting new ones. */
class CoreBoundaryTest {
    private static final Map<String, Pattern> RULES = rules();

    private static Map<String, Pattern> rules() {
        Map<String, Pattern> rules = new LinkedHashMap<>();
        rules.put("MC_TYPE", Pattern.compile("\\b(?:net\\.minecraft|com\\.mojang|org\\.lwjgl)(?:\\.[\\w$*]+)+"));
        rules.put("RHINO_TYPE", Pattern.compile("\\b(?:dev\\.oriel\\.shadow\\.rhino|org\\.mozilla\\.javascript)(?:\\.[\\w$*]+)+|\\bRhinoScriptRuntime\\b"));
        rules.put("SCENE_DOMAIN", Pattern.compile("\\bdev\\.oriel\\.engine\\.scene(?:\\.[\\w$*]+)*|\\b(?:Nbt|Structure|SceneModel|SceneContent|SceneBlock|SceneEntity|SceneGroup|SceneCamera|EntityFocus|EntityFraming)\\b"));
        rules.put("MC_RESOURCE", Pattern.compile("minecraft:[a-zA-Z0-9_./-]+"));
        rules.put("MC_STYLE", Pattern.compile("-mc-[a-z-]+|\\bmc-text\\b"));
        rules.put("FONT_POLICY", Pattern.compile("\\b(?:FontFamilies|MinecraftGlyphs|DEFAULT_FONT_SIZE|NORMAL_LINE_HEIGHT)\\b"));
        return rules;
    }

    @Test
    void inheritedBoundaryCannotGrowAndRemovedEntriesMustBeDeleted() throws IOException {
        Map<String, Integer> actual = new TreeMap<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/main/resources"))) {
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).filter(CoreBoundaryTest::source).sorted().toList()) {
                    actual.putAll(scan(file.toString().replace('\\', '/'), Files.readString(file)));
                }
            }
        }
        Map<String, Integer> allowed = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/architecture/core-boundary.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int split = line.lastIndexOf('\t');
            String key = line.substring(0, split);
            if (allowed.put(key, Integer.parseInt(line.substring(split + 1))) != null) {
                throw new IllegalArgumentException("Duplicate boundary allowance: " + key);
            }
        }
        Map<String, Integer> added = new TreeMap<>(), removed = new TreeMap<>();
        actual.forEach((key, count) -> { if (!count.equals(allowed.get(key))) added.put(key, count); });
        allowed.forEach((key, count) -> { if (!count.equals(actual.get(key))) removed.put(key, count); });
        assertTrue(added.isEmpty() && removed.isEmpty(), () -> "Core boundary changed. New/current entries: " + added
                + "\nStale/previous allowances: " + removed
                + "\nMove dependencies out of core and delete their exact allowances; do not refresh the baseline wholesale.");
    }

    @Test
    void scannerCatchesQualifiedTypesDomainsAndResourceConstants() {
        var found = scan("sample.java", "import net.minecraft.client.Minecraft;\n"
                + "dev.oriel.shadow.rhino.Context context;\n"
                + "dev.oriel.engine.scene.SceneModel scene;\n"
                + "String sound = \"minecraft:ui.button.click\";\n"
                + "String property = \"-mc-yaw\";\n");
        for (String category : List.of("MC_TYPE", "RHINO_TYPE", "SCENE_DOMAIN", "MC_RESOURCE", "MC_STYLE")) {
            assertTrue(found.keySet().stream().anyMatch(key -> key.startsWith(category + "\t")), category);
        }
    }

    @Test
    void commentsDoNotCountButStringsAndTextBlocksDo() {
        String source = "// minecraft:comment\n/* dev.oriel.shadow.rhino.Context */\n"
                + "String url = \"https://example.test/minecraft:kept\";\n"
                + "String escaped = \"\\\" minecraft:escaped\";\n"
                + "String block = \"\"\"\n minecraft:block\n\"\"\";";
        var found = scan("sample.java", source);
        assertFalse(found.keySet().stream().anyMatch(key -> key.contains("comment") || key.contains("Context")));
        assertEquals(3, found.size());
    }

    private static boolean source(Path path) {
        String name = path.toString();
        return name.endsWith(".java") || name.endsWith(".css");
    }

    static Map<String, Integer> scan(String path, String source) {
        Map<String, Integer> found = new TreeMap<>();
        String code = withoutComments(source);
        RULES.forEach((category, pattern) -> {
            var matcher = pattern.matcher(code);
            while (matcher.find()) found.merge(category + "\t" + path + "\t" + matcher.group(), 1, Integer::sum);
        });
        return found;
    }

    /** Preserves quoted resource ids, including Java text blocks, while discarding comments. */
    private static String withoutComments(String text) {
        StringBuilder out = new StringBuilder();
        boolean block = false, line = false, textBlock = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i), next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (line) { if (c == '\n') { line = false; out.append(c); } continue; }
            if (block) { if (c == '*' && next == '/') { block = false; i++; out.append(' '); } continue; }
            if (textBlock) {
                if (text.startsWith("\"\"\"", i)) { textBlock = false; out.append("\"\"\""); i += 2; }
                else out.append(c);
                continue;
            }
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && next != 0) { out.append(next); i++; }
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '/' && next == '/') { line = true; i++; continue; }
            if (c == '/' && next == '*') { block = true; i++; continue; }
            if (text.startsWith("\"\"\"", i)) { textBlock = true; out.append("\"\"\""); i += 2; continue; }
            if (c == '\'' || c == '"') quote = c;
            out.append(c);
        }
        return out.toString();
    }

    /** Explicit one-time inventory command; never invoked by the test or build. */
    public static void main(String[] args) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/main/resources"))) {
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).filter(CoreBoundaryTest::source).sorted().toList()) {
                    scan(file.toString().replace('\\', '/'), Files.readString(file))
                            .forEach((key, count) -> lines.add(key + "\t" + count));
                }
            }
        }
        lines.stream().sorted().forEach(System.out::println);
    }
}
