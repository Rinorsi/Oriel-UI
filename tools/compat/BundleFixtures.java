import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Creates two source-identical, differently versioned nested Oriel fixtures outside the repository. */
public final class BundleFixtures {
    private static String q(String value) { return (char)34 + value + (char)34; }
    private static final String NL = String.valueOf((char)10);

    public static void main(String[] args) throws Exception {
        String loader = args[0];
        Path library = Path.of(args[1]), output = Path.of(args[2]);
        Files.createDirectories(output);
        if (args.length >= 5) {
            current(loader, library, Path.of(args[3]), output, args[4], args.length > 5 ? args[5] : null);
            return;
        }
        for (int i = 1; i <= 2; i++) {
            String version = "0.12." + i;
            Map<String, byte[]> nested = new TreeMap<>();
            try (ZipFile zip = new ZipFile(library.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    byte[] bytes;
                    try (var input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                    if (entry.getName().equals("fabric.mod.json")) {
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        int key = text.indexOf(q("version")), colon = text.indexOf(':', key);
                        int start = text.indexOf((char)34, colon), end = text.indexOf((char)34, start + 1);
                        text = text.substring(0, start + 1) + version + text.substring(end);
                        bytes = text.getBytes(StandardCharsets.UTF_8);
                    } else if (entry.getName().equals("META-INF/neoforge.mods.toml")) {
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        List<String> lines = new ArrayList<>();
                        for (String line : text.split(NL)) {
                            int equals = line.indexOf('=');
                            lines.add(equals >= 0 && line.substring(0, equals).trim().equals("version") ? "version=" + q(version) : line);
                        }
                        bytes = String.join(NL, lines).getBytes(StandardCharsets.UTF_8);
                    }
                    nested.put(entry.getName(), bytes);
                }
            }
            String id = "oriel_bundle_" + i;
            Map<String, byte[]> parent = new TreeMap<>();
            parent.put("META-INF/jars/oriel.jar", zip(nested));
            if (loader.equals("fabric")) {
                String json = "{" + q("schemaVersion") + ":1," + q("id") + ":" + q(id) + "," + q("version") + ":" + q("1.0.0")
                        + "," + q("jars") + ":[{" + q("file") + ":" + q("META-INF/jars/oriel.jar") + "}],"
                        + q("depends") + ":{" + q("oriel") + ":" + q(">=0.12.0 <0.13.0") + "}}";
                parent.put("fabric.mod.json", json.getBytes(StandardCharsets.UTF_8));
            } else {
                String toml = "modLoader=" + q("lowcodefml") + NL + "loaderVersion=" + q("[1,)") + NL + "license=" + q("MIT")
                        + NL + "[[mods]]" + NL + "modId=" + q(id) + NL + "version=" + q("1.0.0") + NL + "displayName=" + q(id) + NL;
                parent.put("META-INF/neoforge.mods.toml", toml.getBytes(StandardCharsets.UTF_8));
                String metadata = "{" + q("jars") + ":[{" + q("identifier") + ":{" + q("group") + ":" + q("dev.oriel") + ","
                        + q("artifact") + ":" + q("oriel") + "}," + q("version") + ":{" + q("range") + ":" + q("[0.12.0,0.13.0)")
                        + "," + q("artifactVersion") + ":" + q(version) + "}," + q("path") + ":" + q("META-INF/jars/oriel.jar") + "}]}";
                parent.put("META-INF/jarjar/metadata.json", metadata.getBytes(StandardCharsets.UTF_8));
            }
            Files.write(output.resolve(id + ".jar"), zip(parent));
        }
    }

    /** Current acceptance embeds original slim/full bytes, never relabels a release. */
    private static void current(String loader, Path slim, Path full, Path output, String scenario, String legacyVersion) throws IOException {
        if (!List.of("slim", "top-full", "two-parents", "conflict", "same-core", "different-core", "legacy").contains(scenario))
            throw new IllegalArgumentException("Unknown JiJ scenario: " + scenario);
        Properties props = new Properties();
        try (var input = Files.newInputStream(Path.of("gradle.properties"))) { props.load(input); }
        String core = props.getProperty("version"), minor = core.substring(0, core.lastIndexOf('.'));
        String fullVersion = minor + "." + props.getProperty("oriel_full_patch");
        String[] parts = minor.split("\\.");
        String next = parts[0] + "." + (Integer.parseInt(parts[1]) + 1) + ".0";
        currentParent(loader, slim, output.resolve("oriel_bundle_1.jar"), "oriel_bundle_1",
                scenario.equals("legacy") ? Objects.requireNonNull(legacyVersion) : core,
                scenario.equals("legacy") ? legacyVersion : core, next);
        if (scenario.equals("top-full")) Files.copy(full, output.resolve("oriel-full.jar"));
        if (List.of("two-parents", "conflict", "same-core", "different-core", "legacy").contains(scenario))
            currentParent(loader, full, output.resolve("oriel_bundle_2.jar"), "oriel_bundle_2", fullVersion,
                    scenario.equals("conflict") ? next : core, scenario.equals("conflict") ? parts[0] + "." + (Integer.parseInt(parts[1]) + 2) + ".0" : next);
    }

    private static void currentParent(String loader, Path library, Path output, String id, String version,
                                      String lower, String upper) throws IOException {
        Map<String, byte[]> parent = new TreeMap<>();
        parent.put("META-INF/jars/oriel.jar", Files.readAllBytes(library));
        String display = "Oriel acceptance " + id;
        if (loader.equals("fabric")) {
            parent.put("fabric.mod.json", ("{\"schemaVersion\":1,\"id\":" + q(id) + ",\"name\":" + q(display)
                    + ",\"version\":\"1.0.0\",\"jars\":[{\"file\":\"META-INF/jars/oriel.jar\"}],\"depends\":{\"oriel\":"
                    + q(">=" + lower + " <" + upper) + "}}").getBytes(StandardCharsets.UTF_8));
        } else {
            String range = "[" + lower + "," + upper + ")";
            parent.put("META-INF/neoforge.mods.toml", ("modLoader=\"lowcodefml\"" + NL + "loaderVersion=\"[1,)\"" + NL
                    + "license=\"MIT\"" + NL + "[[mods]]" + NL + "modId=" + q(id) + NL + "version=\"1.0.0\"" + NL
                    + "displayName=" + q(display) + NL + "[[dependencies." + id + "]]" + NL + "modId=\"oriel\"" + NL
                    + "type=\"required\"" + NL + "versionRange=" + q(range) + NL).getBytes(StandardCharsets.UTF_8));
            parent.put("META-INF/jarjar/metadata.json", ("{\"jars\":[{\"identifier\":{\"group\":\"dev.oriel\",\"artifact\":\"oriel\"},"
                    + "\"version\":{\"range\":" + q(range) + ",\"artifactVersion\":" + q(version)
                    + "},\"path\":\"META-INF/jars/oriel.jar\"}]}").getBytes(StandardCharsets.UTF_8));
        }
        Files.write(output, zip(parent));
    }

    /** Wraps an unchanged release and a separately compiled consumer; ranges stay release-specific. */
    static void real(String loader, Path library, Path output, String id, String version, Path classes) throws IOException {
        Map<String, byte[]> parent = new TreeMap<>();
        parent.put("META-INF/jars/oriel.jar", Files.readAllBytes(library));
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                parent.put(classes.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
        }
        String range = version.equals("0.9.0") ? ">=0.9.0 <0.10.0" : ">=0.10.0 <0.11.0";
        if (loader.equals("fabric")) {
            parent.put("fabric.mod.json", ("{\"schemaVersion\":1,\"id\":" + q(id)
                    + ",\"version\":\"1.0.0\",\"jars\":[{\"file\":\"META-INF/jars/oriel.jar\"}],\"depends\":{\"oriel\":"
                    + q(range) + "}}").getBytes(StandardCharsets.UTF_8));
        } else {
            String interval = version.equals("0.9.0") ? "[0.9.0,0.10.0)" : "[0.10.0,0.11.0)";
            parent.put("META-INF/neoforge.mods.toml", ("modLoader=\"lowcodefml\"" + NL + "loaderVersion=\"[1,)\"" + NL
                    + "license=\"MIT\"" + NL + "[[mods]]" + NL + "modId=" + q(id) + NL + "version=\"1.0.0\"" + NL
                    + "[[dependencies." + id + "]]" + NL + "modId=\"oriel\"" + NL + "type=\"required\"" + NL
                    + "versionRange=" + q(interval) + NL).getBytes(StandardCharsets.UTF_8));
            parent.put("META-INF/jarjar/metadata.json", ("{\"jars\":[{\"identifier\":{\"group\":\"dev.oriel\",\"artifact\":\"oriel\"},"
                    + "\"version\":{\"range\":" + q(interval) + ",\"artifactVersion\":" + q(version)
                    + "},\"path\":\"META-INF/jars/oriel.jar\"}]}").getBytes(StandardCharsets.UTF_8));
        }
        Files.write(output, zip(parent));
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                ZipEntry file = new ZipEntry(entry.getKey()); file.setTime(0);
                zip.putNextEntry(file); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
