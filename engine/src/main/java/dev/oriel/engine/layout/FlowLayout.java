package dev.oriel.engine.layout;

/** Block flow expressed as a cursor with an adjoining-margin accumulator. */
final class FlowLayout implements FormattingContext {
    private final LayoutPass pass;

    FlowLayout(LayoutPass pass) {
        this.pass = pass;
    }

    @Override
    public LayoutResult layoutContent(LayoutBox box, float width, float height, float percentHeight, boolean measure) {
        if (box.inline != null) {
            return InlineLayout.vertical(box) ? pass.inline.layoutVertical(box, width, height, measure)
                    : pass.inline.layout(box, width, percentHeight, measure);
        }
        Cursor flow = new Cursor(box, height);
        for (int i = 0; i < box.children.size(); i++) {
            LayoutBox child = (LayoutBox) box.children.get(i);
            if (child.outOfFlow) {
                PositionedLayout.setStaticPosition(child, box, box.contentX(), flow.staticY());
                continue;
            }
            float childWidth = pass.blockLevelWidth(child, width, percentHeight);
            LayoutResult result = pass.layoutOrMeasure(child, childWidth, Float.NaN, width, percentHeight, measure);
            child.x = box.contentX() + child.marginLeft;
            child.y = flow.place(child, result);
            if (!measure) PositionedLayout.applyRelativeOffset(child, width, percentHeight);
        }
        return flow.finish(box, height);
    }

    @Override
    public float intrinsicContentWidth(LayoutBox box, boolean max) {
        if (box.inline != null) return pass.inline.intrinsicWidth(box, max);
        float extent = 0;
        for (int i = 0; i < box.children.size(); i++) {
            LayoutBox child = (LayoutBox) box.children.get(i);
            if (!child.outOfFlow) extent = Math.max(extent, pass.contribution(child, max));
        }
        return extent;
    }

    private static final class Cursor {
        final boolean topOpen, bottomOpen, throughOpen;
        float position;
        float firstBaseline = Float.NaN, lastBaseline = Float.NaN;
        boolean empty = true;
        MarginSet leading = MarginSet.EMPTY, adjoining = MarginSet.EMPTY;

        Cursor(LayoutBox box, float height) {
            boolean separated = box.independent();
            topOpen = !separated && box.borderTop == 0 && box.paddingTop == 0;
            throughOpen = topOpen && box.borderBottom == 0 && box.paddingBottom == 0;
            bottomOpen = !separated && box.borderBottom == 0 && box.paddingBottom == 0 && Float.isNaN(height);
            position = box.contentY();
        }

        float staticY() {
            return empty && topOpen ? position : position + adjoining.resolve();
        }

        float place(LayoutBox child, LayoutResult result) {
            MarginSet before = result.top().with(child.marginTop);
            MarginSet after = result.bottom().with(child.marginBottom);
            boolean escapes = empty && topOpen;
            float y = escapes ? position : position + adjoining.with(before).resolve();
            if (escapes) leading = leading.with(before);
            if (result.collapsesThrough()) {
                MarginSet combined = before.with(after);
                adjoining = adjoining.with(combined);
                if (escapes) leading = leading.with(combined);
            } else {
                position = y + result.height();
                adjoining = after;
                empty = false;
            }
            if (Float.isNaN(firstBaseline) && !Float.isNaN(result.firstBaseline())) firstBaseline = y + result.firstBaseline();
            if (!Float.isNaN(result.lastBaseline())) lastBaseline = y + result.lastBaseline();
            return y;
        }

        LayoutResult finish(LayoutBox box, float height) {
            float extent = (bottomOpen ? position : position + adjoining.resolve()) - box.contentY();
            float shift = Float.isNaN(height) ? 0 : Alignment.distribute(box.style.alignContent, height - extent, 1)[0];
            if (shift != 0) {
                for (int i = 0; i < box.children.size(); i++) {
                    Box child = box.children.get(i);
                    if (!child.outOfFlow) child.y += shift;
                }
                firstBaseline += shift;
                lastBaseline += shift;
            }
            return new LayoutResult(extent, firstBaseline, lastBaseline, topOpen ? leading : MarginSet.EMPTY,
                    bottomOpen ? adjoining : MarginSet.EMPTY, empty && throughOpen);
        }
    }
}
