package dev.oriel.engine.layout;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.FontMetrics;

import java.util.function.Consumer;

/** R06-only migration hook. Flex/Grid hooks can remain independent in LayoutEngine.layout. */
final class BlockLayoutMigration {
    private final Document document;
    private final BlockLayoutBackend backend = BlockLayoutBackend.configured();
    final boolean trace = Boolean.getBoolean("oriel.layoutTrace");
    private final LayoutConstraints constraints = trace ? new LayoutConstraints() : null;
    private LayoutSnapshot.Ids ids;
    private TextMeasure candidateText;

    BlockLayoutMigration(Document document) {
        this.document = document;
    }

    LayoutPass pass(TextMeasure text, float width, float height) {
        if (constraints != null) constraints.clear();
        return new LayoutPass(text, width, height, backend == BlockLayoutBackend.NEW, constraints);
    }

    void overflow(LayoutBox box, Consumer<LayoutBox> legacy) {
        if (backend == BlockLayoutBackend.NEW) OverflowLayout.resolve(box, true);
        else legacy.accept(box);
    }

    LayoutBox finish(LayoutBox committed) {
        if (backend != BlockLayoutBackend.BOTH) return committed;
        LayoutSnapshot expected = snapshot(committed);
        // Build another tree and pass, with a separately owned text cache. No intrinsic/measurement cache from the
        // committed legacy tree may make the candidate skip work. Computed styles are shared read-only inputs.
        LayoutBox candidate = new BoxTreeBuilder().build(document.documentElement());
        float vw = document.viewportWidth(), vh = document.viewportHeight();
        if (candidateText == null) candidateText = new TextMeasure(document.host().fonts());
        if (constraints != null) constraints.clear();
        LayoutPass candidatePass = new LayoutPass(candidateText, vw, vh, true, constraints);
        float width = candidatePass.blockLevelWidth(candidate, vw, vh);
        candidatePass.layout(candidate, width, Float.NaN, vw, vh);
        candidate.x = candidate.marginLeft;
        candidate.y = candidate.marginTop;
        PositionedLayout.applyRelativeOffset(candidate, vw, vh);
        new PositionedLayout(candidatePass).layoutOutOfFlow(candidate);
        // The legacy pass already clamped the offsets. Equal scroll ranges require no second clamp, and a
        // mismatch must fail without dispatching another scroll change from the verification pass.
        OverflowLayout.resolve(candidate, false);
        expected.requireSame(snapshot(candidate), document.url() + " block old/new layout");
        return candidate;
    }

    LayoutSnapshot snapshot(Box root) {
        if (ids == null) ids = new LayoutSnapshot.Ids();
        return LayoutSnapshot.capture(root, ids, document.viewportWidth(), document.viewportHeight());
    }

    void fontsChanged(FontMetrics fonts) {
        if (candidateText != null) candidateText.invalidate(fonts);
    }

    java.util.Map<String, String> constraints(Box box) {
        java.util.Map<String, String> recorded = constraints == null ? null : constraints.get(box);
        if (recorded == null) return null;
        if (ids == null) ids = new LayoutSnapshot.Ids();
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>(recorded);
        // Element.box is the principal box; pseudo and anonymous boxes have no independent Element target.
        result.put("boxId", "e" + ids.element(box.element) + ":principal");
        return java.util.Map.copyOf(result);
    }
}
