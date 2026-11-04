//? if >=26 {
package dev.oriel.mod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Draws {@link LayerRenderState}s, McCanvas's groups (see {@link Layers}): the content layer into an offscreen target,
 * its masks into another, combined there and cut into the content, then a blit of the result into the frame where the
 * group was painted. It replaces vanilla's picture drawing (a texture the size of the picture, drawn with a pose stack)
 * altogether. The loaders register it as {@link GuiSceneRenderer} is, and every layer's own GUI renderer has one, for
 * groups inside groups.
 */
public final class LayerRenderer extends PictureInPictureRenderer<LayerRenderState> {
    //? if <26.2 {
    /*public LayerRenderer(net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
        super(buffers);
    }
    *///?}

    @Override
    public Class<LayerRenderState> getRenderStateClass() {
        return LayerRenderState.class;
    }

    //? if >=26.2 {
    @Override
    public void prepare(LayerRenderState state, GuiRenderState out,
                        net.minecraft.client.renderer.feature.FeatureRenderDispatcher features, int guiScale) {
        draw(state, out);
    }

    @Override
    protected void renderToTexture(LayerRenderState state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector out) {}
    //?} else {
    /*@Override
    public void prepare(LayerRenderState state, GuiRenderState out, int guiScale) {
        draw(state, out);
    }

    @Override
    protected void renderToTexture(LayerRenderState state, PoseStack pose) {}
    *///?}

    private static void draw(LayerRenderState state, GuiRenderState out) {
        RenderTarget content = Layers.target();
        Layers.draw(state.content(), content);
        if (!state.masks().isEmpty()) {
            RenderTarget mask = Layers.target();
            Layers.draw(state.masks().getFirst(), mask);
            for (int i = 1; i < state.masks().size(); i++) {
                RenderTarget next = Layers.target();
                Layers.draw(state.masks().get(i), next);
                Layers.blit(next, mask, Layers.mask(state.ops().get(i)));
            }
            Layers.blit(mask, content, Layers.DESTINATION_IN);
        }
        RenderPipeline pipeline = Layers.blend(state.blend());
        int a = Math.round(Math.clamp(state.opacity(), 0, 1) * 255);
        out.addBlitToCurrentLayer(Layers.blit(pipeline == null ? Layers.OVER : pipeline, content, state.x0(), state.y0(),
                state.x1(), state.y1(), a << 24 | a << 16 | a << 8 | a, state.scissorArea()));
    }

    @Override
    protected String getTextureLabel() {
        return "oriel layer";
    }
}
//?}
