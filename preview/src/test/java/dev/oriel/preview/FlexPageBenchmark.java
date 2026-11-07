package dev.oriel.preview;

import com.google.gson.JsonObject;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.kit.OrielKit;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.ImageCanvas;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Opt-in paired frame/style/layout probe; compile before entering the exclusive measurement window. */
public final class FlexPageBenchmark {
    private FlexPageBenchmark() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String[] modes = args[1].split(",");
        int rounds = Integer.parseInt(args[2]);
        if (modes.length != 2 || rounds < 3 || rounds % 2 == 0)
            throw new IllegalArgumentException("Expected two modes and an odd round count >= 3");
        List<Path> pages;
        try (var files = Files.walk(root)) {
            pages = files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
        OrielKit.register();
        Path jar = MinecraftAssets.findClientJar().orElseThrow();
        for (Path page : pages) {
            for (int round = 0; round < rounds; round++) {
                for (int index = 0; index < modes.length; index++) {
                    int slot = round % 2 == 0 ? index : modes.length - 1 - index;
                    String mode = modes[slot];
                    System.setProperty("oriel.layout.flex", mode);
                    JsonObject result = measure(page, jar);
                    result.addProperty("page", root.relativize(page).toString().replace((char) 92, '/'));
                    result.addProperty("mode", mode);
                    result.addProperty("slot", slot);
                    result.addProperty("round", round);
                    System.out.println(result);
                }
            }
        }
    }

    private static JsonObject measure(Path page, Path jar) throws Exception {
        try (MinecraftAssets assets = MinecraftAssets.open(PreviewHost.packRoot(page).stream().toList(), java.util.Optional.of(jar))) {
            MinecraftFont font = new MinecraftFont(assets);
            PreviewHost host = new PreviewHost(assets, font);
            host.quiet(true);
            PageScene scene = new PageScene(host, PreviewHost.pageUrl(page), null, new Viewport(427, 240, 1));
            BufferedImage image = new BufferedImage(427, 240, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            ImageCanvas canvas = new ImageCanvas(image, 1, assets, font);
            long[] frames = new long[120], styles = new long[120], layouts = new long[120];
            try {
                // Same normal-frame workload as FrameBenchmark; do not force invalidation during this phase.
                for (int frame = 0; frame < 180; frame++) {
                    Arrays.fill(pixels, 0);
                    long start = System.nanoTime();
                    canvas.reset();
                    scene.frame(frame * (1000.0 / 60), 427, 240, 1);
                    scene.paint(canvas);
                    long elapsed = System.nanoTime() - start;
                    if (scene.error() != null) throw new IllegalStateException(page.toString(), scene.error());
                    if (frame >= 60) frames[frame - 60] = elapsed;
                }
                // Separate forced-stage costs at the final fixed animation state.
                var doc = scene.document();
                for (int sample = 0; sample < 180; sample++) {
                    doc.invalidateStyle();
                    long start = System.nanoTime();
                    doc.flushStyle();
                    long style = System.nanoTime() - start;
                    doc.invalidateLayout();
                    start = System.nanoTime();
                    doc.flushLayout();
                    long layout = System.nanoTime() - start;
                    if (doc.error() != null) throw new IllegalStateException(page.toString(), doc.error());
                    if (sample >= 60) {
                        styles[sample - 60] = style;
                        layouts[sample - 60] = layout;
                    }
                }
                if (!host.missingImages().isEmpty()) throw new IllegalStateException("Missing images: " + host.missingImages());
                JsonObject result = new JsonObject();
                result.addProperty("width", 427);
                result.addProperty("height", 240);
                result.addProperty("warmup", 60);
                result.addProperty("samples", 120);
                result.add("frame", costs(frames));
                result.add("style", costs(styles));
                result.add("layout", costs(layouts));
                return result;
            } finally {
                canvas.dispose();
                if (scene.document() != null) scene.document().close();
            }
        }
    }

    private static JsonObject costs(long[] values) {
        Arrays.sort(values);
        JsonObject result = new JsonObject();
        result.addProperty("p50_ms", values[59] / 1_000_000.0);
        result.addProperty("p95_ms", values[113] / 1_000_000.0);
        return result;
    }
}
