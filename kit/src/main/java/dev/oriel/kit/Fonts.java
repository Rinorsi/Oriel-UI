package dev.oriel.kit;

import java.util.List;

/**
 * The fonts a page can switch to: Minecraft's own two, and the ones the kit bundles (all under the SIL Open Font
 * Licence; the licences are in {@code META-INF/licenses/fonts/}). Each is a Minecraft font id for
 * {@code font-family}, so CSS can also name one directly ({@code font-family: oriel:kai}), and a resource pack's own
 * font works the same way ({@code assets/<ns>/font/<id>.json}, then {@code font-family: <ns>:<id>}). Bundled fonts
 * cover the common Chinese characters; anything else falls back to the game's font.
 *
 * <p>{@link Shell#font} sets one for every page ({@code data-font} on {@code <html>}); elements with
 * {@code data-font-pick="<id>"} switch to it when clicked.
 */
public final class Fonts {
    /** A font: its id (for {@code data-font}), {@code font-family}, name, kind, size it looks best at, and credit. */
    public record Font(String id, String family, String name, String kind, int size, String credit) {}

    public static final List<Font> ALL = List.of(
            new Font("mc", "minecraft:default", "原版", "像素", 8, "Minecraft 默认字体"),
            new Font("unifont", "minecraft:uniform", "Unifont", "像素", 8, "GNU Unifont · 游戏自带"),
            new Font("pixel", "oriel:pixel", "缝合像素 8", "像素", 8, "Fusion Pixel Font 8px · OFL"),
            new Font("pixel_12", "oriel:pixel_12", "缝合像素 12", "像素", 12, "Fusion Pixel Font 12px · OFL"),
            new Font("cubic", "oriel:cubic", "俐方体 11", "像素", 11, "Cubic 11 · OFL"),
            new Font("sans", "oriel:sans", "黑体", "平滑", 8, "Noto Sans SC · OFL"),
            new Font("kai", "oriel:kai", "文楷", "平滑", 8, "基于霞鹜文楷 · OFL"),
            new Font("smiley", "oriel:smiley", "得意黑", "平滑", 9, "Smiley Sans · OFL"),
            new Font("kuaile", "oriel:kuaile", "快乐体", "趣味", 9, "站酷快乐体 · OFL"));

    private Fonts() {}

    /** The font with {@code id}, or the game's default. */
    public static Font byId(String id) {
        for (Font f : ALL) if (f.id().equals(id)) return f;
        return ALL.getFirst();
    }
}
