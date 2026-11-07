package dev.oriel.engine.input;

import com.google.gson.JsonObject;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.event.Event;
import dev.oriel.engine.paint.HitResult;
import dev.oriel.engine.style.PointerEvents;
import dev.oriel.engine.style.Visibility;

/** Explicit queries only. Diagnostics identify nodes but never serialize entered text or event payloads. */
final class InputDiagnostics {
    private InputDiagnostics() {}

    static String id(Node node) {
        if (node == null) return null;
        if (node instanceof Element e) {
            if (!e.id().isEmpty()) return e.tagName() + "#" + e.id();
            StringBuilder path = new StringBuilder();
            int depth = 0;
            for (Element at = e; at != null && depth++ < 64; at = at.parentElement()) {
                if (!path.isEmpty()) path.insert(0, '/');
                path.insert(0, at.tagName() + "[" + at.indexInParent() + "]");
            }
            return path.toString();
        }
        return node.nodeName();
    }

    static String event(Event event, String stage) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "input-event");
        json.addProperty("type", event.type);
        json.addProperty("stage", stage);
        json.addProperty("target", id(event.target()));
        json.addProperty("currentTarget", id(event.currentTarget()));
        json.addProperty("phase", event.phase().name());
        json.addProperty("defaultPrevented", event.defaultPrevented());
        json.addProperty("propagationStopped", event.propagationStopped());
        return json.toString();
    }

    static String composition(Element element, String state, int length) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "composition");
        json.addProperty("target", id(element));
        json.addProperty("state", state);
        json.addProperty("utf16Length", length);
        return json.toString();
    }

    static String whyNoClick(Document document, Element element, float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("click coordinates must be finite viewport pixels");
        document.flushLayout();
        HitResult hit = document.hitTest(x, y);
        String reason;
        if (element.ownerDocument() != document || !element.isConnected()) reason = "disconnected";
        else if (InputEligibility.inert(element)) reason = "inert";
        else if (element.isDisabled()) reason = "disabled";
        else if (element.box == null) reason = "not-rendered";
        else if (element.computedStyle().visibility != Visibility.VISIBLE) reason = "visibility";
        else if (hit != null && element.contains(hit.element())) reason = "receives-pointer";
        else if (element.computedStyle().pointerEvents == PointerEvents.NONE) reason = "pointer-events";
        else {
            float[] rect = element.visibleRect();
            reason = rect == null || x < rect[0] || y < rect[1] || x >= rect[0] + rect[2] || y >= rect[1] + rect[3]
                    ? "outside-visible-bounds" : hit == null ? "outside-hit-shape" : "occluded";
        }
        JsonObject json = new JsonObject();
        json.addProperty("kind", "why-no-click");
        json.addProperty("element", id(element));
        json.addProperty("hit", hit == null ? null : id(hit.element()));
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("reason", reason);
        return json.toString();
    }

    static String focus(Document document, Element element, boolean visible, String source) {
        Element focused = document.focusedElement();
        JsonObject json = new JsonObject();
        json.addProperty("kind", "focus");
        json.addProperty("element", id(element));
        json.addProperty("focused", id(focused));
        json.addProperty("focusWithin", focused != null && element.contains(focused));
        json.addProperty("focusVisible", focused == element && visible);
        json.addProperty("source", source);
        json.addProperty("inert", InputEligibility.inert(element));
        return json.toString();
    }
}
