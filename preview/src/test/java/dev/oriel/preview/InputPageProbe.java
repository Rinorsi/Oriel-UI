package dev.oriel.preview;

import dev.oriel.engine.css.StyleEngine;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.script.ScriptRuntime;
import dev.oriel.engine.style.Prop;
import dev.oriel.kit.OrielKit;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.ImageCanvas;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Fixed-frame differential and alternating CPU measurements; outputs always go to an explicit external directory. */
public final class InputPageProbe {
    private InputPageProbe() {}
    private record Capture(int[] pixels, List<String> styles, List<String> missing) {}

    public static void main(String[] args) throws Exception {
        Path pages = Path.of(args[1]), output = Path.of(args[2]);
        Files.createDirectories(output);
        OrielKit.register();
        Path jar = MinecraftAssets.findClientJar().orElseThrow();
        List<Path> targets;
        try (var files = Files.walk(pages)) { targets = files.filter(p -> p.toString().endsWith(".html")).sorted().toList(); }
        if (args[0].equals("compare")) {
            int comparisons = 0;
            for (Path page : targets) for (int width : new int[] {640, 427}) {
                Capture old = capture(page, jar, "old", width, output);
                for (String mode : List.of("new", "both")) {
                    Capture actual = capture(page, jar, mode, width, output);
                    if (!Arrays.equals(old.pixels, actual.pixels)) throw new AssertionError("pixel difference " + page + " " + width + " " + mode);
                    if (!old.styles.equals(actual.styles)) throw new AssertionError("computed style difference " + page + " " + width + " " + mode);
                    if (!old.missing.equals(actual.missing)) throw new AssertionError("missing resource difference " + page);
                    comparisons++;
                }
                System.out.println("PASS pixels and computed " + page + " " + width);
            }
            System.out.println("PASS " + comparisons + " comparisons");
        } else if (args[0].equals("bench")) {
            int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 5;
            for (int round = 0; round < rounds; round++) for (Path page : targets) {
                for (String mode : round % 2 == 0 ? List.of("old", "new") : List.of("new", "old"))
                    benchmark(page, jar, mode, round);
            }
        } else throw new IllegalArgumentException("compare or bench required");
    }

    private static Capture capture(Path page, Path jar, String mode, int width, Path output) throws Exception {
        System.setProperty("oriel.inputBackend", mode);
        int height = width == 640 ? 360 : 240;
        try (MinecraftAssets assets = MinecraftAssets.openForPage(PreviewHost.packRoot(page).stream().toList(), Optional.of(jar))) {
            MinecraftFont font = new MinecraftFont(assets);
            PreviewHost host = new PreviewHost(assets, font);
            host.quiet(true);
            String url = PreviewHost.pageUrl(page);
            // Seed the runtime before page scripts execute; fixture files and their markup stay untouched.
            Host seeded = (Host) Proxy.newProxyInstance(Host.class.getClassLoader(), new Class<?>[] {Host.class},
                    (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(host, arguments);
                            if (method.getName().equals("createScriptRuntime") && result instanceof ScriptRuntime scripts)
                                scripts.evaluate("(() => { let s = 12345; Math.random = () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296); })()",
                                        "input-differential-seed.js");
                            return result;
                        } catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            Document document = Document.parse(seeded, url, host.loadText(url), null, new Viewport(width, height, 1));
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            ImageCanvas canvas = new ImageCanvas(image, 1, assets, font);
            try {
                for (int frame = 0; frame <= 60; frame++) {
                    Arrays.fill(pixels, 0);
                    canvas.reset();
                    document.frame(frame * 16.0);
                    document.paint(canvas);
                    if (document.error() != null) throw new IllegalStateException(page.toString(), document.error());
                }
                List<String> missing = host.missingImages().stream().sorted().toList();
                if (!missing.isEmpty()) {
                    if (!page.normalize().startsWith(Path.of("tools/h5ref/corpus")))
                        throw new IllegalStateException("Missing assets: " + missing);
                    System.out.println("INCOMPLETE_ASSETS " + page + " " + mode + " " + missing);
                }
                List<String> styles = new ArrayList<>();
                document.forEachElement(e -> {
                    if (e.style == null) return;
                    styles.add(e.tagName() + "#" + e.id());
                    for (Prop p : Prop.values()) styles.add(p.cssName + "=" + StyleEngine.computedValue(e.style, p.cssName));
                });
                String name = page.getFileName().toString() + "-" + Integer.toHexString(page.toString().hashCode()) + "-" + width + "-" + mode;
                ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
                Files.write(output.resolve(name + ".computed.txt"), styles);
                return new Capture(pixels.clone(), styles, missing);
            } finally {
                canvas.dispose();
                document.close();
            }
        }
    }

    private static void benchmark(Path page, Path jar, String mode, int round) throws Exception {
        System.setProperty("oriel.inputBackend", mode);
        try (MinecraftAssets assets = MinecraftAssets.open(PreviewHost.packRoot(page).stream().toList(), Optional.of(jar))) {
            MinecraftFont font = new MinecraftFont(assets);
            PreviewHost host = new PreviewHost(assets, font);
            host.quiet(true);
            PageScene scene = new PageScene(host, PreviewHost.pageUrl(page), null, new Viewport(427, 240, 1));
            BufferedImage image = new BufferedImage(427, 240, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            ImageCanvas canvas = new ImageCanvas(image, 1, assets, font);
            long[] frames = new long[120], styles = new long[120], layouts = new long[120];
            try {
                for (int i = 0; i < 180; i++) {
                    Arrays.fill(pixels, 0);
                    long start = System.nanoTime();
                    canvas.reset();
                    scene.frame(i * (1000.0 / 60), 427, 240, 1);
                    scene.paint(canvas);
                    long frame = System.nanoTime() - start;
                    var doc = scene.document();
                    doc.invalidateStyle();
                    start = System.nanoTime();
                    doc.frame(i * (1000.0 / 60));
                    long style = System.nanoTime() - start;
                    doc.invalidateLayout();
                    start = System.nanoTime();
                    doc.frame(i * (1000.0 / 60));
                    long layout = System.nanoTime() - start;
                    if (scene.error() != null) throw new IllegalStateException(page.toString(), scene.error());
                    if (i >= 60) { frames[i - 60] = frame; styles[i - 60] = style; layouts[i - 60] = layout; }
                }
                if (!host.missingImages().isEmpty()) throw new IllegalStateException("Missing assets: " + host.missingImages());
                emit(page, mode, round, "frame", frames);
                emit(page, mode, round, "restyle-frame", styles);
                emit(page, mode, round, "relayout-frame", layouts);
            } finally {
                canvas.dispose();
                if (scene.document() != null) scene.document().close();
            }
        }
    }

    private static void emit(Path page, String mode, int round, String metric, long[] samples) {
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT, "{\"page\":\"%s\",\"mode\":\"%s\",\"round\":%d,\"metric\":\"%s\",\"p50Ms\":%.6f,\"p95Ms\":%.6f}%n",
                page.getFileName(), mode, round, metric, samples[59] / 1e6, samples[113] / 1e6);
    }
}
