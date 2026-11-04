package dev.oriel.preview;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/** Opt-in CPU frame-cost regression gate; never rewrites pages, images, styles or layout. */
public final class FrameBenchmark {
    private FrameBenchmark() {}

    public static void main(String[] args) throws Exception {
        Path pages = Path.of(args[0]), baseline = Path.of(args[1]), output = Path.of(args[2]);
        int warmup = Integer.getInteger("oriel.benchWarmup", 60), frames = Integer.getInteger("oriel.benchFrames", 120);
        int rounds = Integer.getInteger("oriel.benchRounds", 3);
        double tolerance = Double.parseDouble(System.getProperty("oriel.benchTolerance", "0.25"));
        boolean update = Boolean.getBoolean("oriel.benchUpdate");
        if (warmup < 10 || frames < 20 || rounds < 3 || rounds % 2 == 0 || !Double.isFinite(tolerance) || tolerance < 0 || tolerance > 1)
            throw new IllegalArgumentException("Invalid benchmark configuration");
        OrielKit.register();
        Path jar = MinecraftAssets.findClientJar().orElseThrow(() -> new IllegalStateException("Benchmark requires ORIEL_MC_JAR"));
        List<Path> targets;
        try (var paths = Files.walk(pages)) { targets = paths.filter(p -> p.toString().endsWith(".html")).sorted().toList(); }
        if (targets.isEmpty()) throw new IllegalArgumentException("No kit pages");
        JsonObject result = new JsonObject(), costs = new JsonObject();
        result.addProperty("warmup", warmup);
        result.addProperty("frames", frames);
        result.addProperty("rounds", rounds);
        result.addProperty("aggregate", "median-of-independent-rounds");
        result.addProperty("viewport", "427x240@1");
        result.addProperty("backend", "ImageCanvas");
        result.addProperty("clearBeforeFrame", true);
        result.addProperty("java", System.getProperty("java.version"));
        result.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        result.addProperty("processors", Runtime.getRuntime().availableProcessors());
        result.add("pages", costs);
        for (Path page : targets) {
            double[] medians = new double[rounds], tails = new double[rounds];
            for (int round = 0; round < rounds; round++) {
                double[] cost = measure(page, jar, warmup, frames);
                medians[round] = cost[0];
                tails[round] = cost[1];
            }
            Arrays.sort(medians);
            Arrays.sort(tails);
            JsonObject cost = new JsonObject();
            cost.addProperty("p50_ms", medians[rounds / 2]);
            cost.addProperty("p95_ms", tails[rounds / 2]);
            String key = pages.relativize(page).toString().replace((char)92, '/');
            costs.add(key, cost);
            System.out.println(key + " " + cost);
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(result) + (char)10;
        Files.writeString(output, json);
        if (update) {
            Files.createDirectories(baseline.toAbsolutePath().getParent());
            Files.writeString(baseline, json);
            System.out.println("Updated baseline for review: " + baseline);
            return;
        }
        JsonObject expected = JsonParser.parseString(Files.readString(baseline)).getAsJsonObject();
        for (String key : List.of("warmup", "frames", "rounds", "aggregate", "viewport", "backend", "clearBeforeFrame"))
            if (!result.get(key).equals(expected.get(key))) throw new IllegalStateException("Benchmark configuration differs: " + key);
        JsonObject previous = expected.getAsJsonObject("pages");
        if (!previous.keySet().equals(costs.keySet())) throw new IllegalStateException("Page set changed; review and regenerate baseline");
        int regressions = 0;
        for (String page : costs.keySet()) for (String quantile : List.of("p50_ms", "p95_ms")) {
            double old = previous.getAsJsonObject(page).get(quantile).getAsDouble();
            double now = costs.getAsJsonObject(page).get(quantile).getAsDouble();
            if (now > old * (1 + tolerance)) {
                regressions++;
                System.err.println("REGRESSION " + page + " " + quantile + ": " + now + " > " + old + " * " + (1 + tolerance));
            }
        }
        if (regressions != 0) throw new IllegalStateException(regressions + " frame-cost regressions; details: " + output);
        System.out.println("Frame benchmark PASS: " + targets.size() + " pages");
    }

    private static double percentile(long[] sorted, double fraction) {
        return sorted[(int) Math.ceil(sorted.length * fraction) - 1] / 1_000_000.0;
    }

    private static double[] measure(Path page, Path jar, int warmup, int frames) throws java.io.IOException {
        try (MinecraftAssets assets = MinecraftAssets.open(PreviewHost.packRoot(page).stream().toList(), java.util.Optional.of(jar))) {
            MinecraftFont font = new MinecraftFont(assets);
            PreviewHost host = new PreviewHost(assets, font);
            host.quiet(true);
            PageScene scene = new PageScene(host, PreviewHost.pageUrl(page), null, new Viewport(427, 240, 1));
            BufferedImage image = new BufferedImage(427, 240, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            ImageCanvas canvas = new ImageCanvas(image, 1, assets, font);
            long[] samples = new long[frames];
            try {
                for (int frame = 0; frame < warmup + frames; frame++) {
                    Arrays.fill(pixels, 0); // Never feed the previous frame back into alpha/blend effects.
                    long start = System.nanoTime();
                    canvas.reset();
                    scene.frame(frame * (1000.0 / 60), 427, 240, 1);
                    scene.paint(canvas);
                    long elapsed = System.nanoTime() - start;
                    if (scene.error() != null) throw new IllegalStateException(page.toString(), scene.error());
                    if (frame >= warmup) samples[frame - warmup] = elapsed;
                }
                if (!host.missingImages().isEmpty()) throw new IllegalStateException("Missing assets: " + host.missingImages());
            } finally {
                canvas.dispose();
                if (scene.document() != null) scene.document().close();
            }
            Arrays.sort(samples);
            return new double[] {percentile(samples, .50), percentile(samples, .95)};
        }
    }
}
