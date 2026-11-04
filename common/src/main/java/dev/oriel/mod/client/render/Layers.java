//? if >=26 {
package dev.oriel.mod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=26.2 {
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
//?} else {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
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
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.MaskComposite;
import dev.oriel.mod.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The offscreen layers behind {@link McCanvas}'s groups (filters, masks, clip-path, blend modes, rounded overflow
 * clips, {@code background-clip: text}).
 *
 * <p>A group's content is recorded, while the page is painted, into a {@link Layer}: a GUI render state of its own,
 * with a GUI renderer of its own to draw it. The group goes into the frame's GUI as a picture ({@link LayerRenderState});
 * when the GUI renderer prepares its pictures, {@link LayerRenderer} draws the layer into a window-sized offscreen
 * {@link RenderTarget} (vanilla's GUI renderer draws into the main render target only, so it is swapped for the
 * offscreen one while it draws), runs its filters and combines its masks there, and blits the result into the frame.
 * Everything about a layer is a GUI render state drawn into a target, filters and masks included, so the
 * version-specific parts are the targets, the swap and the pipelines below.
 *
 * <p>Layers are pooled and handed out again once a frame has been drawn: a frame's pictures are all prepared before
 * the frame's GUI is drawn, and the next frame's page is painted after it. Targets are too, and the ones a group only
 * needs on the way (filter steps, masks) are {@link #release released} as soon as they have been read, for the
 * next group of the frame: the GPU runs the passes in the order they are recorded. Targets have no depth buffer
 * (vanilla's GUI renderer draws without one when a target has none), to halve their memory.
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
    private static int layersUsed;
    private static final List<RenderTarget> targets = new ArrayList<>();
    private static final Deque<RenderTarget> free = new ArrayDeque<>();
    /** A layer was drawn since the pools were last handed out again. */
    private static boolean drawn;
    /** Beyond this many layers in a frame, the pools are handed out again anyway (pictures that were never drawn). */
    private static final int MAX_LAYERS = 512;

    // ---- Pools ----

    /** A page is about to be painted: when the last frame's layers have been drawn, they are free again. */
    static void beginFrame() {
        if (drawn || layersUsed > MAX_LAYERS) {
            layersUsed = 0;
            free.clear();
            free.addAll(targets);
            drawn = false;
        }
    }

    /** An empty layer to record into. */
    static Layer layer() {
        if (layersUsed == layers.size()) layers.add(new Layer());
        return layers.get(layersUsed++);
    }

    /** A cleared window-sized target, the caller's until it is {@link #release}d or the frame has been drawn. */
    static RenderTarget target() {
        WindowRenderState window = window();
        RenderTarget t = free.poll();
        if (t == null) {
            t = newTarget(window.width, window.height);
            targets.add(t);
        } else if (t.width != window.width || t.height != window.height) {
            t.resize(window.width, window.height);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(t.getColorTexture(), GuiRenderer.CLEAR_COLOR);
        return t;
    }

    /** {@code t} has been read for the last time this frame: the next {@link #target} can have it. */
    static void release(RenderTarget t) {
        free.push(t);
    }

    /** Resources reloaded (the window can have changed): drop the pools, so they are made again as needed. */
    static void reload() {
        for (RenderTarget t : targets) t.destroyBuffers();
        targets.clear();
        free.clear();
        for (Layer l : layers) l.renderer.close();
        layers.clear();
        layersUsed = 0;
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
        blit(from, into, pipeline, -1);
    }

    /** {@link #blit(RenderTarget, RenderTarget, RenderPipeline)} multiplied by {@code color} (premultiplied). */
    static void blit(RenderTarget from, RenderTarget into, RenderPipeline pipeline, int color) {
        Layer l = layer();
        WindowRenderState window = window();
        int scale = Math.max(1, window.guiScale);
        l.state.addBlitToCurrentLayer(blit(pipeline, from, 0, 0, Math.ceilDiv(window.width, scale),
                Math.ceilDiv(window.height, scale), color, null));
        draw(l, into);
    }

    /**
     * A {@code backdrop-filter} in a group (Filter Effects 2 §3): what {@code group} holds so far, over what is under
     * the page for the {@code page} group (the frame so far: the world, or the panorama), through the filters within
     * {@code area}, cut to the backdrop's shape, drawn into {@code group}.
     */
    static void backdrop(RenderTarget group, LayerRenderState.Backdrop backdrop, boolean page, float[] area) {
        RenderTarget under = target();
        if (page) blit(mainTarget(), under, OVER);
        blit(group, under, OVER);
        under = filter(under, backdrop.filters(), area);
        RenderTarget shape = target();
        draw(backdrop.shape(), shape);
        blit(shape, under, DESTINATION_IN);
        release(shape);
        int a = Math.round(Math.clamp(backdrop.alpha(), 0, 1) * 255);
        blit(under, group, OVER, a << 24 | a << 16 | a << 8 | a);
        release(under);
    }

    private static RenderTarget mainTarget() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        //?} else
        //return Minecraft.getInstance().getMainRenderTarget();
    }

    /**
     * The blit of {@code from}'s pixels under GUI rectangle (x0, y0)–(x1, y1) onto the same rectangle, multiplied by
     * {@code color} (premultiplied, as the target is).
     */
    static BlitRenderState blit(RenderPipeline pipeline, RenderTarget from, int x0, int y0, int x1, int y1, int color,
                                @Nullable ScreenRectangle scissor) {
        float[] uv = uv(from, x0, y0, x1, y1);
        return new BlitRenderState(pipeline, texture(from), new Matrix3x2f(), x0, y0, x1, y1, uv[0], uv[2], uv[1], uv[3],
                color, scissor, null);
    }

    /** Texture coordinates {u0, v0, u1, v1} of GUI rectangle (x0, y0)–(x1, y1) in a target (drawn with y up). */
    private static float[] uv(RenderTarget t, float x0, float y0, float x1, float y1) {
        float scale = Math.max(1, window().guiScale), w = t.width, h = t.height;
        return new float[] {x0 * scale / w, 1 - y0 * scale / h, x1 * scale / w, 1 - y1 * scale / h};
    }

    /** A target's colour, sampled pixel for pixel; beyond its edges, its edge (blurs reach there). */
    private static TextureSetup texture(RenderTarget t) {
        return TextureSetup.singleTexture(t.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    static WindowRenderState window() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        //?} else
        //return Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState;
    }

    private static RenderTarget newTarget(int width, int height) {
        //? if >=26.3 {
        return new TextureTarget("oriel layer", width, height, GpuFormat.RGBA8_UNORM, null);
        //?} else if >=26.2 {
        /*return new TextureTarget("oriel layer", width, height, false, GpuFormat.RGBA8_UNORM);
        *///?} else
        //return new TextureTarget("oriel layer", width, height, false);
    }

    // ---- Filters ----

    /**
     * {@code from} through {@code filters} in order (Filter Effects 1), drawn within GUI rectangle {@code area}
     * {x0, y0, x1, y1}, which grows by what blurs and shadows reach. Returns the target with the result, {@code from}
     * itself when there are no filters; the others it used are released, {@code from} too when it is not returned.
     */
    static RenderTarget filter(RenderTarget from, List<Filter> filters, float[] area) {
        for (Filter f : filters) {
            RenderTarget out;
            switch (f.kind()) {
                case BLUR -> {
                    if (f.amount() <= 0) continue;
                    grow(area, 3 * f.amount());
                    out = blur(from, area, f.amount());
                }
                case DROP_SHADOW -> {
                    // The input's alpha, coloured, moved and blurred (sigma half the radius), under the input.
                    float sigma = f.amount() / 2;
                    grow(area, Math.max(Math.abs(f.x()), Math.abs(f.y())) + 3 * sigma);
                    out = target();
                    pass(from, out, TINT, area, f.x(), f.y(), premultiplied(f.color()), 0);
                    if (sigma > 0) {
                        RenderTarget tint = out;
                        out = blur(tint, area, sigma);
                        release(tint);
                    }
                    blit(from, out, OVER);
                }
                default -> {
                    out = target();
                    pass(from, out, COLOR_FILTERS.get(f.kind()), area, 0, 0, -1, f.amount());
                }
            }
            release(from);
            from = out;
        }
        return from;
    }

    /** {@code from} blurred by a Gaussian of {@code sigma} GUI px, in two passes (into a new target). */
    private static RenderTarget blur(RenderTarget from, float[] area, float sigma) {
        float deviceSigma = sigma * Math.max(1, window().guiScale);
        RenderTarget across = target();
        pass(from, across, BLUR_X, area, 0, 0, -1, deviceSigma);
        RenderTarget out = target();
        pass(across, out, BLUR_Y, area, 0, 0, -1, deviceSigma);
        release(across);
        return out;
    }

    /** Draws {@code from} into {@code into} through {@code pipeline} within {@code area}, sampling (dx, dy) GUI px back. */
    private static void pass(RenderTarget from, RenderTarget into, RenderPipeline pipeline, float[] area, float dx, float dy,
                             int color, float amount) {
        float[] uv = uv(from, area[0] - dx, area[1] - dy, area[2] - dx, area[3] - dy);
        int x0 = (int) Math.floor(area[0]), y0 = (int) Math.floor(area[1]);
        ScreenRectangle bounds = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(area[2]) - x0),
                Math.max(1, (int) Math.ceil(area[3]) - y0));
        Layer l = layer();
        l.state.addGuiElement(new FilterQuad(pipeline, texture(from), area[0], area[1], area[2], area[3], uv[0], uv[1], uv[2],
                uv[3], color, amount, bounds));
        draw(l, into);
    }

    /** Grows {x0, y0, x1, y1} by {@code by} on every side, within the screen. */
    private static void grow(float[] area, float by) {
        WindowRenderState window = window();
        int scale = Math.max(1, window.guiScale);
        area[0] = Math.max(0, area[0] - by);
        area[1] = Math.max(0, area[1] - by);
        area[2] = Math.min(Math.ceilDiv(window.width, scale), area[2] + by);
        area[3] = Math.min(Math.ceilDiv(window.height, scale), area[3] + by);
    }

    private static int premultiplied(int argb) {
        float a = (argb >>> 24) / 255f;
        return argb & 0xFF000000 | Math.round((argb >> 16 & 0xFF) * a) << 16 | Math.round((argb >> 8 & 0xFF) * a) << 8
                | Math.round((argb & 0xFF) * a);
    }

    /**
     * A quad for the filter pipelines: (x0, y0)–(x1, y1) in GUI px, sampling the source at (u0, v0)–(u1, v1), with
     * {@code amount} (the filter's) in {@code LineWidth}.
     */
    private record FilterQuad(RenderPipeline pipeline, TextureSetup textureSetup, float x0, float y0, float x1, float y1,
                              float u0, float v0, float u1, float v1, int color, float amount, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public @Nullable ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            vertex(consumer, x0, y0, u0, v0);
            vertex(consumer, x0, y1, u0, v1);
            vertex(consumer, x1, y1, u1, v1);
            vertex(consumer, x1, y0, u1, v0);
        }

        private void vertex(VertexConsumer consumer, float x, float y, float u, float v) {
            consumer.addVertex(x, y, 0).setColor(color).setUv(u, v).setLineWidth(amount);
        }
    }

    // ---- Pipelines ----

    /** The filter passes' vertices: position, colour, texture coordinates and the filter's amount. */
    //? if >=26.2 {
    private static final VertexFormat FILTER_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .build();
    //?} else {
    /*private static final VertexFormat FILTER_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();
    *///?}

    /** Source-over of premultiplied colour: a layer drawn onto what is under it. */
    static final RenderPipeline OVER = blend("layer_over", "ONE", "ONE_MINUS_SRC_ALPHA", "ONE", "ONE_MINUS_SRC_ALPHA");
    /** Keeps what is there where the source is opaque: a group's content cut by its mask. */
    static final RenderPipeline DESTINATION_IN = blend("layer_destination_in", "ZERO", "SRC_ALPHA", "ZERO", "SRC_ALPHA");
    /** {@code mask-composite: subtract}: the new mask where the ones before are not. */
    static final RenderPipeline SUBTRACT = blend("layer_subtract", "ONE_MINUS_DST_ALPHA", "ZERO", "ONE_MINUS_DST_ALPHA", "ZERO");
    /** {@code mask-composite: intersect}. */
    static final RenderPipeline INTERSECT = blend("layer_intersect", "DST_ALPHA", "ZERO", "DST_ALPHA", "ZERO");
    /** {@code mask-composite: exclude}: where exactly one of them is. */
    static final RenderPipeline EXCLUDE = blend("layer_exclude", "ONE_MINUS_DST_ALPHA", "ONE_MINUS_SRC_ALPHA",
            "ONE_MINUS_DST_ALPHA", "ONE_MINUS_SRC_ALPHA");
    /** {@code mix-blend-mode: multiply} (exact over an opaque backdrop, as the GUI's is). */
    static final RenderPipeline MULTIPLY = blend("layer_multiply", "DST_COLOR", "ONE_MINUS_SRC_ALPHA", "ONE", "ONE_MINUS_SRC_ALPHA");
    /** {@code mix-blend-mode: screen}. */
    static final RenderPipeline SCREEN = blend("layer_screen", "ONE", "ONE_MINUS_SRC_COLOR", "ONE", "ONE_MINUS_SRC_ALPHA");
    /** {@code mix-blend-mode: plus-lighter}. */
    static final RenderPipeline PLUS_LIGHTER = blend("layer_plus_lighter", "ONE", "ONE", "ONE", "ONE");

    /** A Gaussian blur along x, then along y; sigma in device px. */
    static final RenderPipeline BLUR_X = filter("BLUR_X"), BLUR_Y = filter("BLUR_Y");
    /** A drop shadow's colour where the source is opaque. */
    static final RenderPipeline TINT = filter("TINT");
    private static final Map<Filter.Kind, RenderPipeline> COLOR_FILTERS = new EnumMap<>(Filter.Kind.class);

    static {
        for (Filter.Kind kind : Filter.Kind.values()) {
            if (kind != Filter.Kind.BLUR && kind != Filter.Kind.DROP_SHADOW) COLOR_FILTERS.put(kind, filter(kind.name()));
        }
    }

    private static boolean compiled, failed;

    /** Whether layers can be drawn: their pipelines compile (the first time this is asked, again after a reload). */
    static boolean available() {
        if (renderers == null) return false;
        if (compiled || failed) return compiled;
        RuntimeException error = null;
        List<RenderPipeline> all = new ArrayList<>(List.of(OVER, DESTINATION_IN, SUBTRACT, INTERSECT, EXCLUDE, MULTIPLY,
                SCREEN, PLUS_LIGHTER, BLUR_X, BLUR_Y, TINT));
        all.addAll(COLOR_FILTERS.values());
        try {
            compiled = true;
            for (RenderPipeline p : all) {
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
            Constants.LOG.warn("Oriel's layer shaders are not available; filters, masks and blend modes are not drawn", error);
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

    /** A blit with blend factors (names shared by every version's factor enums). */
    private static RenderPipeline blend(String name, String srcColor, String dstColor, String srcAlpha, String dstAlpha) {
        return pipeline(name, "core/layer", null, false, srcColor, dstColor, srcAlpha, dstAlpha);
    }

    /** A filter pass: {@code define} picks the filter; it replaces what is under it. */
    private static RenderPipeline filter(String define) {
        return pipeline("layer_filter_" + define.toLowerCase(java.util.Locale.ROOT), "core/layer_filter", define, true,
                "ONE", "ZERO", "ONE", "ZERO");
    }

    private static RenderPipeline pipeline(String name, String shaderPath, @Nullable String define, boolean amount,
                                           String srcColor, String dstColor, String srcAlpha, String dstAlpha) {
        Identifier shader = Constants.id(shaderPath);
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
                .withVertexBinding(0, amount ? FILTER_FORMAT : DefaultVertexFormat.POSITION_TEX_COLOR)
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
                .withVertexFormat(amount ? FILTER_FORMAT : DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withCull(false);
        *///?}
        if (define != null) builder.withShaderDefine(define);
        //? if <26.3
        //builder.withShaderDefine("ORIEL_26_2");
        return builder.build();
    }
}
//?}
