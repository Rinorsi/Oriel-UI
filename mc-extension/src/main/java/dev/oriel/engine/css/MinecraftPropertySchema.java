package dev.oriel.engine.css;

import dev.oriel.engine.style.EntityFocus;
import dev.oriel.engine.style.Prop;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.oriel.engine.css.Properties.*;

/** The original twelve Minecraft properties, registered by the optional domain extension. */
public final class MinecraftPropertySchema implements PropertySchema {
    private static final Function<Object, String> COLOR = v -> CssColors.serialize((Integer) v);
    private static final Function<Object, String> KEYWORD = v -> Keywords.css((Enum<?>) v);
    private static final Function<Object, String> NUMBER = v -> CssText.number((Float) v);
    private static final Function<Object, String> PX = v -> CssText.px((Float) v);
    private static final Function<Object, String> SECONDS = v -> CssText.seconds((Float) v);
    public void register(Phase phase) {
        switch (phase) {
            case TEXT -> add(Prop.TOOLTIP_DELAY, nonNegative(Numeric::time), SECONDS);
            case TINT -> add(Prop.TINT, CssColors::read, COLOR);
            case MODEL -> model();
            case SHORTHANDS -> shorthands();
        }
    }
    private static void model() {
        add(Prop.OBFUSCATED, Keywords.parser(Map.of("on", true, "off", false, "none", false)), v -> (Boolean) v ? "on" : "off");
        add(List.of(Prop.MODEL_YAW, Prop.MODEL_PITCH, Prop.MODEL_ROLL), Numeric::angle, v -> CssText.deg((Float) v));
        add(Prop.MODEL_SCALE, nonNegative(Numeric::number), NUMBER);
        add(Prop.ENTITY_FOCUS, Keywords.parser(EntityFocus.class), KEYWORD);
        add(Prop.GAZE_REACH, (r, ctx) -> Numeric.px(r, ctx, false), PX);
        // -mc-gaze-limit's parts: magnitudes, or none (NaN, so it flips rather than interpolates).
        add(List.of(Prop.GAZE_LIMIT_YAW, Prop.GAZE_LIMIT_UP, Prop.GAZE_LIMIT_DOWN),
                or(Map.of("none", Float.NaN), nonNegative(Numeric::angle)),
                v -> Float.isNaN((Float) v) ? "none" : CssText.deg((Float) v));
        add(List.of(Prop.TRANSLATE_X, Prop.TRANSLATE_Y, Prop.TRANSLATE_Z), Numeric::number, NUMBER);
        // -mc-scene-center's parts: block coordinates, or auto (NaN, so it flips rather than interpolates).
        add(List.of(Prop.SCENE_CENTER_X, Prop.SCENE_CENTER_Y, Prop.SCENE_CENTER_Z),
                or(Map.of("auto", Float.NaN), Numeric::number),
                v -> Float.isNaN((Float) v) ? "auto" : CssText.number((Float) v));
    }

    private static void shorthands() {
        Shorthands.register("-mc-gaze-limit", Shorthands.longhands("-oriel-gaze-limit-yaw", "-oriel-gaze-limit-up",
                "-oriel-gaze-limit-down"), values -> Shorthands.shortest(values, last -> last), (l, v) -> Shorthands.sequence(l, v, Shorthands.REPEAT));
        // Inside a <scene>: x [y [z]] in blocks, the ones left out 0; the centre is auto or all three coordinates.
        Shorthands.register("-mc-translate", Shorthands.longhands("-oriel-translate-x", "-oriel-translate-y", "-oriel-translate-z"),
                values -> Shorthands.shortest(values, last -> "0"), (l, v) -> Shorthands.sequence(l, v, last -> Shorthands.INITIAL));
        Shorthands.register("-mc-scene-center", Shorthands.longhands("-oriel-scene-center-x", "-oriel-scene-center-y",
                "-oriel-scene-center-z"), values -> values.contains("auto") ? "auto" : String.join(" ", values),
                (l, v) -> Shorthands.isIdent(v, "auto")
                ? Map.of(l.get(0), v, l.get(1), v, l.get(2), v) : Shorthands.sequence(l, v, Shorthands.REQUIRED));

    }
}
