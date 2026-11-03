package dev.oriel.engine.css;

import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.Prop;
import dev.oriel.engine.style.TimingFunction;

import java.util.Set;

/**
 * One {@code @keyframes} keyframe computed for a specific element: {@code style} is the element's base style with the
 * keyframe's declarations applied (relative units, var() and currentColor resolved), and {@code props} lists the
 * properties the keyframe specifies. {@code timing} is the keyframe's own
 * {@code animation-timing-function}, or null to use the animation's.
 *
 * @param offset 0..1
 */
public record ResolvedKeyframe(float offset, TimingFunction timing, ComputedStyle style, Set<Prop> props) {}
