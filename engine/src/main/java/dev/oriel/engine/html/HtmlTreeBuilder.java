package dev.oriel.engine.html;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.dom.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** UI HTML tree construction: document scaffolding, scoped implied ends and inert fragments. */
final class HtmlTreeBuilder {
    private static final Set<String> METADATA = Set.of("base", "link", "meta", "style", "title", "script", "template");
    private static final Set<String> BLOCK_STARTS = Set.of("address", "article", "aside", "blockquote", "details", "dialog", "div",
            "dl", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
            "hgroup", "hr", "main", "menu", "nav", "ol", "p", "pre", "section", "table", "ul");
    private final Document document;
    private final Node root;
    private final boolean fragment;
    private final HtmlInput input;
    private final BiConsumer<Integer, String> issue;
    private final ArrayDeque<Element> open = new ArrayDeque<>();
    private Element html, head, body;
    private Node textParent;
    private final StringBuilder text = new StringBuilder();

    private HtmlTreeBuilder(Document document, String source, Node root, boolean fragment, BiConsumer<Integer, String> issue) {
        this.document = document; this.root = root; this.fragment = fragment; this.issue = issue;
        input = new HtmlInput(source, issue);
    }
    static void document(Document document, String source, BiConsumer<Integer, String> issue) {
        new HtmlTreeBuilder(document, source, document, false, issue).build();
    }
    static List<Node> fragment(Document document, String source, BiConsumer<Integer, String> issue) {
        Element container = document.createElement("template");
        new HtmlTreeBuilder(document, source, container, true, issue).build();
        List<Node> children = new ArrayList<>(container.childNodes());
        for (Node child : children) container.removeChild(child);
        return children;
    }
    private void build() {
        try {
            for (;;) {
                HtmlInput.Lexeme token = input.next();
                switch (token.kind()) {
                    case EOF -> { if (!fragment) body(); return; }
                    case START -> { flush(); start(token); }
                    case END -> { flush(); end(token); }
                    case TEXT -> characters(token.data());
                    case COMMENT, DOCTYPE -> { }
                }
            }
        } finally { flush(); }
    }
    private Element html() {
        if (html == null) html = document.appendChild(document.createElement("html"));
        return html;
    }
    private Element head() {
        if (head == null) { head = document.createElement("head"); html().insertBefore(head, html().firstChild()); }
        return head;
    }
    private Element body() {
        head();
        if (body == null) { body = html().appendChild(document.createElement("body")); open.clear(); open.push(body); }
        return body;
    }
    private Node parent() { return !open.isEmpty() ? open.peek() : fragment ? root : body(); }
    private void start(HtmlInput.Lexeme token) {
        String tag = token.name();
        if (!fragment && Set.of("html", "head", "body").contains(tag)) {
            Element element = switch (tag) { case "html" -> html(); case "head" -> head(); default -> body(); };
            attributes(element, token.attributes()); return;
        }
        Element element = document.createElement(tag);
        for (var it = open.descendingIterator(); it.hasNext();) {
            Element ancestor = it.next();
            if (ancestor.tagName().equals("svg") || ancestor.tagName().equals("math")) {
                UnsupportedElements.foreignChild(document, tag, ancestor.tagName()); break;
            }
        }
        attributes(element, token.attributes());
        if (fragment && tag.equals("script")) element.markAlreadyStarted();
        boolean raw = HtmlParser.RAW_TEXT.contains(tag), rcdata = tag.equals("textarea") || tag.equals("title");
        if (!fragment && body == null && open.isEmpty() && METADATA.contains(tag)) {
            head().appendChild(element);
            if (raw || rcdata) raw(element, tag, rcdata);
            else if (tag.equals("template") && !token.closed()) open.push(element);
            return;
        }
        if (BLOCK_STARTS.contains(tag)) closeScoped(Set.of("p"), Set.of("button", "td", "th", "li", "dd", "dt"));
        switch (tag) {
            case "li" -> closeScoped(Set.of("li"), Set.of("ul", "ol", "menu"));
            case "dt", "dd" -> { closeScoped(Set.of("dt"), Set.of("dl")); closeScoped(Set.of("dd"), Set.of("dl")); }
            case "option" -> closeScoped(Set.of("option"), Set.of("select", "datalist"));
            case "optgroup" -> { closeScoped(Set.of("option"), Set.of("select")); closeScoped(Set.of("optgroup"), Set.of("select")); }
            default -> { }
        }
        parent().appendChild(element);
        if (HtmlParser.VOID.contains(tag) || token.closed()) return;
        if (raw || rcdata) { raw(element, tag, rcdata); return; }
        int depth = 0;
        for (Node node = element; node != null && !(node instanceof Document); node = node.parentNode()) depth++;
        if (depth < document.limits().maxDepth()) open.push(element);
        else issue.accept(token.offset(), "Maximum element depth reached; children flattened");
    }
    private void closeScoped(Set<String> names, Set<String> boundaries) {
        int remove = 0;
        for (Element element : open) {
            remove++;
            if (names.contains(element.tagName())) { while (remove-- > 0) open.pop(); return; }
            if (boundaries.contains(element.tagName()) || element.tagName().equals("body") || element.tagName().equals("template")) return;
        }
    }
    private void end(HtmlInput.Lexeme token) {
        String tag = token.name();
        if (!fragment && Set.of("html", "head", "body").contains(tag)) return;
        if (tag.equals("br")) { issue.accept(token.offset(), "End tag for void br recovered as a start tag"); parent().appendChild(document.createElement("br")); return; }
        if (tag.equals("p") && open.stream().noneMatch(e -> e.tagName().equals(tag))) {
            issue.accept(token.offset(), "Unmatched p end tag recovered as an empty paragraph"); parent().appendChild(document.createElement("p")); return;
        }
        int remove = 0;
        for (Element element : open) {
            remove++;
            if (element.tagName().equals(tag)) {
                while (remove-- > 0) open.pop();
                if (!fragment && open.isEmpty() && body != null) open.push(body);
                return;
            }
        }
        issue.accept(token.offset(), "Unmatched end tag '" + tag + "'; ignored");
    }
    private static void attributes(Element element, Map<String, String> attributes) {
        attributes.forEach((name, value) -> { if (!element.hasAttribute(name)) element.setAttribute(name, value); });
    }
    private void raw(Element element, String tag, boolean rcdata) {
        String data = input.raw(tag, rcdata);
        if (!data.isEmpty()) element.appendChild(document.createTextNode(data));
    }
    private void characters(String data) {
        if (data.isEmpty() || !fragment && body == null && open.isEmpty() && data.isBlank()) return;
        Node destination = parent();
        if (destination != textParent) flush();
        textParent = destination; text.append(data);
    }
    private void flush() {
        if (textParent == null) return;
        if (textParent.lastChild() instanceof Text previous) previous.setData(previous.data() + text);
        else textParent.appendChild(document.createTextNode(text.toString()));
        textParent = null; text.setLength(0);
    }
}
