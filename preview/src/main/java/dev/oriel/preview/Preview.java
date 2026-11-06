package dev.oriel.preview;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.css.CssColors;
import dev.oriel.engine.dom.Viewport;
import dev.oriel.kit.OrielKit;
import dev.oriel.preview.host.PreviewHost;
import dev.oriel.preview.render.MinecraftAssets;
import dev.oriel.preview.render.MinecraftFont;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The standalone previewer: a window that renders a page live, or a headless snapshot to PNG. */
public final class Preview {
    private static final String USAGE = """
            Usage: preview <page.html> [--scale N] [--size WxH] [--data data.json] [--snapshot out.png [--frames N] [--boxes selector]] [--narrate] [--strict]
                   preview <page.html> --actions actions.txt [--out dir] [--snapshot out.png] [--scale N] [--size WxH] [--narrate]
                   preview --canvas-test [--scale N] [--size WxH] [--snapshot out.png]
                   preview --check <dir or page.html> [--strict] [--lang language] [--report result.json]
            The GUI size defaults to 427x240 (1280x720 at GUI scale 3), the scale to 2.
            --background <opaque CSS color> replaces the world backdrop in windows, snapshots and actions.
            --lang <language> selects a language such as zh_cn (default) or en_us; --check also accepts it.
            Diagnostics (CSS the engine did not take as written) go to stderr; with --strict a warning fails the run
            (exit code 3).""";

    /** Command line options. Sizes are GUI px. */
    record Options(Path page, boolean canvasTest, int scale, int width, int height, Path data, Path snapshot, int frames,
                   Path actions, Path out, boolean narrate, boolean strict, String boxes, Integer background, String language) {
        static Options parse(String... args) {
            Path page = null, data = null, snapshot = null, actions = null, out = null;
            String boxes = null;
            String language = "zh_cn";
            Integer background = null;
            boolean canvasTest = false, narrate = false, strict = false;
            int scale = 2, width = CanvasTest.WIDTH, height = CanvasTest.HEIGHT, frames = 1;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--canvas-test" -> canvasTest = true;
                    case "--scale" -> scale = positive(value(args, ++i, arg));
                    case "--frames" -> frames = positive(value(args, ++i, arg));
                    case "--data" -> data = Path.of(value(args, ++i, arg));
                    case "--snapshot" -> snapshot = Path.of(value(args, ++i, arg));
                    case "--actions" -> actions = Path.of(value(args, ++i, arg));
                    case "--out" -> out = Path.of(value(args, ++i, arg));
                    case "--narrate" -> narrate = true;
                    case "--strict" -> strict = true;
                    case "--boxes" -> boxes = value(args, ++i, arg);
                    case "--lang" -> language = value(args, ++i, arg);
                    case "--background" -> {
                        String color = value(args, ++i, arg);
                        background = CssColors.parse(color, 0xFF000000);
                        if (background == null || (background >>> 24) != 255) {
                            throw new IllegalArgumentException("--background needs an opaque CSS color, got " + color);
                        }
                    }
                    case "--size" -> {
                        String[] size = value(args, ++i, arg).split("x");
                        if (size.length != 2) throw new IllegalArgumentException("--size takes WxH, e.g. 427x240");
                        width = positive(size[0]);
                        height = positive(size[1]);
                    }
                    default -> {
                        if (arg.startsWith("--") || page != null) throw new IllegalArgumentException("Unexpected argument: " + arg);
                        page = Path.of(arg);
                    }
                }
            }
            if (canvasTest == (page != null)) throw new IllegalArgumentException("Give either a page or --canvas-test");
            if ((actions != null || narrate) && canvasTest) {
                throw new IllegalArgumentException((narrate ? "--narrate" : "--actions") + " needs a page");
            }
            return new Options(page, canvasTest, scale, width, height, data, snapshot, frames, actions, out, narrate, strict, boxes, background, language);
        }

        /** Runs without a window: a snapshot or a script of actions. */
        boolean headless() {
            return snapshot != null || actions != null;
        }

        /** Where {@code shot} actions write: {@code --out}, else beside the snapshot, else the working directory. */
        Path shots() {
            if (out != null) return out;
            Path parent = snapshot == null ? null : snapshot.toAbsolutePath().getParent();
            return parent != null ? parent : Path.of("");
        }

        private static String value(String[] args, int i, String option) {
            if (i >= args.length) throw new IllegalArgumentException(option + " needs a value");
            return args[i];
        }

        private static int positive(String value) {
            try {
                int n = Integer.parseInt(value);
                if (n > 0) return n;
            } catch (NumberFormatException e) {
                // reported below
            }
            throw new IllegalArgumentException("Expected a positive number, got " + value);
        }
    }

    private Preview() {}

    public static void main(String[] args) throws IOException {
        // The Ore presets' Java controllers attach to their pages as they load.
        OrielKit.register();
        if (args.length > 0 && args[0].equals("--check")) {
            System.setProperty("java.awt.headless", "true");
            try {
                System.exit(check(args));
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage() + "\n" + USAGE);
                System.exit(2);
            }
            return;
        }
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage() + "\n" + USAGE);
            System.exit(2);
            return;
        }
        if (options.headless()) System.setProperty("java.awt.headless", "true");

        Optional<Path> jar = MinecraftAssets.findClientJar();
        System.out.println(jar.map(j -> "Minecraft assets: " + j).orElse("No Minecraft " + MinecraftAssets.MINECRAFT_VERSION
                + " jar found (set -D" + MinecraftAssets.JAR_PROPERTY + " or " + MinecraftAssets.JAR_ENV + "); using a fallback font"));
        Optional<Path> pack = options.canvasTest() ? Optional.empty() : PreviewHost.packRoot(options.page());
        MinecraftAssets assets = MinecraftAssets.openForPage(pack.stream().toList(), jar);
        MinecraftFont font = new MinecraftFont(assets);
        PreviewHost host = new PreviewHost(assets, font);
        host.setLanguage(options.language());
        Scene scene = options.canvasTest() ? new CanvasTest(host) : new PageScene(host, PreviewHost.pageUrl(options.page()),
                options.data() == null ? null : Files.readString(options.data()),
                new Viewport(options.width(), options.height(), options.scale()));
        if (options.narrate()) ((PageScene) scene).narrateTo(System.out);

        if (options.actions() != null) {
            System.exit(strict(actions((PageScene) scene, new FrameRenderer(assets, font, options.background()), options), host, options));
        }
        if (options.snapshot() != null) {
            System.exit(strict(snapshot(scene, new FrameRenderer(assets, font, options.background()), options), host, options));
        }
        SwingUtilities.invokeLater(() -> new PreviewWindow(scene, host, options.scale(), options.width(), options.height(), options.background()).show());
    }

    /**
     * Runs the {@code --actions} script, then writes the last frame to {@code --snapshot} if given. Exits 1 if the
     * page failed, 2 if the script is malformed or names something that is not there.
     */
    private static int actions(PageScene scene, FrameRenderer renderer, Options options) throws IOException {
        try {
            Actions actions = Actions.parse(Files.readString(options.actions()));
            Path shots = options.shots();
            Files.createDirectories(shots.toAbsolutePath());
            BufferedImage image = actions.run(scene, renderer, options.width(), options.height(), options.scale(), shots,
                    System.out);
            if (options.snapshot() != null) {
                ImageIO.write(image, "png", options.snapshot().toFile());
                System.out.println("Wrote " + options.snapshot().toAbsolutePath());
            }
            if (options.boxes() != null) printBoxes(scene, options.boxes());
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(options.actions() + ": " + e.getMessage());
            return 2;
        }
        return scene.error() == null ? 0 : 1;
    }

    static int check(String[] args) throws IOException {
        if (args.length < 2 || args[1].startsWith("--")) throw new IllegalArgumentException("--check needs a target");
        String language = "zh_cn";
        boolean strict = false;
        Path report = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--strict" -> strict = true;
                case "--report" -> report = Path.of(Options.value(args, ++i, "--report"));
                case "--lang" -> {
                    language = Options.value(args, ++i, "--lang");
                    if (language.startsWith("--")) throw new IllegalArgumentException("--lang needs a language");
                }
                default -> throw new IllegalArgumentException("Unknown check option: " + args[i]);
            }
        }
        return Check.run(java.nio.file.Path.of(args[1]), language, strict, report);
    }

    /** With {@code --strict}, a run that otherwise passed fails with 3 when the page had warnings or errors. */
    private static int strict(int code, PreviewHost host, Options options) {
        if (code == 0 && options.strict() && host.problems() > 0) {
            System.err.println(host.problems() + " diagnostic(s) at warning level or above (--strict)");
            return 3;
        }
        return code;
    }

    /** Renders {@code frames} frames 16 ms apart and writes the last; exits non-zero if the page failed. */
    private static int snapshot(Scene scene, FrameRenderer renderer, Options options) throws IOException {
        BufferedImage image = null;
        for (int i = 0; i < options.frames(); i++) {
            image = renderer.render(scene, options.width() * options.scale(), options.height() * options.scale(),
                    options.scale(), i * 16.0, null);
        }
        ImageIO.write(image, "png", options.snapshot().toFile());
        System.out.println("Wrote " + options.snapshot().toAbsolutePath());
        if (options.boxes() != null && scene instanceof PageScene page) printBoxes(page, options.boxes());
        return scene.error() == null ? 0 : 1;
    }

    /**
     * Prints the border box of every element matching {@code selector} in GUI px ({@code --boxes}), one line each:
     * {@code x,y wxh  tag#id.classes}. For finding what pushes a layout around without guessing.
     */
    private static void printBoxes(PageScene page, String selector) {
        for (Element e : page.document().querySelectorAll(selector)) {
            float[] r = e.getBoundingClientRect();
            StringBuilder name = new StringBuilder(e.tagName());
            String id = e.getAttribute("id"), cls = e.getAttribute("class");
            if (id != null) name.append('#').append(id);
            if (cls != null && !cls.isBlank()) name.append('.').append(String.join(".", cls.trim().split("\\s+")));
            System.out.printf("%6.1f,%6.1f %6.1fx%-6.1f %s%n", r[0], r[1], r[2], r[3], name);
        }
    }
}
