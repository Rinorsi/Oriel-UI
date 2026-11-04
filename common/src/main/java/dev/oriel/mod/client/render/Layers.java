//? if >=26 {
package dev.oriel.mod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.2 {
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
//?} else {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
*///?}
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.PrimitiveTopology;
*///?} else {
/*import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
*///?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.oriel.engine.style.BlendMode;
import dev.oriel.engine.style.MaskComposite;
import dev.oriel.mod.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The offscreen layers behind {@link McCanvas}'s groups (filters, masks, clip-path, blend modes, rounded overflow
 * clips, {@code background-clip: text}).
 *
 * <p>A group's content is recorded, while the page is painted, into a {@link Layer}: a GUI render state of its own,
 * with a GUI renderer of its own to draw it. The group goes into the frame's GUI as a picture ({@link LayerRenderState});
 * when the GUI renderer prepares its pictures, {@link LayerRenderer} draws the layer into a window-sized offscreen
 * {@link RenderTarget} (vanilla's GUI renderer draws into the main render target only, so it is swapped for the
 * offscreen one while it draws), combines its masks there, and blits the result into the frame. Everything about a
 * layer is a GUI render state drawn into a target, masks and compositing included, so the version-specific parts are
 * the targets, the swap and the pipelines below.
 *
 * <p>Layers and targets are pooled and handed out again once a frame has been drawn: a frame's pictures are all
 * prepared before the frame's GUI is drawn, and the next frame's page is painted after it.
 */
public final class Layers {
    private Layers() {}

    /** A GUI render state and the GUI renderer that draws it. */
    static final class Layer {
        final GuiRenderState state = new GuiRenderState();
        final GuiRenderer renderer = renderers.apply(state);
    }

    /**
     * Makes a layer's GUI renderer for its render state, with {@link GuiSceneRenderer} and {@link LayerRenderer} among
     * its picture renderers. Each loader sets it: NeoForge's GUI renderer takes its picture renderers as registrations.
     */
    private static @Nullable Function<GuiRenderState, GuiRenderer> renderers;

    public static void renderers(Function<GuiRenderState, GuiRenderer> factory) {
        renderers = factory;
    }

    private static final List<Layer> layers = new ArrayList<>();
    private static final List<RenderTarget> targets = new ArrayList<>();
    private static int layersUsed, targetsUsed;
    /** A layer was drawn since the pools were last handed out again. */
    private static boolean drawn;
    /** Beyond this many layers in a frame, the pools are handed out again anyway (pictures that were never drawn). */
    private static final int MAX_LAYERS = 512;

    // ---- Pools ----

    /** A page is about to be painted: when the last frame's layers have been drawn, they are free again. */
    static void beginFrame() {
        if (drawn || layersUsed > MAX_LAYERS) {
            layersUsed = 0;
            targetsUsed = 0;
            drawn = false;
        }
    }

    /** An empty layer to record into. */
    static Layer layer() {
        if (layersUsed == layers.size()) layers.add(new Layer());
        return layers.get(layersUsed++);
    }

    /** A cleared window-sized target, free until the frame has been drawn. */
    static RenderTarget target() {
        WindowRenderState window = window();
        if (targetsUsed == targets.size()) targets.add(newTarget(window.width, window.height));
        RenderTarget t = targets.get(targetsUsed++);
        if (t.width != window.width || t.height != window.height) t.resize(window.width, window.height);
        //? if >=26.2 {
        double far = 0.0; // 26.2 reversed depth
        //?} else
        //double far = 1.0;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(t.getColorTexture(), GuiRenderer.CLEAR_COLOR, t.getDepthTexture(), far);
        return t;
    }

    /** Resources reloaded (the window can have changed): drop the pools, so they are made again as needed. */
    static void reload() {
        for (RenderTarget t : targets) t.destroyBuffers();
        targets.clear();
        for (Layer l : layers) l.renderer.close();
        layers.clear();
        layersUsed = targetsUsed = 0;
        compiled = failed = false;
    }

    // ---- Drawing ----

    /** Draws {@code layer}'s render state into {@code into} (over what is there), and empties the state. */
    static void draw(Layer layer, RenderTarget into) {
        drawn = true;
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        RenderTarget main = mc.gameRenderer.mainRenderTarget;
        mc.gameRenderer.mainRenderTarget = into;
        //?} else {
        /*RenderTarget main = mc.mainRenderTarget;
        mc.mainRenderTarget = into;
        *///?}
        try {
            //? if >=26.2 {
            layer.renderer.render();
            //?} else
            //layer.renderer.render(mc.gameRenderer.fogRenderer.getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
            layer.renderer.endFrame();
        } finally {
            //? if >=26.2 {
            mc.gameRenderer.mainRenderTarget = main;
            //?} else
            //mc.mainRenderTarget = main;
        }
    }

    /** Draws {@code from} over {@code into}, pixel for pixel, with {@code pipeline}'s blending. */
    static void blit(RenderTarget from, RenderTarget into, RenderPipeline pipeline) {
        Layer l = layer();
        WindowRenderState window = window();
        int scale = Math.max(1, window.guiScale);
        l.state.addBlitToCurrentLayer(blit(pipeline, from, 0, 0, Math.ceilDiv(window.width, scale),
                Math.ceilDiv(window.height, scale), -1, null));
        draw(l, into);
    }

    /**
     * The blit of {@code from}'s pixels under GUI rectangle (x0, y0)–(x1, y1) onto the same rectangle, multiplied by
     * {@code color} (premultiplied, as the target is).
     */
    static BlitRenderState blit(RenderPipeline pipeline, RenderTarget from, int x0, int y0, int x1, int y1, int color,
                                net.minecraft.client.gui.navigation.@Nullable ScreenRectangle scissor) {
        WindowRenderState window = window();
        float scale = Math.max(1, window.guiScale), w = from.width, h = from.height;
        // Targets are drawn with y up: the top of the screen is v = 1.
        return new BlitRenderState(pipeline,
                TextureSetup.singleTexture(from.getColorTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                new Matrix3x2f(), x0, y0, x1, y1, x0 * scale / w, x1 * scale / w, 1 - y0 * scale / h, 1 - y1 * scale / h,
                color, scissor, null);
    }

    static WindowRenderState window() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        //?} else
        //return Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState;
    }

    private static RenderTarget newTarget(int width, int height) {
        //? if >=26.3 {
        return new TextureTarget("oriel layer", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        //?} else if >=26.2 {
        /*return new TextureTarget("oriel layer", width, height, true, GpuFormat.RGBA8_UNORM);
        *///?} else
        //return new TextureTarget("oriel layer", width, height, true);
    }

    // ---- Pipelines ----

    /** Source-over of premultiplied colour: a layer drawn onto what is under it. */
    static final RenderPipeline OVER = pipeline("layer_over", "ONE", "ONE_MINUS_SRC_ALPHA", "ONE", "ONE_MINUS_SRC_ALPHA");
    /** Keeps what is there where the source is opaque: a group's content cut by its mask. */
    static final RenderPipeline DESTINATION_IN = pipeline("layer_destination_in", "ZERO", "SRC_ALPHA", "ZERO", "SRC_ALPHA");
    /** {@code mask-composite: subtract}: the new mask where the ones before are not. */
    static final RenderPipeline SUBTRACT = pipeline("layer_subtract", "ONE_MINUS_DST_ALPHA", "ZERO", "ONE_MINUS_DST_ALPHA", "ZERO");
    /** {@code mask-composite: intersect}. */
    static final RenderPipeline INTERSECT = pipeline("layer_intersect", "DST_ALPHA", "ZERO", "DST_ALPHA", "ZERO");
    /** {@code mask-composite: exclude}: where exactly one of them is. */
    static final RenderPipeline EXCLUDE = pipeline("layer_exclude", "ONE_MINUS_DST_ALPHA", "ONE_MINUS_SRC_ALPHA",
            "ONE_MINUS_DST_ALPHA", "ONE_MINUS_SRC_ALPHA");
    /** {@code mix-blend-mode: multiply} (exact over an opaque backdrop, as the GUI's is). */
    static final RenderPipeline MULTIPLY = pipeline("layer_multiply", "DST_COLOR", "ONE_MINUS_SRC_ALPHA", "ONE", "ONE_MINUS_SRC_ALPHA");
    /** {@code mix-blend-mode: screen}. */
    static final RenderPipeline SCREEN = pipeline("layer_screen", "ONE", "ONE_MINUS_SRC_COLOR", "ONE", "ONE_MINUS_SRC_ALPHA");
    /** {@code mix-blend-mode: plus-lighter}. */
    static final RenderPipeline PLUS_LIGHTER = pipeline("layer_plus_lighter", "ONE", "ONE", "ONE", "ONE");

    private static boolean compiled, failed;

    /** Whether layers can be drawn: their pipelines compile (the first time this is asked, again after a reload). */
    static boolean available() {
        if (renderers == null) return false;
        if (compiled || failed) return compiled;
        RuntimeException error = null;
        try {
            compiled = true;
            for (RenderPipeline p : List.of(OVER, DESTINATION_IN, SUBTRACT, INTERSECT, EXCLUDE, MULTIPLY, SCREEN, PLUS_LIGHTER)) {
                //? if >=26.3 {
                compiled &= RenderSystem.getCompiledPipelineNullable(p) != null;
                //?} else
                //compiled &= RenderSystem.getDevice().precompilePipeline(p).isValid();
            }
        } catch (RuntimeException e) {
            compiled = false;
            error = e;
        }
        if (!compiled) {
            failed = true;
            Constants.LOG.warn("Oriel's layer shader is not available; filters, masks and blend modes are not drawn", error);
        }
        return compiled;
    }

    /** The pipeline that combines a mask layer with the ones before it. */
    static RenderPipeline mask(MaskComposite op) {
        return switch (op) {
            case ADD -> OVER;
            case SUBTRACT -> SUBTRACT;
            case INTERSECT -> INTERSECT;
            case EXCLUDE -> EXCLUDE;
        };
    }

    /** The pipeline that draws a group onto the frame with {@code blend}, or null for one there is none for. */
    static @Nullable RenderPipeline blend(BlendMode blend) {
        return switch (blend) {
            case NORMAL -> OVER;
            case MULTIPLY -> MULTIPLY;
            case SCREEN -> SCREEN;
            case PLUS_LIGHTER -> PLUS_LIGHTER;
            default -> null;
        };
    }

    private static RenderPipeline pipeline(String name, String srcColor, String dstColor, String srcAlpha, String dstAlpha) {
        Identifier shader = Constants.id("core/layer");
        //? if >=26.2 {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Constants.id("pipeline/" + name))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.valueOf(srcColor),
                        BlendFactor.valueOf(dstColor), BlendFactor.valueOf(srcAlpha), BlendFactor.valueOf(dstAlpha))))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false);
        //?} else {
        /*RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Constants.id("pipeline/" + name))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.valueOf(srcColor),
                        DestFactor.valueOf(dstColor), SourceFactor.valueOf(srcAlpha), DestFactor.valueOf(dstAlpha))))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withCull(false);
        *///?}
        //? if <26.3
        //builder.withShaderDefine("ORIEL_26_2");
        return builder.build();
    }
}
//?}
