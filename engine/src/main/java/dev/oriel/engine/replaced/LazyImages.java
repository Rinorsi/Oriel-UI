package dev.oriel.engine.replaced;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.Node;
import dev.oriel.engine.layout.Box;
import dev.oriel.engine.paint.Affine;
import dev.oriel.engine.paint.Coordinates;
import dev.oriel.engine.style.Display;
import dev.oriel.engine.style.Image;
import dev.oriel.engine.style.Visibility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Document-owned admission queue, shared by lazy img sources and CSS background URLs. */
public final class LazyImages {
    private static final double STEP_MS = 50;
    private final Document document;
    private final Map<Element, Set<Image>> admitted = new IdentityHashMap<>();
    private final Set<Element> candidates = new LinkedHashSet<>();
    private double lastBatch = Double.NEGATIVE_INFINITY;
    private boolean waiting;

    public LazyImages(Document document) { this.document = document; }

    /** DOM hooks track opt-in elements, avoiding a full-tree scan on every animated attribute change. */
    public void track(Element element) {
        if (element.isConnected() && (lazyImage(element) || lazyBackground(element))) candidates.add(element);
        else {
            candidates.remove(element);
            admitted.remove(element);
        }
    }

    public void forget(Node subtree) {
        candidates.removeIf(subtree::contains);
        admitted.keySet().removeIf(subtree::contains);
    }

    public static boolean lazyImage(Element element) {
        return element.tagName().equals("img") && "lazy".equalsIgnoreCase(element.getAttribute("loading"));
    }

    public static boolean lazyBackground(Element element) {
        return element != null && element.hasAttribute("data-oriel-lazy");
    }

    /** No resource lookup here: both measurement and painting must pass this gate. */
    public boolean allows(Element element, Image image) {
        Set<Image> sources = admitted.get(element);
        return sources != null && sources.contains(image);
    }

    /** Called after layout, never from a geometry read or paint. Returns whether sources became drawable. */
    public boolean frame(double now) {
        waiting = false;
        if (candidates.isEmpty()) return false;
        int budget = budget();
        int used = 0;
        boolean due = now - lastBatch >= STEP_MS;
        for (Element element : candidates) {
            List<Image> sources = sources(element);
            Set<Image> loaded = admitted.get(element);
            if (loaded != null) loaded.retainAll(sources);
            if (sources.isEmpty() || loaded != null && loaded.containsAll(sources) || !near(element)) continue;
            int before = used;
            for (Image source : sources) {
                if (loaded != null && loaded.contains(source)) continue;
                if (!due || used == budget) { waiting = true; continue; }
                if (loaded == null) {
                    loaded = new HashSet<>();
                    admitted.put(element, loaded);
                }
                loaded.add(source);
                // Preparing the texture here keeps later painting from decoding an entire prefetched row at once.
                ImageSources.size(document, source);
                if (source instanceof Image.Url url && !url.url().regionMatches(true, 0, "data:", 0, 5)) {
                    document.host().prepareImage(url.url());
                }
                used++;
            }
            if (used > before && lazyImage(element) && element.replaced.update()) document.invalidateLayout();
        }
        if (used > 0) lastBatch = now;
        return used > 0;
    }

    /** Idle preview hosts still advance batches; far-away images alone do not keep a page awake. */
    public boolean needsFrame(double now) { return waiting && now - lastBatch >= STEP_MS; }

    /** Pending near-view admissions count as loading when automation waits for a settled page. */
    public boolean pending() { return waiting; }

    public void clear() {
        candidates.clear();
        admitted.clear();
        waiting = false;
    }

    private List<Image> sources(Element element) {
        List<Image> result = new ArrayList<>();
        if (lazyImage(element) && element.replaced instanceof ImageContent content && content.source() != null) {
            result.add(content.source());
        }
        if (lazyBackground(element) && element.style != null) {
            for (var layer : element.style.backgroundLayers) {
                if (layer.image() instanceof Image.Url) result.add(layer.image());
            }
        }
        return result;
    }

    private int budget() {
        Element root = document.documentElement();
        String value = root == null ? null : root.getAttribute("data-oriel-lazy-budget");
        if (value == null) return 3;
        try {
            int budget = Integer.parseInt(value);
            if (budget >= 1 && budget <= 64) return budget;
        } catch (NumberFormatException ignored) {
            // Report malformed page options with their source location, like other declarative options.
        }
        document.diagnostics().reportOnce("lazy-image-budget:" + value, () -> new Diagnostic(
                Diagnostic.Severity.WARNING, "lazy-image-budget", document.url(), root.sourceLine(), root.sourceColumn(),
                "data-oriel-lazy-budget must be an integer from 1 to 64; got '" + value + "'",
                "use data-oriel-lazy-budget=3 on html (the default)"));
        return 3;
    }

    /** Intersect expanded scrollports on both axes, following the same containing blocks as paint. */
    private boolean near(Element element) {
        if (!element.isConnected() || element.box == null) return false;
        if (element.style == null || element.style.visibility != Visibility.VISIBLE) return false;
        for (Element p = element; p != null; p = p.parentElement()) {
            if (p.style != null && (p.style.display == Display.NONE || p.style.opacity <= 0)) return false;
        }
        float vw = document.viewportWidth(), vh = document.viewportHeight();
        float left = -vw / 2, top = -vh / 2, right = vw * 1.5f, bottom = vh * 1.5f;
        for (Box box = element.box.contentParent(); box != null; box = box.contentParent()) {
            var style = box.style;
            if (style == null || !style.overflowX.clips() && !style.overflowY.clips()) continue;
            float[] port = Coordinates.toViewport(box, new Affine()).mapBounds(box.borderLeft, box.borderTop,
                    box.paddingBoxWidth(), box.paddingBoxHeight(), new float[4]);
            if (style.overflowX.clips()) {
                float margin = style.overflowX.scrolls() ? port[2] / 2 : 0;
                left = Math.max(left, port[0] - margin);
                right = Math.min(right, port[0] + port[2] + margin);
            }
            if (style.overflowY.clips()) {
                float margin = style.overflowY.scrolls() ? port[3] / 2 : 0;
                top = Math.max(top, port[1] - margin);
                bottom = Math.min(bottom, port[1] + port[3] + margin);
            }
        }
        float[] rect = Coordinates.boundingRect(element.box);
        return right > left && bottom > top && rect[0] < right && rect[0] + rect[2] > left
                && rect[1] < bottom && rect[1] + rect[3] > top;
    }
}
