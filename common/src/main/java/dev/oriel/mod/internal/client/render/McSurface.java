package dev.oriel.mod.internal.client.render;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oriel.engine.host.PixelSurface;
import dev.oriel.mod.internal.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A canvas's pixels in game: a {@link NativeImage} in a {@link DynamicTexture} registered as
 * {@code oriel:canvas/<n>}, so {@link McCanvas} draws it like any texture. An upload sends only the rows written
 * since the last one, not the whole (up to 2048²) image. Render thread only.
 */
public final class McSurface implements PixelSurface {
    private static int nextId;
    private static final boolean METRICS = Boolean.getBoolean("oriel.paintMetrics");
    private static long liveBytes, uploadedBytes, uploads;
    private final long allocatedBytes;
    /** The canvas textures alive now: registered at run time, so no resource pack knows them. */
    private static final java.util.Set<Identifier> LIVE = new java.util.HashSet<>();

    private final Identifier id = Constants.id("canvas/" + nextId++);
    private final NativeImage pixels;
    private final DynamicTexture texture;
    /** Rows written since the last upload: {@code [dirtyTop, dirtyBottom)}, empty when top >= bottom. */
    private int dirtyTop = Integer.MAX_VALUE, dirtyBottom;

    public McSurface(int width, int height) {
        allocatedBytes = (long) width * height * 4;
        pixels = new NativeImage(width, height, true);
        texture = new DynamicTexture(() -> "Oriel " + id, pixels); // uploads the cleared image
        Minecraft.getInstance().getTextureManager().register(id, texture);
        LIVE.add(id);
        if (METRICS) liveBytes += allocatedBytes;
    }

    /** Actual dirty-row submissions; initial DynamicTexture allocation is not an upload() call. */
    public static java.util.Map<String, Long> statistics() {
        return java.util.Map.of("enabled", METRICS ? 1L : 0L, "liveTextureBytes", liveBytes,
                "dirtyRowUploadBytes", uploadedBytes, "dirtyRowUploads", uploads);
    }

    /** Whether {@code texture} is a live canvas's pixels (drawn as is, not looked up in the resource packs). */
    public static boolean isCanvas(Identifier texture) {
        return LIVE.contains(texture);
    }

    @Override
    public int width() {
        return pixels.getWidth();
    }

    @Override
    public int height() {
        return pixels.getHeight();
    }

    @Override
    public String url() {
        return id.toString();
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int argb) {
        pixels.fillRect(x, y, width, height, argb);
        written(y, height);
    }

    @Override
    public void getPixels(int x, int y, int width, int height, int[] argb) {
        for (int row = 0; row < height; row++) {
            for (int i = 0; i < width; i++) argb[row * width + i] = pixels.getPixel(x + i, y + row);
        }
    }

    @Override
    public void setPixels(int x, int y, int width, int height, int[] argb) {
        for (int row = 0; row < height; row++) {
            for (int i = 0; i < width; i++) pixels.setPixel(x + i, y + row, argb[row * width + i]);
        }
        written(y, height);
    }

    @Override
    public void upload() {
        if (dirtyTop >= dirtyBottom) return;
        int rowBytes = width() * 4, rows = dirtyBottom - dirtyTop;
        //? if >=26.2 {
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(),
                pixels.getPixelBytes().slice(dirtyTop * rowBytes, rows * rowBytes), 0, 0, 0, dirtyTop, width(), rows);
        //?} else
        //RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), pixels, 0, 0, 0, dirtyTop, width(), rows, 0, dirtyTop);
        dirtyTop = Integer.MAX_VALUE;
        dirtyBottom = 0;
        if (METRICS) { uploadedBytes += (long) rowBytes * rows; uploads++; }
    }

    /** Releases the texture, which frees the image too. */
    @Override
    public void dispose() {
        if (LIVE.remove(id) && METRICS) liveBytes -= allocatedBytes;
        Minecraft.getInstance().getTextureManager().release(id);
    }

    private void written(int y, int height) {
        dirtyTop = Math.min(dirtyTop, y);
        dirtyBottom = Math.max(dirtyBottom, y + height);
    }

}
