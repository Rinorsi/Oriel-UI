import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.tools.ToolProvider;

/** Headless binary/business probe. This does not start a loader or establish game compatibility. */
public final class RealReleaseProbe {
    private RealReleaseProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !args[0].matches("fabric|neoforge"))
            throw new IllegalArgumentException("loader old-release.jar new-release.jar external-output");
        String loader = args[0];
        Path old = Path.of(args[1]), current = Path.of(args[2]), output = Path.of(args[3]).toAbsolutePath().normalize();
        Path repo = Path.of("").toRealPath();
        if (output.startsWith(repo)) throw new IllegalArgumentException("Output must be outside the repository");
        Files.createDirectories(output);
        if (output.toRealPath().startsWith(repo)) throw new IllegalArgumentException("Output resolves inside the repository");
        Path[] releases = {old, current};
        Path[] bundles = new Path[2], nestedReleases = new Path[2];
        for (int i = 0; i < 2; i++) {
            String version = i == 0 ? "0.9.0" : "0.10.0";
            try (var zip = new ZipFile(releases[i].toFile())) {
                String metadata = new String(zip.getInputStream(zip.getEntry(loader.equals("fabric")
                        ? "fabric.mod.json" : "META-INF/neoforge.mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
                String regex = loader.equals("fabric") ? "\"version\"\\s*:\\s*\"" + Pattern.quote(version) + "\""
                        : "(?m)^version\\s*=\\s*\"" + Pattern.quote(version) + "\"";
                if (!Pattern.compile(regex).matcher(metadata).find()) throw new AssertionError("Unexpected release version: " + releases[i]);
            }
            String name = "Consumer" + i;
            Path dir = output.resolve(name);
            Files.createDirectories(dir);
            Path source = output.resolve(name + ".java");
            Files.writeString(source, """
                    import dev.oriel.engine.dom.Document;
                    import dev.oriel.engine.host.Host;
                    import dev.oriel.engine.host.FontMetrics;
                    public final class %s {
                        public static String run() {
                            Host host = new Host() {
                                public FontMetrics fonts() { return (text, font) -> text.length() * 8f; }
                                public String loadText(String url) { return null; }
                            };
                            var doc = Document.parse(host, "probe:consumer.html", "<p id='value'>before</p>");
                            var value = doc.querySelector("#value");
                            value.setAttribute("data-consumer", "%s");
                            value.setTextContent("after");
                            if (!value.textContent().equals("after") || !value.getAttribute("data-consumer").equals("%s"))
                                throw new AssertionError("DOM business result");
                            doc.close();
                            return "PASS %s DOM parse/query/mutate/close";
                        }
                    }
                    """.formatted(name, version, version, version));
            int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath", releases[i].toString(),
                    "-d", dir.toString(), source.toString());
            if (result != 0) throw new AssertionError("Consumer compilation failed: " + version);
            if (i == 0) {
                Path slotSource = output.resolve("SlotConsumer.java");
                Files.writeString(slotSource, "public final class SlotConsumer { public static void run() {"
                        + "((dev.oriel.mod.client.DocumentDriver.Owner) null).slots(); }}");
                if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath", releases[i].toString(),
                        "-d", dir.toString(), slotSource.toString()) != 0) throw new AssertionError("Slot consumer compilation failed");
            }
            Path bundle = output.resolve("oriel_bundle_" + i + ".jar");
            bundles[i] = bundle;
            BundleFixtures.real(loader, releases[i], bundle, "oriel_bundle_" + i, version, dir);
            try (var zip = new ZipFile(bundle.toFile())) {
                byte[] nested = zip.getInputStream(zip.getEntry("META-INF/jars/oriel.jar")).readAllBytes();
                if (!MessageDigest.isEqual(nested, Files.readAllBytes(releases[i]))) throw new AssertionError("Release was rewritten");
                nestedReleases[i] = output.resolve("nested-runtime-" + i + ".jar");
                Files.write(nestedReleases[i], nested);
            }
            System.out.println("RELEASE " + releases[i] + " SHA256 " + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(releases[i]))));
        }
        for (int runtime = 0; runtime < 2; runtime++) {
            URL[] urls = {bundles[0].toUri().toURL(), bundles[1].toUri().toURL(), nestedReleases[runtime].toUri().toURL()};
            try (var classes = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                for (int consumer = 0; consumer < 2; consumer++) {
                    System.out.println("RUNTIME " + (runtime == 0 ? "0.9.0" : "0.10.0") + " "
                            + classes.loadClass("Consumer" + consumer).getMethod("run").invoke(null));
                }
                try {
                    classes.loadClass("SlotConsumer").getMethod("run").invoke(null);
                    throw new AssertionError("Expected slot linkage/null-receiver failure");
                } catch (java.lang.reflect.InvocationTargetException failure) {
                    Class<? extends Throwable> expected = runtime == 0 ? NullPointerException.class : NoSuchMethodError.class;
                    if (!expected.isInstance(failure.getCause())) throw failure;
                    System.out.println("SLOT_ABI runtime=" + (runtime == 0 ? "0.9.0" : "0.10.0")
                            + " expected=" + expected.getSimpleName() + " observed=" + failure.getCause());
                }
            }
        }
        System.out.println("LIMIT: shared DOM subset passes, old slot descriptor rejects 0.10.0; release-specific dependency ranges are disjoint. Loader resolution and game behavior are not verified.");
    }
}
