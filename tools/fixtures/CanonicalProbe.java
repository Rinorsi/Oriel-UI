import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import dev.oriel.buildlogic.CanonicalJarDigest;

/** Real ZIP fixtures: container differences pass, changed entry bytes fail. */
public final class CanonicalProbe {
    private CanonicalProbe() {}

    private static void write(Path jar, boolean reverse, boolean changed) throws Exception {
        var names = new ArrayList<>(List.of("a.class", "assets/b.txt"));
        if (reverse) Collections.reverse(names);
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.setLevel(reverse ? 0 : 9);
            for (String name : names) {
                var entry = new ZipEntry(name);
                entry.setTime(reverse ? 1700000000000L : 1600000000000L);
                out.putNextEntry(entry);
                out.write((name + (changed ? "changed" : "original")).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        var dir = Path.of(args[0]);
        var a = dir.resolve("a.jar");
        var b = dir.resolve("b.jar");
        var c = dir.resolve("c.jar");
        write(a, false, false);
        write(b, true, false);
        write(c, false, true);
        if (Files.mismatch(a, b) == -1) throw new AssertionError("Fixture must differ in container bytes");
        if (!CanonicalJarDigest.sha256(a).equals(CanonicalJarDigest.sha256(b))) throw new AssertionError("Order/metadata drift");
        if (CanonicalJarDigest.sha256(a).equals(CanonicalJarDigest.sha256(c))) throw new AssertionError("Tamper accepted");
        System.out.println("PASS");
    }
}
