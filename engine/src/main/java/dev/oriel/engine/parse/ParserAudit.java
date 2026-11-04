package dev.oriel.engine.parse;

/** Parser selection after migration. Legacy modes fail explicitly instead of pretending to dual-run. */
public final class ParserAudit {
    public enum Mode { NEW }
    private ParserAudit() {}
    public static Mode mode() {
        return switch (System.getProperty("oriel.parser", "new")) {
            case "new" -> Mode.NEW;
            case "old", "both" -> throw new IllegalStateException("Legacy parsers were removed; use the pre-removal migration commit for old/both audits");
            default -> throw new IllegalArgumentException("oriel.parser must be new; legacy old/both modes were retired");
        };
    }
}
