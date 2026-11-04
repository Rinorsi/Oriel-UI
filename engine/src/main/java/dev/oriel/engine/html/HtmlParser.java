package dev.oriel.engine.html;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.dom.Text;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.parse.ParserAudit;
import dev.oriel.engine.parse.SourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/** Stable entry point for HTML documents and inert markup fragments. */
public final class HtmlParser {
    static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
    static final Set<String> RAW_TEXT = Set.of("script", "style");
    private HtmlParser() {}

    public static void parseInto(Document document, String markup) {
        String source = markup == null ? "" : markup;
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) { LegacyHtmlParser.parseInto(document, source); return; }
        Document reference = ParserAudit.mode() == ParserAudit.Mode.BOTH ? shadow(document) : null;
        if (reference != null) LegacyHtmlParser.parseInto(reference, source);
        int previous = document.diagnostics().all().size();
        HtmlTreeBuilder.document(document, source, issues(document, source));
        if (reference != null) {
            ParserAudit.compare("html-dom", document.url(), fields(reference.childNodes()), fields(document.childNodes()));
            ParserAudit.compare("html-diagnostics", document.url(), diagnostics(reference.diagnostics().all()),
                    diagnostics(document.diagnostics().all().subList(previous, document.diagnostics().all().size())));
        }
    }

    public static List<Node> parseFragment(Document document, String markup) {
        String source = markup == null ? "" : markup;
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) return LegacyHtmlParser.parseFragment(document, source);
        Document reference = ParserAudit.mode() == ParserAudit.Mode.BOTH ? shadow(document) : null;
        List<Node> old = reference == null ? null : LegacyHtmlParser.parseFragment(reference, source);
        int previous = document.diagnostics().all().size();
        List<Node> result = HtmlTreeBuilder.fragment(document, source, issues(document, source));
        if (reference != null) {
            ParserAudit.compare("html-fragment", document.url(), fields(old), fields(result));
            ParserAudit.compare("html-fragment-diagnostics", document.url(), diagnostics(reference.diagnostics().all()),
                    diagnostics(document.diagnostics().all().subList(previous, document.diagnostics().all().size())));
        }
        return result;
    }

    private static BiConsumer<Integer, String> issues(Document document, String source) {
        return (offset, message) -> {
            SourceLocation location = SourceLocation.at(source, offset);
            document.diagnostics().report(new Diagnostic(Diagnostic.Severity.WARNING, "html-syntax", document.url(),
                    location.line(), location.column(), message, "fix the markup at the reported position"));
        };
    }

    private static Document shadow(Document document) {
        Document shadow = Document.create(new Host() {
            public FontMetrics fonts() { return document.host().fonts(); }
            public String loadText(String url) { return document.host().loadText(url); }
            public dev.oriel.engine.Limits limits() { return document.limits(); }
            public Map<String, java.util.function.Function<Element, dev.oriel.engine.host.ReplacedContent>> replacedElements() {
                return document.host().replacedElements();
            }
            public void diagnostic(Diagnostic diagnostic) {}
        }, document.url());
        shadow.removeAllChildren();
        return shadow;
    }

    private static Map<String, String> fields(List<Node> roots) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("children", String.valueOf(roots.size()));
        for (int i = 0; i < roots.size(); i++) fields(roots.get(i), "/" + i, fields);
        return fields;
    }
    private static void fields(Node node, String at, Map<String, String> fields) {
        fields.put(at + "/type", node.nodeName());
        if (node instanceof Text text) fields.put(at + "/data", text.data());
        if (node instanceof Element element) element.attributes().forEach((key, value) -> fields.put(at + "/@" + key, value));
        fields.put(at + "/children", String.valueOf(node.childNodes().size()));
        for (int i = 0; i < node.childNodes().size(); i++) fields(node.childNodes().get(i), at + "/" + i, fields);
    }
    private static Map<String, String> diagnostics(List<Diagnostic> diagnostics) {
        Map<String, String> fields = new TreeMap<>();
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i); String at = "diagnostics/" + i;
            fields.put(at + "/code", d.code()); fields.put(at + "/severity", d.severity().name());
            fields.put(at + "/source", d.source()); fields.put(at + "/line", String.valueOf(d.line()));
            fields.put(at + "/column", String.valueOf(d.column())); fields.put(at + "/message", d.message());
            fields.put(at + "/suggestion", d.suggestion());
        }
        return fields;
    }
}
