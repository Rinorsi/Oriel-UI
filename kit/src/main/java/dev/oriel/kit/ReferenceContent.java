package dev.oriel.kit;

/** Optional reference content is detected without initializing any of its classes. */
public final class ReferenceContent {
    private static final boolean AVAILABLE = ReferenceContent.class.getResource("/assets/oriel/ui/templates/catalog.json") != null;

    private ReferenceContent() {}

    public static boolean available() { return AVAILABLE; }

    /** Home remains useful when the reference gallery is not distributed. */
    public static String home() { return AVAILABLE ? OrielKit.GALLERY : OrielKit.MODSETTINGS; }

    /** Built-in navigation only; arbitrary downstream page URLs are not restricted. */
    public static boolean pageAvailable(String name) {
        return AVAILABLE || name.equals("modsettings") || name.equals("about");
    }
}
