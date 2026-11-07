package dev.oriel.preview;

import com.google.gson.Gson;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.engine.paint.DisplayList;
import dev.oriel.engine.paint.Painter;
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
import java.util.Map;

/** Deterministic same-frame paint comparison. No input simulation or GPU correctness is implied. */
public final class PaintComparison {
    private PaintComparison() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("PaintComparison page frames width height report.json");
        Path page = Path.of(args[0]);
        int frames = Integer.parseInt(args[1]), width = Integer.parseInt(args[2]), height = Integer.parseInt(args[3]);
        if (frames < 1 || frames > 600 || width < 1 || width > 1920 || height < 1 || height > 1080) {
            throw new IllegalArgumentException("Comparison dimensions or frames exceed limits");
        }
        OrielKit.register();
        Path jar = MinecraftAssets.findClientJar().orElseThrow(() -> new IllegalStateException("Comparison requires ORIEL_MC_JAR"));
        try (MinecraftAssets assets = MinecraftAssets.openForPage(PreviewHost.packRoot(page).stream().toList(), java.util.Optional.of(jar))) {
            MinecraftFont font = new MinecraftFont(assets);
            PreviewHost host = new PreviewHost(assets, font);
            host.quiet(true);
            String url = PreviewHost.pageUrl(page);
            Document doc = Document.parse(host, url, Files.readString(page), null, new Viewport(width, height, 1));
            ImageCanvas old = canvas(width, height, assets, font), current = canvas(width, height, assets, font);
            int[] oldPixels = pixels(old), currentPixels = pixels(current);
            Painter painter = doc.painter();
            painter.trace(true);
            long commands = 0;
            try {
                for (int frame = 0; frame < frames; frame++) {
                    doc.frame(frame * 16.0);
                    Arrays.fill(oldPixels, 0);
                    old.reset();
                    painter.mode(Painter.Mode.OLD);
                    doc.paint(old);
                    if (doc.error() != null) throw new IllegalStateException(url, doc.error());
                    DisplayList expected = painter.lastDisplayList();
                    commands += expected.size();
                    for (Painter.Mode mode : new Painter.Mode[] {Painter.Mode.NEW, Painter.Mode.BOTH}) {
                        Arrays.fill(currentPixels, 0);
                        current.reset();
                        painter.mode(mode);
                        doc.paint(current);
                        if (doc.error() != null) throw new IllegalStateException(url, doc.error());
                        expected.assertEquivalent(painter.lastDisplayList(), url + " frame=" + frame + " mode=" + mode);
                        int mismatch = Arrays.mismatch(oldPixels, currentPixels);
                        if (mismatch >= 0) throw new IllegalStateException(url + " frame=" + frame + " mode=" + mode
                                + " pixel=" + mismatch % width + "," + mismatch / width
                                + " old=" + Integer.toHexString(oldPixels[mismatch]) + " new=" + Integer.toHexString(currentPixels[mismatch]));
                    }
                }
                if (!host.missingImages().isEmpty()) throw new IllegalStateException("Missing images: " + host.missingImages());
                Files.writeString(Path.of(args[4]), new Gson().toJson(Map.of("page", page.toAbsolutePath().toString(),
                        "frames", frames, "width", width, "height", height, "commands", commands,
                        "rgbaComparisons", (long) width * height * frames * 2, "cache", painter.cacheStatistics(),
                        "differences", 0, "backend", "ImageCanvas")));
            } finally {
                doc.close(); old.dispose(); current.dispose();
            }
        }
    }

    private static ImageCanvas canvas(int width, int height, MinecraftAssets assets, MinecraftFont font) {
        return new ImageCanvas(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), 1, assets, font);
    }

    private static int[] pixels(ImageCanvas canvas) {
        return ((DataBufferInt) canvas.image().getRaster().getDataBuffer()).getData();
    }
}
