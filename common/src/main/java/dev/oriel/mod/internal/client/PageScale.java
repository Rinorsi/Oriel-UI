package dev.oriel.mod.internal.client;

/** Maps a document's CSS pixels to vanilla GUI pixels, without changing the window. */
public record PageScale(double ratio, float devicePixelRatio) {
    public static PageScale resolve(int requested, double vanilla) {
        if (requested < 0 || requested > 4) throw new IllegalArgumentException("UI scale must be 0 (automatic) or 1..4");
        if (!Double.isFinite(vanilla) || vanilla <= 0) throw new IllegalArgumentException("Invalid vanilla GUI scale");
        double effective = requested == 0 ? vanilla : requested;
        return new PageScale(effective / vanilla, (float) effective);
    }

    public int extent(int guiExtent) { return Math.max(1, (int) Math.ceil(guiExtent / ratio)); }
    public double toPage(double gui) { return gui / ratio; }
    public double toGui(double page) { return page * ratio; }
}
