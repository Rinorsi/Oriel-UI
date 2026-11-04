package dev.oriel.engine.parse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;

/** Migration-only field comparisons. Reports are explicit external files, never implicit repository outputs. */
public final class ParserAudit {
    public enum Mode { OLD, NEW, BOTH }
    private static final ThreadLocal<Mode> OVERRIDE = new ThreadLocal<>();
    private ParserAudit() {}

    public static Mode mode() {
        if (OVERRIDE.get() != null) return OVERRIDE.get();
        return switch (System.getProperty("oriel.parser", "old")) {
            case "old" -> Mode.OLD;
            case "new" -> Mode.NEW;
            case "both" -> Mode.BOTH;
            default -> throw new IllegalArgumentException("oriel.parser must be old, new or both");
        };
    }

    public static <T> T using(Mode mode, java.util.function.Supplier<T> operation) {
        Mode before = OVERRIDE.get(); OVERRIDE.set(mode);
        try { return operation.get(); }
        finally { if (before == null) OVERRIDE.remove(); else OVERRIDE.set(before); }
    }

    public static void compare(String kind, String source, Map<String, String> oldFields, Map<String, String> newFields) {
        var fields = new TreeSet<>(oldFields.keySet()); fields.addAll(newFields.keySet());
        var differences = new ArrayList<String>();
        for (String key : fields) {
            String a = oldFields.get(key), b = newFields.get(key);
            if (!java.util.Objects.equals(a, b)) differences.add("{\"field\":" + json(key)
                    + ",\"old\":" + json(a) + ",\"new\":" + json(b) + "}");
        }
        write("{\"kind\":" + json(kind) + ",\"source\":" + json(source) + ",\"comparedFields\":" + fields.size()
                + ",\"differences\":[" + String.join(",", differences) + "]}\n");
    }

    private static synchronized void write(String line) {
        String destination = System.getProperty("oriel.parser.report");
        if (destination == null) throw new IllegalStateException("both mode requires oriel.parser.report outside the repository");
        Path path = Path.of(destination);
        if (!path.isAbsolute()) throw new IllegalArgumentException("oriel.parser.report must be an absolute path");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static String json(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
