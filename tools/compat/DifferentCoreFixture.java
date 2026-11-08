import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Builds an intentionally different core resource fixture in the external acceptance directory. */
public final class DifferentCoreFixture {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), output = Path.of(args[1]), api = Path.of(args[2]);
        var entries = new TreeMap<String, byte[]>();
        try (var zip = new ZipFile(source.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                if (entry.isDirectory()) continue;
                try (var input = zip.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); }
            }
        }
        String resource = "assets/oriel/ui/ore.css";
        if (!entries.containsKey(resource)) throw new IOException("Fixture requires the real Ore stylesheet");
        entries.put(resource, (new String(entries.get(resource), StandardCharsets.UTF_8)
                + "\n/* Acceptance fixture: intentionally different core. */\n.o-window { padding: 2px; }\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(output.toAbsolutePath().getParent());
        write(output, entries);
        Path manifest = output.resolveSibling("different-core-manifest.json");
        var process = new ProcessBuilder("node", "tools/release/manifest.mjs", output.toString(), api.toString(), manifest.toString()).inheritIO().start();
        if (process.waitFor() != 0) throw new IOException("Fixture manifest generation failed");
        entries.put("META-INF/oriel/manifest.json", Files.readAllBytes(manifest));
        write(output, entries);
    }

    private static void write(Path output, TreeMap<String, byte[]> entries) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var entry : entries.entrySet()) {
                var file = new ZipEntry(entry.getKey());
                file.setTime(0);
                zip.putNextEntry(file);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
}
