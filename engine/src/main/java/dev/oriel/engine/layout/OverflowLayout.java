package dev.oriel.engine.layout;

import dev.oriel.engine.input.Controls;

/** Overflow accumulation and routing to the actual containing block. Verification can defer scroll clamping. */
final class OverflowLayout {
    private OverflowLayout() {}

    static void resolve(LayoutBox box, boolean clamp) {
        for (int i = 0; i < box.children.size(); i++) {
            LayoutBox child = (LayoutBox) box.children.get(i);
            resolve(child, clamp);
            Box owner = child.outOfFlow ? child.contentParent() : box;
            if (!(owner instanceof LayoutBox target)) continue;
            float x = child.x + child.overflowRight, y = child.y + child.overflowBottom;
            for (Box ancestor = box; ancestor != target; ancestor = ancestor.parent) {
                x += ancestor.x;
                y += ancestor.y;
            }
            target.extendOverflow(x, y);
        }
        for (int i = 0; i < box.lines.size(); i++) {
            for (Fragment fragment : box.lines.get(i).fragments) {
                box.extendOverflow(fragment.x() + fragment.width(), fragment.y() + fragment.height());
            }
        }
        boolean empty = box.overflowRight == Float.NEGATIVE_INFINITY;
        box.scrollWidth = Math.max(box.paddingBoxWidth(), empty ? 0 : box.overflowRight - box.borderLeft + box.paddingRight);
        box.scrollHeight = Math.max(box.paddingBoxHeight(), empty ? 0 : box.overflowBottom - box.borderTop + box.paddingBottom);
        if (box.context == LayoutBox.Context.LEAF) Controls.overflow(box);
        if (clamp && box.isScrollContainer()) box.element.clampScroll();
        box.overflowRight = empty || box.style.overflowX.clips() ? box.width : Math.max(box.width, box.overflowRight);
        box.overflowBottom = empty || box.style.overflowY.clips() ? box.height : Math.max(box.height, box.overflowBottom);
    }
}
