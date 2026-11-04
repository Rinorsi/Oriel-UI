//? if >=26 {
package dev.oriel.mod.client.render;

import dev.oriel.engine.style.MaskComposite;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One of {@link McCanvas}'s groups, for {@link LayerRenderer}: the {@code content} layer, cut by the {@code masks}
 * (combined with {@code ops}, the first one's ignored), drawn onto the frame in GUI rectangle (x0, y0)–(x1, y1) with
 * {@code opacity} and {@code blend}.
 */
public record LayerRenderState(Layers.Layer content, List<Layers.Layer> masks, List<MaskComposite> ops, float opacity,
                               dev.oriel.engine.style.BlendMode blend, int x0, int y0, int x1, int y1,
                               @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    @Override
    public float scale() {
        return 1;
    }
}
//?}
