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
    /** Font metadata. Name, kind and credit are message keys or literals; document-aware accessors localize them. */
    public record Font(String id, String family, String name, String kind, int size, String credit) {
        public String name(dev.oriel.engine.dom.Document doc) { return Lang.value(doc, name); }
        public String kind(dev.oriel.engine.dom.Document doc) { return Lang.value(doc, kind); }
        public String credit(dev.oriel.engine.dom.Document doc) { return Lang.value(doc, credit); }
    }

    public static final List<Font> ALL = List.of(
            new Font("mc", "minecraft:default", "oriel.ui.fonts.vanilla", "oriel.ui.fonts.pixel", 8, "oriel.ui.fonts.minecraft_default_font"),
            new Font("unifont", "minecraft:uniform", "oriel.ui.fonts.unifont.name", "oriel.ui.fonts.pixel", 8, "oriel.ui.fonts.gnu_unifont_included_with_the_game"),
            new Font("pixel", "oriel:pixel", "oriel.ui.fonts.fusion_pixel_8", "oriel.ui.fonts.pixel", 8, "oriel.ui.fonts.pixel.credit"),
            new Font("pixel_12", "oriel:pixel_12", "oriel.ui.fonts.fusion_pixel_12", "oriel.ui.fonts.pixel", 12, "oriel.ui.fonts.pixel_12.credit"),
            new Font("cubic", "oriel:cubic", "oriel.ui.fonts.cubic_11", "oriel.ui.fonts.pixel", 11, "oriel.ui.fonts.cubic.credit"),
            new Font("sans", "oriel:sans", "oriel.ui.fonts.noto_sans_sc", "oriel.ui.fonts.smooth", 8, "oriel.ui.fonts.sans.credit"),
            new Font("kai", "oriel:kai", "oriel.ui.fonts.lxgw_wenkai", "oriel.ui.fonts.smooth", 8, "oriel.ui.fonts.based_on_lxgw_wenkai_ofl"),
            new Font("smiley", "oriel:smiley", "oriel.ui.fonts.smiley_sans", "oriel.ui.fonts.smooth", 9, "oriel.ui.fonts.smiley.credit"),
            new Font("kuaile", "oriel:kuaile", "oriel.ui.fonts.zcool_kuaile", "oriel.ui.fonts.playful", 9, "oriel.ui.fonts.zcool_kuaile_ofl"),
            new Font("brush", "oriel:brush", "oriel.ui.fonts.ma_shan_zheng", "oriel.ui.fonts.calligraphy", 10, "oriel.ui.fonts.ma_shan_zheng_ofl"));

    private Fonts() {}

    /** The font with {@code id}, or the game's default. */
    public static Font byId(String id) {
        for (Font f : ALL) if (f.id().equals(id)) return f;
        return ALL.getFirst();
    }
}
