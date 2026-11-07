package dev.oriel.engine.layout;

/** Selects the migration implementation once per pass; old remains the default until all gates pass. */
final class GridLayout implements FormattingContext {
    private final FormattingContext delegate;

    GridLayout(LayoutPass pass) {
        delegate = FlexGridCompare.mode("grid").equals("old") ? new LegacyGridLayout(pass) : new GridSolver(pass);
    }

    @Override
    public LayoutResult layoutContent(LayoutBox box, float contentWidth, float contentHeight, float percentHeight,
                                      boolean measure) {
        return delegate.layoutContent(box, contentWidth, contentHeight, percentHeight, measure);
    }

    @Override
    public float intrinsicContentWidth(LayoutBox box, boolean max) {
        return delegate.intrinsicContentWidth(box, max);
    }
}
