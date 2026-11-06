package dev.oriel.kit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the template centre shows for a page a player keeps in their own folder: a title, a subtitle and a cover, all
 * optional, all written in the page's own {@code <head>}:
 *
 * <ul>
 *   <li>title: {@code <title>}, else the folder it sits in for an {@code index.html}, else the file name;</li>
 *   <li>subtitle: {@code <meta name="oriel-subtitle" content="...">}, else {@code <meta name="description">}, else none;</li>
 *   <li>cover: {@code <meta name="oriel-cover" content="path/to/image.png">} (relative to the page), else {@code cover.png}
 *       beside the page, else {@code <page name>.png} beside it. It must be a PNG inside the folder.</li>
 * </ul>
 *
 * Text is cut to a fixed length so a long one cannot run out of its card.
 */
public final class LocalPage {
    /**
     * {@code title} is never empty; {@code subtitle} and {@code cover} are empty for none. {@code cover} is a path below the
     * folder with / separators. {@code modified} is milliseconds since the epoch and {@code size} is bytes, both 0 if unknown.
     */
    public record Info(String path, String title, String subtitle, String cover, long modified, long size) {}

    public static final int TITLE_MAX = 40, SUBTITLE_MAX = 80;
    private static final int HEAD = 16 * 1024;
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTENT = Pattern.compile("content\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private LocalPage() {}

    /** Reads {@code page} (a file below {@code root}); never throws for an unreadable or odd page, it falls back to the file name. */
    public static Info read(Path root, Path page) {
        Path base = root.toAbsolutePath().normalize(), file = page.toAbsolutePath().normalize();
        String path = base.relativize(file).toString().replace('\\', '/');
        String head = head(file);
        String name = file.getFileName().toString();
        String stem = name.endsWith(".html") ? name.substring(0, name.length() - 5) : name;
        String title = null;
        Matcher t = TITLE.matcher(head);
        if (t.find()) title = text(t.group(1));
        if (title == null || title.isEmpty()) {
            Path parent = file.getParent();
            title = name.equals("index.html") && parent != null && !parent.equals(base) ? parent.getFileName().toString() : stem;
        }
        String subtitle = meta(head, "oriel-subtitle");
        if (subtitle.isEmpty()) subtitle = meta(head, "description");
        Path dir = file.getParent() == null ? base : file.getParent();
        String cover = "";
        String declared = meta(head, "oriel-cover");
        if (!declared.isEmpty()) cover = png(base, dir.resolve(declared));
        if (cover.isEmpty()) cover = png(base, dir.resolve("cover.png"));
        if (cover.isEmpty()) cover = png(base, dir.resolve(stem + ".png"));
        long modified = 0, size = 0;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException | RuntimeException ignored) {
        }
        return new Info(path, cut(title, TITLE_MAX), cut(subtitle, SUBTITLE_MAX), cover, modified, size);
    }

    /** The {@code content} of {@code <meta name="...">} in the head, text only, or empty. */
    private static String meta(String head, String name) {
        Matcher m = Pattern.compile("<meta\\s[^>]*name\\s*=\\s*[\"']" + Pattern.quote(name) + "[\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(head);
        if (!m.find()) return "";
        Matcher c = CONTENT.matcher(m.group());
        return c.find() ? text(c.group(2)) : "";
    }

    private static String text(String raw) {
        return raw.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }

    /** At most {@code max} characters, ending in … when cut. */
    public static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** The image's path below the folder, or empty when it is not a PNG file inside it. */
    private static String png(Path base, Path image) {
        Path p = image.toAbsolutePath().normalize();
        if (!p.startsWith(base) || !p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") || !Files.isRegularFile(p)) return "";
        return base.relativize(p).toString().replace('\\', '/');
    }

    private static String head(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readNBytes(HEAD), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
