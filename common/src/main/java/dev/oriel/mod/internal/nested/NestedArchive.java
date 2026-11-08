package dev.oriel.mod.internal.nested;

import org.jspecify.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Bounded archive access only; loader modules decide which nested entries to visit. */
public final class NestedArchive implements AutoCloseable {
    private static final int MAX_ENTRY = 64 * 1024 * 1024;
    private final @Nullable ZipFile zip;
    private final @Nullable Path directory;
    private final Map<String, byte[]> memory;

    private NestedArchive(@Nullable ZipFile zip, @Nullable Path directory, Map<String, byte[]> memory) {
        this.zip = zip;
        this.directory = directory;
        this.memory = memory;
    }

    public static NestedArchive open(Path path) throws IOException {
        if (Files.isDirectory(path)) return new NestedArchive(null, path, Map.of());
        if (path.getFileSystem().equals(java.nio.file.FileSystems.getDefault()))
            return new NestedArchive(new ZipFile(path.toFile()), null, Map.of());
        return open(Files.readAllBytes(path));
    }

    public static NestedArchive open(byte[] bytes) throws IOException {
        Map<String, byte[]> selected = new LinkedHashMap<>();
        long total = 0;
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals(NestedManifest.PATH) || name.equals("fabric.mod.json")
                        || name.equals("META-INF/neoforge.mods.toml") || name.equals("META-INF/jarjar/metadata.json") || name.endsWith(".jar")) {
                    byte[] data = bounded(input, name.equals(NestedManifest.PATH) ? NestedManifest.MAX_BYTES : MAX_ENTRY);
                    total += data.length;
                    if (total > 128 * 1024 * 1024) throw new IOException("Nested metadata budget exceeded");
                    if (selected.put(name, data) != null) throw new IOException("Duplicate archive entry: " + name);
                }
            }
        }
        return new NestedArchive(null, null, selected);
    }

    public @Nullable byte[] read(String name) throws IOException {
        if (name.startsWith("/") || name.contains("..") || name.contains("\\")) throw new IOException("Invalid nested path: " + name);
        int max = name.equals(NestedManifest.PATH) ? NestedManifest.MAX_BYTES : MAX_ENTRY;
        if (zip != null) {
            var entry = zip.getEntry(name);
            if (entry == null) return null;
            try (var stream = zip.getInputStream(entry)) { return bounded(stream, max); }
        }
        if (directory != null) {
            Path file = directory.resolve(name);
            if (!Files.isRegularFile(file)) return null;
            try (var stream = Files.newInputStream(file)) { return bounded(stream, max); }
        }
        return memory.get(name);
    }

    public @Nullable String text(String name) throws IOException {
        byte[] value = read(name);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    public @Nullable NestedManifest manifest() throws IOException {
        String text = text(NestedManifest.PATH);
        return text == null ? null : NestedManifest.parse(text);
    }

    public static byte[] bounded(InputStream stream, int max) throws IOException {
        byte[] bytes = stream.readNBytes(max + 1);
        if (bytes.length > max) throw new IOException("Nested entry exceeds " + max + " bytes");
        return bytes;
    }

    @Override public void close() throws IOException { if (zip != null) zip.close(); }
}
