package dev.oriel.engine.layout;

import dev.oriel.engine.css.StyleEngine;

import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Records inputs and intermediate sizing values, rather than guessing causes from the final rectangle. */
final class LayoutConstraints {
    private final Map<Box, Map<String, String>> entries = new IdentityHashMap<>();

    void clear() { entries.clear(); }

    Map<String, String> get(Box box) { return entries.get(box); }

    void record(LayoutBox box, float width, float imposedHeight, boolean definite,
                                       float cbWidth, float cbHeight, float contentWidth, float contentHeight,
                                       float percentHeight, float autoHeight, float usedHeight, boolean ratioDependent,
                                       String heightRule) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("element", box.element.toString());
        values.put("context", box.context.name());
        values.put("parentContext", box.parent instanceof LayoutBox parent ? parent.context.name() : "viewport");
        values.put("widthAssignment", "parent formatting context assigns border-box width");
        values.put("borderBoxWidth", Float.toString(width));
        values.put("imposedHeight", Float.toString(imposedHeight));
        values.put("imposedHeightDefinite", Boolean.toString(definite));
        values.put("containingWidth", Float.toString(cbWidth));
        values.put("containingHeight", Float.toString(cbHeight));
        values.put("contentWidth", Float.toString(contentWidth));
        values.put("resolvedContentHeight", Float.toString(contentHeight));
        values.put("percentageHeightBasis", Float.toString(percentHeight));
        values.put("contentBorderBoxHeight", Float.toString(autoHeight));
        values.put("minBorderBoxHeight", Float.toString(box.minHeight));
        values.put("maxBorderBoxHeight", Float.toString(box.maxHeight));
        values.put("borderBoxHeight", Float.toString(usedHeight));
        values.put("heightRule", heightRule);
        values.put("ratioContentMinimum", Boolean.toString(ratioDependent));
        for (String property : new String[] {"width", "height", "min-width", "max-width", "min-height",
                "max-height", "aspect-ratio", "box-sizing", "position", "overflow-x", "overflow-y"}) {
            values.put("style." + property, StyleEngine.computedValue(box.style, property));
        }
        entries.put(box, Map.copyOf(values));
    }
}
