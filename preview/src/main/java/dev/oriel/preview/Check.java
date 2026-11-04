package dev.oriel.preview;

import dev.oriel.engine.dom.Viewport;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code preview --check <dir or page>}: loads every page headless with its controller, runs a second of frames at
 * two GUI sizes, and reports each page's problems: an engine or controller error, CSS the engine did not take as
 * written (diagnostics at warning level), images that do not resolve, and design rules ({@link DesignLint}). One line per page, then a summary;
 * exits 1 when any page failed. Meant as the gate an AI (or CI) runs after editing a page.
 */
final class Check {
    /** The sizes every page must survive: the common 1080p GUI and a small window. */
    static final int[][] SIZES = {{640, 360}, {427, 240}};
    static final int FRAMES = 60;

    private Check() {}

    static int run(Path target) throws IOException {
        List<Path> pages = new ArrayList<>();
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                walk.filter(p -> p.toString().endsWith(".html")).sorted().forEach(pages::add);
            }
        } else {
            pages.add(target);
        }
        if (pages.isEmpty()) {
            System.err.println("no .html pages under " + target);
            return 2;
        }
        Optional<Path> jar = MinecraftAssets.findClientJar();
        int failed = 0, warned = 0;
        for (Path page : pages) {
            List<String> problems = new ArrayList<>();
            int warnings = 0;
            for (int[] size : SIZES) {
                MinecraftAssets assets = MinecraftAssets.open(PreviewHost.packRoot(page).stream().toList(), jar);
                MinecraftFont font = new MinecraftFont(assets);
                PreviewHost host = new PreviewHost(assets, font);
                host.quiet(true);
                PageScene scene = new PageScene(host, PreviewHost.pageUrl(page), null, new Viewport(size[0], size[1], 2));
                FrameRenderer frames = new FrameRenderer(assets, font);
                for (int i = 0; i < FRAMES && scene.error() == null; i++) frames.render(scene, size[0] * 2, size[1] * 2, 2, i * 16.0, null);
                String at = " (" + size[0] + "x" + size[1] + ")";
                if (scene.error() != null) problems.add("error" + at + ": " + firstLine(scene.error()));
                for (String url : host.missingImages()) if (!problems.contains("missing image: " + url)) problems.add("missing image: " + url);
                if (size == SIZES[0]) {
                    for (String d : host.diagnosticLines()) problems.add("css: " + d);
                    warnings = host.problems();
                    if (scene.document() != null && scene.error() == null) {
                        List<String> design = DesignLint.run(scene.document());
                        for (String d : design) problems.add("design: " + d);
                        warnings += design.size();
                    }
                }
            }
            boolean bad = problems.stream().anyMatch(p -> p.startsWith("error") || p.startsWith("missing"));
            if (bad) failed++;
            else if (warnings > 0) warned++;
            System.out.println((bad ? "FAIL " : warnings > 0 ? "WARN " : "OK   ") + page.getFileName());
            for (String p : problems) System.out.println("     " + p);
        }
        System.out.println(pages.size() + " page(s): " + (pages.size() - failed - warned) + " ok, " + warned + " with warnings, " + failed + " failed");
        return failed > 0 ? 1 : 0;
    }

    private static String firstLine(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        String m = root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
        StackTraceElement[] st = root.getStackTrace();
        return st.length == 0 ? m : m + " at " + st[0];
    }
}
