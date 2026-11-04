package dev.oriel.engine.html;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;

/** Elements whose native browser behavior cannot be represented by the engine's generic flow boxes. */
public final class UnsupportedElements {
    private UnsupportedElements() {}

    public static void check(Document document, Element element) {
        String tag = element.tagName();
        if (element.replaced != null || tag.contains("-")) return;
        String suggestion = switch (tag) {
            case "svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
                    "defs", "use", "symbol", "text", "tspan", "textpath", "clippath", "mask",
                    "filter", "lineargradient", "radialgradient", "stop", "foreignobject", "image" ->
                    "use <img src=\"image.png\">, CSS gradients, or CSS borders instead of inline SVG";
            case "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "col", "colgroup" ->
                    "use a <div> with display: grid and grid-template-columns for rows and columns";
            case "video", "audio", "source", "track" ->
                    "use an <img> poster and a <button> whose host controller starts media playback";
            case "iframe", "object", "embed", "frame", "frameset" ->
                    "compose the content into the page or use a host-provided replaced element";
            case "math", "mi", "mn", "mo", "ms", "mtext", "mrow", "mfrac", "msqrt",
                    "mroot", "msub", "msup", "msubsup", "mtable", "mtr", "mtd" ->
                    "use plain text with <sub>/<sup> or a PNG image of the formula";
            case "marquee", "blink" -> "use CSS @keyframes with transform or opacity to animate a <div>";
            case "map", "area" -> "use positioned <button> elements over an <img> for clickable regions";
            case "ruby", "rt", "rp" -> "use separate text elements in a CSS grid for pronunciation annotations";
            default -> null;
        };
        if (suggestion != null) report(document, tag, suggestion);
    }

    static void foreignChild(Document document, String tag, String root) {
        if (tag.contains("-")) return;
        report(document, tag, "replace the <" + root + "> subtree with <img src=\"image.png\"> or HTML/CSS elements");
    }

    private static void report(Document document, String tag, String suggestion) {
        document.diagnostics().reportOnce("unsupported-element:" + tag, () -> new Diagnostic(
                Diagnostic.Severity.WARNING, "unsupported-element", document.url(), 0,
                "Element <" + tag + "> is not supported", suggestion));
    }
}
