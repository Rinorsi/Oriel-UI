package dev.oriel.engine.css;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.parse.SourceLocation;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Syntax diagnostics scoped to a stylesheet or declaration input, never to mutable parser globals. */
final class CssIssues {
    private record Context(String url, Consumer<Diagnostic> sink) {}
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private CssIssues() {}

    static <T> T in(String url, Consumer<Diagnostic> sink, Supplier<T> operation) {
        Context before = CURRENT.get(); CURRENT.set(new Context(url, sink));
        try { return operation.get(); }
        finally { if (before == null) CURRENT.remove(); else CURRENT.set(before); }
    }

    static void report(String source, int offset, String message) {
        Context context = CURRENT.get();
        if (context == null) return; // Internal value parsers expose bad tokens to their enclosing declaration parser.
        SourceLocation location = SourceLocation.at(source, offset);
        context.sink().accept(new Diagnostic(Diagnostic.Severity.WARNING, "css-syntax", context.url(),
                location.line(), location.column(), message, "fix the CSS at the reported position"));
    }
}
