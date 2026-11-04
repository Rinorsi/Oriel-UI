package dev.oriel.engine.html;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.parse.ParserAudit;
import dev.oriel.engine.parse.SourceLocation;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** Parses HTML documents and inert fragments with the Oriel tokenizer and tree builder. */
public final class HtmlParser {
    static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
    static final Set<String> RAW_TEXT = Set.of("script", "style");
    private HtmlParser() {}

    public static void parseInto(Document document, String markup) {
        ParserAudit.mode(); String source = markup == null ? "" : markup;
        HtmlTreeBuilder.document(document, source, issues(document, source));
    }

    public static List<Node> parseFragment(Document document, String markup) {
        ParserAudit.mode(); String source = markup == null ? "" : markup;
        return HtmlTreeBuilder.fragment(document, source, issues(document, source));
    }

    private static BiConsumer<Integer, String> issues(Document document, String source) {
        SourceLocation.Index positions = new SourceLocation.Index(source);
        return (offset, message) -> {
            SourceLocation location = positions.at(offset);
            document.diagnostics().report(new Diagnostic(Diagnostic.Severity.WARNING, "html-syntax", document.url(),
                    location.line(), location.column(), message, "fix the markup at the reported position"));
        };
    }
}
