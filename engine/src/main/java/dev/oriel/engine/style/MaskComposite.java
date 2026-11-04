package dev.oriel.engine.style;

/**
 * {@code mask-composite} (CSS Masking 1 §7.8), between each mask layer and the layers under it (the ones after it in
 * the list). The {@code -webkit-mask-composite} names map onto these: source-over → add, source-out → subtract,
 * source-in → intersect, xor → exclude.
 */
public enum MaskComposite { ADD, SUBTRACT, INTERSECT, EXCLUDE }
