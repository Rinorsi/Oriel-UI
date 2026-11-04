package dev.oriel.engine.css;

import dev.oriel.engine.host.ExtensionRegistry;
import dev.oriel.engine.style.Prop;

import java.util.List;

/** Registers optional property parsers at stable positions in the existing compatibility order. */
public interface PropertySchema {
    enum Phase { TEXT, TINT, MODEL, SHORTHANDS }

    void register(Phase phase);

    List<PropertySchema> INSTALLED = ExtensionRegistry.providers(PropertySchema.class);

    static void registerAll(Phase phase) {
        for (PropertySchema schema : INSTALLED) schema.register(phase);
    }

    /** Public enum slots retained until the Java style API migration; no parser is installed by core. */
    static boolean uninstalledCompatibilitySlot(Prop property) {
        if (!INSTALLED.isEmpty()) return false;
        return switch (property) {
            case TINT, OBFUSCATED, MODEL_YAW, MODEL_PITCH, MODEL_ROLL, MODEL_SCALE, ENTITY_FOCUS,
                    GAZE_REACH, GAZE_LIMIT_YAW, GAZE_LIMIT_UP, GAZE_LIMIT_DOWN,
                    TRANSLATE_X, TRANSLATE_Y, TRANSLATE_Z, SCENE_CENTER_X, SCENE_CENTER_Y, SCENE_CENTER_Z,
                    TOOLTIP_DELAY -> true;
            default -> false;
        };
    }
}
