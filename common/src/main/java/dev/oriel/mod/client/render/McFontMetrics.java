package dev.oriel.mod.client.render;

import dev.oriel.engine.host.FontFamilies;
import dev.oriel.engine.host.FontMetrics;
import dev.oriel.engine.host.FontSpec;
import dev.oriel.engine.paint.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
//? if >=26
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text measurement with Minecraft's font, and what both measurement and {@link McCanvas#drawText} use for a
 * {@link FontSpec}: the {@link Style} (font, bold, italic, decorations) and the character sequence, so laid-out text
 * and painted text always agree (bold glyphs are wider).
 *
 * <p>Families resolve through {@link FontFamilies} to font ids ({@code minecraft:default | uniform | alt |
 * illageralt}, any {@code ns:id} with an {@code assets/<ns>/font/<id>.json}); a family whose font does not exist
 * falls through to the next, and the last resort is the default font. Each spec keeps its styles in its host slot;
 * the shared table is keyed without the size, so animating {@code font-size} adds nothing to it. Text is drawn as
 * written (no {@code §} formatting codes); only text with right-to-left characters, or a right-to-left language,
 * goes through Minecraft's bidi reordering and shaping. Render thread only.
 */
public final class McFontMetrics implements FontMetrics {
    public static final McFontMetrics INSTANCE = new McFontMetrics();
    private static final int CAPACITY = 256;

    /** A spec's styles, by decoration flags ({@link Canvas#UNDERLINE} | {@link Canvas#STRIKETHROUGH}). */
    private record Resolved(int generation, Style[] styles) {}

    private record Key(List<String> families, boolean bold, boolean italic) {}

    private final Map<Key, Style[]> styles = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Style[]> eldest) {
            return size() > CAPACITY;
        }
    };
    private final Map<Identifier, Boolean> knownFonts = new HashMap<>();
    /** Bumped when resources reload, which invalidates what specs hold. */
    private int generation;

    private McFontMetrics() {}

    @Override
    public float width(String text, FontSpec font) {
        return Minecraft.getInstance().font.getSplitter().stringWidth(sequence(text, style(font, 0))) * font.scale();
    }

    @Override
    public float charWidth(int codePoint, FontSpec font) {
        return Minecraft.getInstance().font.getSplitter().stringWidth(FormattedCharSequence.codepoint(codePoint, style(font, 0)))
                * font.scale();
    }

    /** The style for a font request with text decorations ({@link Canvas#UNDERLINE} | {@link Canvas#STRIKETHROUGH}). */
    public Style style(FontSpec font, int decorations) {
        Style[] resolved = font.hostFont() instanceof Resolved r && r.generation == generation ? r.styles : null;
        if (resolved == null) {
            resolved = styles.computeIfAbsent(new Key(font.families(), font.bold(), font.italic()), this::resolve);
            font.setHostFont(new Resolved(generation, resolved));
        }
        return resolved[decorations & 3];
    }

    /**
     * The characters Minecraft draws for {@code text}: as written, or in visual order and shaped when the text has
     * right-to-left parts.
     */
    public FormattedCharSequence sequence(String text, Style style) {
        return needsBidi(text) ? Language.getInstance().getVisualOrder(FormattedText.of(text, style))
                : FormattedCharSequence.forward(text, style);
    }

    /**
     * Obfuscated text ({@link Canvas#OBFUSCATED}, §k) for this frame: each character becomes another of the same
     * width, changing about twenty times a second. The game's own §k picks from every glyph of the font (Unifont's
     * tens of thousands), so each frame bakes new glyphs into new atlas pages and drawing slows down for good; a
     * small pool bakes a bounded set once.
     */
    public String obfuscate(String text, FontSpec font) {
        Map<Integer, int[]> pool = obfuscatePools.computeIfAbsent(new Key(font.families(), font.bold(), font.italic()), k -> pool(font));
        long tick = System.currentTimeMillis() / 50;
        StringBuilder out = new StringBuilder(text.length());
        int i = 0, n = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int[] same = Character.isWhitespace(cp) ? null : pool.get(Math.round(charWidth(cp, font) / font.scale() * 2));
            if (same == null || same.length == 0) {
                out.appendCodePoint(cp);
            } else {
                long h = (tick * 31 + n++) * 0x9E3779B97F4A7C15L;
                out.appendCodePoint(same[(int) ((h >>> 33) % same.length)]);
            }
        }
        return out.toString();
    }

    /** Pool characters by width (in half px at the native size). */
    private Map<Integer, int[]> pool(FontSpec font) {
        Map<Integer, List<Integer>> by = new HashMap<>();
        POOL.codePoints().forEach(cp -> by.computeIfAbsent(Math.round(charWidth(cp, font) / font.scale() * 2), w -> new java.util.ArrayList<>()).add(cp));
        Map<Integer, int[]> out = new HashMap<>();
        by.forEach((w, cps) -> out.put(w, cps.stream().mapToInt(Integer::intValue).toArray()));
        return out;
    }

    private static final String POOL = "!#$%&()*+-/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ^_abcdefghijklmnopqrstuvwxyz{|}~"
            + "的一是不了在人有我他这个们中来上大为和国地到以说时要就出会可也你对生能而子那得于着下自之年过发后作里用道行所然家种事成方多经么去法学如都同现当没动面起看定天分还进好小部其些主样理心";
    private final Map<Key, Map<Integer, int[]>> obfuscatePools = new HashMap<>();

    /** Forgets resolved fonts; resource packs may have added or removed some. */
    public void clearCache() {
        styles.clear();
        knownFonts.clear();
        obfuscatePools.clear();
        generation++;
    }

    private Style[] resolve(Key key) {
        Style base = Style.EMPTY.withFont(font(key.families())).withBold(key.bold()).withItalic(key.italic());
        Style underlined = base.withUnderlined(true);
        return new Style[] {base, underlined, base.withStrikethrough(true), underlined.withStrikethrough(true)};
    }

    //? if >=26 {
    private FontDescription font(List<String> families) {
        for (String family : families) {
            Identifier id = Identifier.tryParse(FontFamilies.fontId(family));
            if (id != null && knownFonts.computeIfAbsent(id, McFontMetrics::exists)) {
                return id.equals(FontDescription.DEFAULT.id()) ? FontDescription.DEFAULT : new FontDescription.Resource(id);
            }
        }
        return FontDescription.DEFAULT;
    }
    //?} else {
    /*private Identifier font(List<String> families) {
        for (String family : families) {
            Identifier id = Identifier.tryParse(FontFamilies.fontId(family));
            if (id != null && knownFonts.computeIfAbsent(id, McFontMetrics::exists)) return id;
        }
        return Style.DEFAULT_FONT;
    }
    *///?}

    private static boolean exists(Identifier font) {
        Identifier file = Identifier.fromNamespaceAndPath(font.getNamespace(), "font/" + font.getPath() + ".json");
        return Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
    }

    private static boolean needsBidi(String text) {
        if (Language.getInstance().isDefaultRightToLeft()) return true;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x0590) continue; // below Hebrew, everything is left to right
            switch (Character.getDirectionality(cp)) {
                case Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                     Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING, Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                     Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE -> {
                    return true;
                }
                default -> {}
            }
        }
        return false;
    }
}
