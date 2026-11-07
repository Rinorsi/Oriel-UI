package dev.oriel.engine.css;

import dev.oriel.engine.dom.Element;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stage-3 migration switch. Both mode returns legacy results and fails immediately on semantic differences. */
final class SelectorAudit {
    enum Mode { OLD, NEW, BOTH }
    private static BufferedWriter writer;
    private static final Map<String, Long> records = new LinkedHashMap<>();

    private SelectorAudit() {}

    static Mode mode() {
        return switch (System.getProperty("oriel.selector", "old")) {
            case "old" -> Mode.OLD;
            case "new" -> Mode.NEW;
            case "both" -> Mode.BOTH;
            default -> throw new IllegalArgumentException("oriel.selector must be old, new or both");
        };
    }

    static synchronized void compare(String kind, Element element, boolean equal, String fields, String detail) {
        String path = System.getProperty("oriel.selector.audit");
        if (path != null) {
            try {
                if (writer == null) {
                    writer = Files.newBufferedWriter(Path.of(path.replace("%p", Long.toString(ProcessHandle.current().pid()))),
                            StandardCharsets.UTF_8);
                    Runtime.getRuntime().addShutdownHook(new Thread(SelectorAudit::close, "oriel-selector-audit"));
                }
                String record = "{\"kind\":\"" + kind + "\",\"equal\":" + equal + "," + fields;
                records.merge(record, 1L, Long::sum);
            } catch (IOException ex) {
                throw new UncheckedIOException("Cannot write selector audit: " + path, ex);
            }
        }
        if (!equal) throw new IllegalStateException("Selector dual-run mismatch (" + kind + ") at "
                + (element.ownerDocument() == null ? "" : element.ownerDocument().url()) + " "
                + element.tagName() + "#" + element.id() + ": " + fields + "; " + detail);
    }

    private static synchronized void close() {
        try {
            if (writer != null) {
                for (var record : records.entrySet()) {
                    writer.write(record.getKey() + ",\"count\":" + record.getValue() + "}\n");
                }
                writer.close();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
