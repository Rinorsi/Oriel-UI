package dev.oriel.engine.css;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.PseudoElement;
import dev.oriel.engine.style.ComputedStyle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/** Explicit migration oracle. Reflection and audit accounting are initialized only in both mode. */
final class CascadeAudit {
    enum Mode { OLD, NEW, BOTH }

    private CascadeAudit() {}

    static Mode mode() {
        return switch (System.getProperty("oriel.cascade", "old")) {
            case "old" -> Mode.OLD;
            case "new" -> Mode.NEW;
            case "both" -> Mode.BOTH;
            default -> throw new IllegalArgumentException("oriel.cascade must be old, new or both");
        };
    }

    private static final class Oracle {
        static final Field[] FIELDS = Arrays.stream(ComputedStyle.class.getFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);
        static final LongAdder COMPUTATIONS = new LongAdder(), CHECKED = new LongAdder(), MISMATCHES = new LongAdder();
        static {
            String path = System.getProperty("oriel.cascade.audit");
            if (path != null) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.writeString(Path.of(path.replace("%p", Long.toString(ProcessHandle.current().pid()))),
                            "{\"kind\":\"computed-audit\",\"computations\":" + COMPUTATIONS.sum()
                                    + ",\"fields\":" + CHECKED.sum() + ",\"mismatches\":" + MISMATCHES.sum() + "}\n");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, "oriel-cascade-audit"));
        }
    }

    static void compare(Element element, PseudoElement pseudo, ComputedStyle old, ComputedStyle fresh) {
        Oracle.COMPUTATIONS.increment();
        if (old == null || fresh == null) {
            if (old != fresh) mismatch(element, pseudo, "presence", old, fresh);
            return;
        }
        try {
            for (Field field : Oracle.FIELDS) {
                Object expected = field.get(old), actual = field.get(fresh);
                Oracle.CHECKED.increment();
                if (!Objects.deepEquals(expected, actual)) mismatch(element, pseudo, field.getName(), expected, actual);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot compare computed fields", e);
        }
    }

    private static void mismatch(Element element, PseudoElement pseudo, String field, Object old, Object fresh) {
        Oracle.MISMATCHES.increment();
        throw new IllegalStateException("Computed dual-run mismatch at " + element.ownerDocument().url() + " "
                + element + "::" + pseudo + " field=" + field + " old=" + old + " new=" + fresh);
    }
}
