package dev.oriel.mod.internal.nested;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Build-time inventory. Deliberately independent of Minecraft and loader APIs. */
public record NestedManifest(String version, String variant, String loader, String mc,
                             String coreHash, String apiHash, Map<String, String> entries) {
    public static final String PATH = "META-INF/oriel/manifest.json";
    public static final int MAX_BYTES = 200 * 1024;

    public NestedManifest {
        entries = Map.copyOf(entries);
    }

    public static NestedManifest parse(String json) {
        try {
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("manifest exceeds 200 KiB");
            var root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("unsupported manifest schema");
            var entries = new LinkedHashMap<String, String>();
            for (var value : root.getAsJsonArray("entries")) {
                var entry = value.getAsJsonObject();
                String path = entry.get("path").getAsString();
                if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains("\\"))
                    throw new IllegalArgumentException("invalid entry path: " + path);
                if (entries.put(path, hash(entry.get("sha256").getAsString())) != null)
                    throw new IllegalArgumentException("duplicate entry: " + path);
            }
            String variant = root.get("variant").getAsString(), loader = root.get("loader").getAsString();
            if (!variant.equals("slim") && !variant.equals("full")) throw new IllegalArgumentException("invalid variant");
            if (!loader.equals("fabric") && !loader.equals("neoforge")) throw new IllegalArgumentException("invalid loader");
            return new NestedManifest(root.get("version").getAsString(), variant, loader, root.get("mc").getAsString(),
                    hash(root.get("coreHash").getAsString()), hash(root.get("apiHash").getAsString()), entries);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Oriel nested manifest: " + e.getMessage(), e);
        }
    }

    private static String hash(String value) {
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid SHA-256");
        return value;
    }
}
