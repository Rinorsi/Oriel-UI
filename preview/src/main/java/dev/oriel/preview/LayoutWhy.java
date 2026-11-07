package dev.oriel.preview;

import dev.oriel.engine.dom.Viewport;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Headless JSONL-only CLI for flex/grid solving diagnostics. */
final class LayoutWhy {
    private LayoutWhy() {}

    static boolean requested(String[] args) {
        for (String arg : args) if (arg.equals("--why-size") || arg.equals("--why-position")) return true;
        return false;
    }

    static int run(String[] args) throws IOException {
        var remaining = new ArrayList<String>();
        boolean size = false, position = false;
        for (String arg : args) {
            if (arg.equals("--why-size")) size = true;
            else if (arg.equals("--why-position")) position = true;
            else remaining.add(arg);
        }
        var options = Preview.Options.parse(remaining.toArray(String[]::new));
        if (options.canvasTest() || options.actions() != null || options.snapshot() != null
                || options.narrate() || options.boxes() != null)
            throw new IllegalArgumentException("--why-size/--why-position require a page and cannot combine with canvas-test, actions, snapshot, narrate or boxes");
        String query = size && position ? "all" : size ? "why-size" : "why-position";
        Map<String, String> before = new LinkedHashMap<>();
        configure(before, "java.awt.headless", "true");
        configure(before, "oriel.layout.flex", "new");
        configure(before, "oriel.layout.grid", "new");
        configure(before, "oriel.layout.flex.trace", query);
        configure(before, "oriel.layout.grid.trace", query);
        configure(before, "oriel.layout.traceStdout", "true");
        configure(before, "oriel.layout.traceEnvironment", "preview");
        try {
            var roots = PreviewHost.packRoot(options.page()).stream().toList();
            try (var assets = MinecraftAssets.openForPage(roots, MinecraftAssets.findClientJar())) {
                var host = new PreviewHost(assets, new MinecraftFont(assets));
                host.logTo(System.err);
                host.setLanguage(options.language());
                var scene = new PageScene(host, PreviewHost.pageUrl(options.page()),
                        options.data() == null ? null : Files.readString(options.data()),
                        new Viewport(options.width(), options.height(), options.scale()));
                try {
                    for (int i = 0; i < options.frames() && scene.error() == null; i++)
                        scene.frame(i * 16.0, options.width(), options.height(), options.scale());
                    if (scene.error() != null) {
                        System.err.println(scene.error().getMessage());
                        return 1;
                    }
                    PageLint.report(scene.document(), host);
                    return options.strict() && host.problems() > 0 ? 3 : 0;
                } finally {
                    if (scene.document() != null) scene.document().close();
                }
            }
        } finally {
            before.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    private static void configure(Map<String, String> before, String key, String value) {
        before.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }
}
