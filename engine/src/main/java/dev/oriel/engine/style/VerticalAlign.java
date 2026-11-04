package dev.oriel.engine.style;

/**
 * The {@code vertical-align} property for inline-level boxes. {@code LENGTH} is a length or percentage (raised by it
 * from the baseline; a percentage of the box's line height), kept in {@link ComputedStyle#verticalAlignLength}.
 */
public enum VerticalAlign {
    BASELINE, TOP, MIDDLE, BOTTOM, TEXT_TOP, TEXT_BOTTOM, SUB, SUPER, LENGTH
}
