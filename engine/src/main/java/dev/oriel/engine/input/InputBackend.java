package dev.oriel.engine.input;

/** Per-instance migration switch. Unknown values fail before a document starts accepting input. */
public enum InputBackend {
    OLD, NEW, BOTH;

    public static InputBackend configured() {
        return switch (System.getProperty("oriel.inputBackend", "old")) {
            case "old" -> OLD;
            case "new" -> NEW;
            case "both" -> BOTH;
            default -> throw new IllegalArgumentException("oriel.inputBackend must be old, new or both");
        };
    }
}
