package dev.oriel.mod.internal.client.render;


import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.oriel.engine.paint.ScissorStack;
import dev.oriel.engine.paint.SmoothShape;
import dev.oriel.engine.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
//? if <26.3
//import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Minecraft's GUI drawing, the part of it that differs between Minecraft versions; this is the one for 26.x, where
 * a {@link GuiGraphicsExtractor} records render states that the GUI renderer draws at the end of the frame.
 *
 * <p>An instance is {@link McCanvas}'s backend for one frame: McCanvas keeps the transform, opacity and clip stacks and
 * computes bounds, and hands each primitive here already transformed and clipped. The static methods draw the few
 * things Oriel puts on screen without the engine (the error panel, the typing notice) and tooltips.
 */
public final class McGui {
    private final GuiGraphicsExtractor g;
    /**
     * Where this GUI's (0, 0) is on screen, in GUI px: 0 for the frame's, a layer's origin for one of
     * {@link McCanvas}'s groups (its offscreen target covers only the group). The canvas works in screen coordinates
     * throughout; everything handed to vanilla is moved by this, and the scissor handed back is moved back.
     */
    private final int ox, oy;
    private final Matrix3x2f shifted = new Matrix3x2f();

    public McGui(GuiGraphicsExtractor g) {
        this(g, 0, 0);
    }

    public McGui(GuiGraphicsExtractor g, int ox, int oy) {
        this.g = g;
        this.ox = ox;
        this.oy = oy;
    }

    /** {@code pose} in this GUI's coordinates (a copy when moved: render states keep their pose). */
    private Matrix3x2fc at(Matrix3x2fc pose) {
        return ox == 0 && oy == 0 ? pose : new Matrix3x2f().translation(-ox, -oy).mul(pose);
    }

    /** {@code pose} in this GUI's coordinates, for vanilla's pose stack (which copies it). */
    private Matrix3x2fc atStack(Matrix3x2fc pose) {
        return ox == 0 && oy == 0 ? pose : shifted.translation(-ox, -oy).mul(pose);
    }

    private @Nullable ScreenRectangle at(@Nullable ScreenRectangle r) {
        return r == null || ox == 0 && oy == 0 ? r : new ScreenRectangle(r.left() - ox, r.top() - oy, r.width(), r.height());
    }

    // ---- For McCanvas ----

    /**
     * What the GUI renderer can draw this frame: the framebuffer at the GUI scale it renders with (its scissors are
     * clamped to the framebuffer, and one clamped to nothing is a crash), within the screen and any scissor already
     * pushed. The framebuffer is not always the GUI size: {@code Window.setWindowed} resizes it at once, while the GUI
     * scale and size change only when the resize event arrives, so for a frame the GUI can reach past the framebuffer.
     */
    public ScissorStack drawableArea() {
        //? if >=26.2 {
        WindowRenderState window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        int scale = Math.max(1, window.guiScale), frameWidth = window.width, frameHeight = window.height;
        //?} else {
        /*com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        int scale = Math.max(1, window.getGuiScale()), frameWidth = window.getWidth(), frameHeight = window.getHeight();
        *///?}
        int width = Math.min(g.guiWidth(), Math.ceilDiv(frameWidth, scale));
        int height = Math.min(g.guiHeight(), Math.ceilDiv(frameHeight, scale));
        ScreenRectangle outer = g.scissorStack.peek();
        return outer == null ? new ScissorStack(0, 0, width, height) : new ScissorStack(Math.max(0, outer.left()),
                Math.max(0, outer.top()), Math.min(width, outer.right()), Math.min(height, outer.bottom()));
    }

    /** Saves vanilla's pose for the canvas, which sets it before each call; returns the transform it had. */
    public Matrix3x2f begin() {
        g.pose().pushMatrix();
        return new Matrix3x2f(g.pose());
    }

    /** Puts vanilla's pose back. */
    public void end() {
        g.pose().popMatrix();
    }

    /** Pushes a scissor, in GUI px on screen (the transform is not applied). */
    public void pushScissor(int left, int top, int right, int bottom) {
        g.pose().identity();
        g.enableScissor(left - ox, top - oy, right - ox, bottom - oy);
    }

    public void popScissor() {
        g.disableScissor();
    }

    /** The scissor elements are clipped to now, or null. */
    public @Nullable ScreenRectangle scissor() {
        ScreenRectangle r = g.scissorStack.peek();
        return r == null || ox == 0 && oy == 0 ? r : new ScreenRectangle(r.left() + ox, r.top() + oy, r.width(), r.height());
    }

    /** Whether text in this colour shows (vanilla skips text with alpha 0). */
    public static boolean textVisible(int argb) {
        return argb >>> 24 != 0;
    }

    /**
     * One rectangle in one colour: the vertices go (x0,y0) (x0,y1) (x1,y1) (x1,y0), wound as vanilla's when x0 < x1
     * and y0 < y1 (the caller swaps x0 and x1 under a mirroring transform; GUI pipelines cull back faces).
     *
     * @param bounds  the transformed bounding box clipped to the scissor
     */
    public void fill(Matrix3x2fc pose, float x0, float y0, float x1, float y1, int color, ScreenRectangle bounds,
              @Nullable ScreenRectangle scissor) {
        g.guiRenderState.addGuiElement(new RectRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), at(pose), x0, y0, x1,
                y1, false, 0, 0, 0, 0, color, at(scissor), at(bounds)));
    }

    /** A texture region (normalised UVs) multiplied by {@code color}, wound as {@link #fill}. */
    public void blit(Matrix3x2fc pose, Identifier texture, boolean smooth, float x0, float y0, float x1, float y1,
              float u0, float v0, float u1, float v1, int color, ScreenRectangle bounds, @Nullable ScreenRectangle scissor) {
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(texture);
        var sampler = smooth ? RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR) : t.getSampler();
        g.guiRenderState.addGuiElement(new RectRenderState(RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(t.getTextureView(), sampler), at(pose), x0, y0, x1, y1, true, u0, v0, u1, v1, color,
                at(scissor), at(bounds)));
    }

    /** Quads with a colour per vertex, 4 vertices each, already wound as vanilla's. */
    public void quads(Matrix3x2fc pose, float[] xy, int[] colors, ScreenRectangle bounds, @Nullable ScreenRectangle scissor) {
        g.guiRenderState.addGuiElement(new QuadsRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), at(pose), xy, colors,
                at(scissor), at(bounds)));
    }

    /** Whether {@link #shape} can draw: the shape shader is compiled. */
    public boolean shapesAvailable() {
        return SdfRenderState.available();
    }

    /** Resources reloaded: if the shape shader failed to compile, {@link #shapesAvailable} tries it again. */
    public static void reloadShapes() {
        SdfRenderState.reload();
        Layers.reload();
    }

    /**
     * A rounded box, border or box-shadow drawn by the shape shader as one quad; the parameters are
     * {@link SdfRenderState}'s.
     */
    public void shape(Matrix3x2fc pose, SmoothShape shape, float edge, short[] radii, int color, ScreenRectangle bounds,
               @Nullable ScreenRectangle scissor) {
        g.guiRenderState.addGuiElement(new SdfRenderState(at(pose), shape, edge, radii, color, at(scissor), at(bounds)));
    }

    /** Text at (x, y) in {@code m}, scaled by {@code scale}. */
    public void text(Matrix3x2fc m, float x, float y, float scale, FormattedCharSequence text, int color, boolean shadow) {
        g.pose().set(atStack(m)).translate(x, y).scale(scale, scale);
        g.text(Minecraft.getInstance().font, text, 0, 0, color, shadow);
    }

    /** A GUI sprite (nine-slice and tiling at its integer size {@code w × h}), at (x, y) in {@code m} scaled by (sx, sy). */
    public void sprite(Matrix3x2fc m, float x, float y, float sx, float sy, Identifier sprite, int w, int h, int color) {
        g.pose().set(atStack(m)).translate(x, y).scale(sx, sy);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, w, h, color);
    }

    /** An item at (x, y) in {@code m}, scaled from its 16 px, with count and durability when asked. */
    public void item(Matrix3x2fc m, float x, float y, float scale, ItemStack stack, boolean decorations) {
        g.pose().set(atStack(m)).translate(x, y).scale(scale, scale);
        g.item(stack, 0, 0);
        if (decorations) g.itemDecorations(Minecraft.getInstance().font, stack, 0, 0);
    }

    /**
     * A 3D scene in a screen box, a block {@code scale} GUI px, multiplied by {@code color}: a picture, blitted with the
     * colour premultiplied as the picture is, so fading scales every channel.
     */
    /** One of McCanvas's groups, drawn by {@link LayerRenderer}. */
    public void layer(LayerRenderState layer) {
        g.guiRenderState.addPicturesInPictureState(layer.moved(ox, oy));
    }

    public void scene(Scene scene, int color, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissor) {
        g.guiRenderState.addPicturesInPictureState(new GuiSceneRenderState(scene, premultiplied(color), x0 - ox, y0 - oy, x1 - ox,
                y1 - oy, scale, at(scissor)));
    }

    /** {@code argb} with its colour channels multiplied by its alpha. */
    private static int premultiplied(int argb) {
        float a = (argb >>> 24) / 255f;
        return argb & 0xFF000000 | (int) ((argb >> 16 & 0xFF) * a) << 16 | (int) ((argb >> 8 & 0xFF) * a) << 8
                | (int) ((argb & 0xFF) * a);
    }

    // ---- Panels, tooltips, the cursor ----

    /** From here on, everything is drawn above what was drawn before (vanilla's next stratum). */
    public static void nextLayer(GuiGraphicsExtractor g) {
        g.nextStratum();
    }

    /** Moves what is drawn next by ({@code x}, {@code y}) GUI px, until {@link #popTranslation}. */
    public static void pushTranslation(GuiGraphicsExtractor g, float x, float y) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
    }

    public static void popTranslation(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    public static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.outline(x, y, width, height, color);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        g.text(font, text, x, y, color, shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.text(font, text, x, y, color, shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        g.text(font, text, x, y, color, shadow);
    }

    /** A tooltip of already wrapped lines for this frame, drawn on top at its end. */
    public static void tooltip(GuiGraphicsExtractor g, Font font, List<FormattedCharSequence> lines, int x, int y) {
        g.setTooltipForNextFrame(font, lines, x, y);
    }

    /** A tooltip of lines that are not wrapped, for this frame. */
    public static void componentTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int x, int y) {
        g.setComponentTooltipForNextFrame(font, lines, x, y);
    }

    /**
     * An item tooltip of {@code lines} (the item's own and any after them) for this frame: with the stack's tooltip
     * image and style, and the gap after its name.
     */
    public static void itemTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, ItemStack stack, int x, int y) {
        //? if >=26.3 {
        g.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), x, y, stack.get(DataComponents.TOOLTIP_STYLE), true);
        //?} else {
        /*g.setTooltipForNextFrame(font, lines.stream().map(Component::getVisualOrderText).toList(), stack.getTooltipImage(),
                DefaultTooltipPositioner.INSTANCE, x, y, true, stack.get(DataComponents.TOOLTIP_STYLE));
        *///?}
    }

    /**
     * Draws the tooltips set for this frame now, for HUD overlays drawn after the screen's own pass has drawn its
     * tooltips.
     */
    public static void drawTooltipsNow(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.extractDeferredElements(mouseX, mouseY, partialTick);
    }

    /**
     * Shows the CSS cursor a page asks for while the pointer is over it; vanilla resets it every frame. Screens' pages
     * and HUD overlays both ask ({@code screen} tells them apart).
     */
    public static void cursor(GuiGraphicsExtractor g, Cursor cursor, boolean screen) {
        if (cursor != Cursor.AUTO && cursor != Cursor.DEFAULT) g.requestCursor(cursorType(cursor));
    }

    /** A page's cursor is no longer asked for (its screen closed). Nothing to do: vanilla resets it every frame. */
    public static void resetCursor() {}

    private static CursorType cursorType(Cursor cursor) {
        return switch (cursor) {
            case POINTER, GRAB, GRABBING -> CursorTypes.POINTING_HAND;
            case TEXT -> CursorTypes.IBEAM;
            case EW_RESIZE -> CursorTypes.RESIZE_EW;
            case NS_RESIZE -> CursorTypes.RESIZE_NS;
            case MOVE -> CursorTypes.RESIZE_ALL;
            case NOT_ALLOWED -> CursorTypes.NOT_ALLOWED;
            case CROSSHAIR -> CursorTypes.CROSSHAIR;
            default -> CursorTypes.ARROW;
        };
    }
}
