package dev.oriel.engine.diag;

/**
 * Something the engine did not take as written: an invalid or unsupported declaration, selector or rule, or a
 * feature it only approximates. Nothing is dropped or approximated without one. {@code code} names the kind (for
 * tools and tests), {@code source} is the page or stylesheet URL and {@code line} its 1-based line (0 when
 * unknown); {@code suggestion} says what to write instead, or is empty.
 */
public record Diagnostic(Severity severity, String code, String source, int line, String message, String suggestion) {
    public enum Severity { INFO, WARNING, ERROR }

    public Diagnostic {
        suggestion = suggestion == null ? "" : suggestion;
    }

    /** {@code source:line: severity [code] message (suggestion)}, as logs and the previewer print it. */
    public String format() {
        return source + (line > 0 ? ":" + line : "") + ": " + severity.name().toLowerCase(java.util.Locale.ROOT)
                + " [" + code + "] " + message + (suggestion.isEmpty() ? "" : " (" + suggestion + ")");
    }
}
