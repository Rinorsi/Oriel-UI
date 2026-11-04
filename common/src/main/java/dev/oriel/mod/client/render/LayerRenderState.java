//? if >=26 {
package dev.oriel.mod.client.render;

import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.MaskComposite;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One of {@link McCanvas}'s groups, for {@link LayerRenderer}: its {@code content} through its {@code filters}, cut by
 * the {@code masks} (combined with {@code ops}, the first one's ignored), drawn onto the frame in GUI rectangle
 * (x0, y0)–(x1, y1) with {@code opacity} and {@code blend}. The content is layers drawn one after the other, with the
 * group's backdrops between them; the {@code page} group's backdrop starts from what is under the page.
 */
public record LayerRenderState(List<Segment> content, boolean page, List<Filter> filters, List<Layers.Layer> masks, List<MaskComposite> ops, float opacity,
                               dev.oriel.engine.style.BlendMode blend, int x0, int y0, int x1, int y1,
                               @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    @Override
    public float scale() {
        return 1;
    }

    /** A part of a group's content, in painting order. */
    public sealed interface Segment {}

    /** Content drawn into the group. */
    public record Draw(Layers.Layer layer) implements Segment {}

    /**
     * A {@code backdrop-filter}: what the group holds so far, through {@code filters}, inside the {@code shape}
     * layer, drawn over it with {@code alpha}.
     */
    public record Backdrop(Layers.Layer shape, List<Filter> filters, float alpha) implements Segment {}
}
//?}
