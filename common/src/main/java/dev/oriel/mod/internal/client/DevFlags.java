package dev.oriel.mod.internal.client;


/**
 * Switches for the dev-only tools (the autopilot and the scripted tour), readable without loading them: a jar built
 * with {@code -Poriel_release} leaves those classes and their pages out, so code outside them must test these first.
 */
public final class DevFlags {
    /** {@code -Poriel.autopilot} (set by {@code -Pautopilot} on dev runs). */
    public static final boolean AUTOPILOT = available("DevAutopilot") && Boolean.getBoolean("oriel.autopilot");
    /** Hot reload is optional: release jars omit its implementation. */
    public static final boolean RELOAD_AVAILABLE = available("DevReload");
    /** Release jars do not contain the inspector implementation or page. */
    public static final boolean INSPECTOR_AVAILABLE = available("DevInspector");
    /** The scripted tour and its drawn cursor, on 26.3. */
    //? if >=26.3 {
    public static final boolean TOUR = available("DevTour") && Boolean.getBoolean("oriel.tour");
    //?} else
    //public static final boolean TOUR = false;

    private DevFlags() {}

    // Inspect the resource without resolving or initializing an optional development class.
    private static boolean available(String name) {
        return DevFlags.class.getResource(name + ".class") != null;
    }
}
