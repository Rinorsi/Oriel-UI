package dev.oriel.engine.css;

import dev.oriel.engine.host.ExtensionRegistry;
import dev.oriel.engine.style.Prop;
import dev.oriel.engine.style.ComputedStyle;

import java.util.List;

/** Registers optional property parsers at stable positions in the existing compatibility order. */
public interface PropertySchema {
    /** Metadata for an installed longhand, including components of list-valued properties. */
    record Property(String name, boolean inherited, String initialValue, Prop.Interp interpolation,
                    boolean affectsLayout, boolean affectsPaint) {}

    static List<Property> properties() {
        return Properties.all().stream().map(PropertySchema::describe).toList();
    }

    static Property named(String name) {
        Longhand longhand = Properties.longhand(name);
        return longhand == null ? null : describe(longhand);
    }

    private static Property describe(Longhand longhand) {
        Prop property = longhand.prop == null ? longhand.group.prop : longhand.prop;
        String initial = longhand.initial == null ? longhand.serialize(longhand.get(ComputedStyle.INITIAL))
                : ComponentValue.text(longhand.initial.value);
        return new Property(longhand.name, longhand.inherited(), initial, property.interpolation, property.affectsLayout, true);
    }

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
