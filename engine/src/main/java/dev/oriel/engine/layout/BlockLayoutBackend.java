package dev.oriel.engine.layout;

/** Experimental layout selection, captured once per document. */
enum BlockLayoutBackend {
    OLD, NEW, BOTH;

    static BlockLayoutBackend configured() {
        return switch (System.getProperty("oriel.layout.block", "old")) {
            case "old" -> OLD;
            case "new" -> NEW;
            case "both" -> BOTH;
            default -> throw new IllegalArgumentException("oriel.layout.block must be old, new or both");
        };
    }
}
