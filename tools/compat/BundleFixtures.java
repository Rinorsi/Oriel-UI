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
        for (int i = 1; i <= 2; i++) {
            String version = "0.10." + i;
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
                        + q("depends") + ":{" + q("oriel") + ":" + q(">=0.10.0 <0.11.0") + "}}";
                parent.put("fabric.mod.json", json.getBytes(StandardCharsets.UTF_8));
            } else {
                String toml = "modLoader=" + q("lowcodefml") + NL + "loaderVersion=" + q("[1,)") + NL + "license=" + q("MIT")
                        + NL + "[[mods]]" + NL + "modId=" + q(id) + NL + "version=" + q("1.0.0") + NL + "displayName=" + q(id) + NL;
                parent.put("META-INF/neoforge.mods.toml", toml.getBytes(StandardCharsets.UTF_8));
                String metadata = "{" + q("jars") + ":[{" + q("identifier") + ":{" + q("group") + ":" + q("dev.oriel") + ","
                        + q("artifact") + ":" + q("oriel") + "}," + q("version") + ":{" + q("range") + ":" + q("[0.10.0,0.11.0)")
                        + "," + q("artifactVersion") + ":" + q(version) + "}," + q("path") + ":" + q("META-INF/jars/oriel.jar") + "}]}";
                parent.put("META-INF/jarjar/metadata.json", metadata.getBytes(StandardCharsets.UTF_8));
            }
            Files.write(output.resolve(id + ".jar"), zip(parent));
        }
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
