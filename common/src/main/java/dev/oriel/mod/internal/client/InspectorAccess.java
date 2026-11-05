package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;
import dev.oriel.engine.dom.Document;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

/** Internal optional-tool seam. No signature resolves a development implementation. */
public interface InspectorAccess {
    static boolean enabled() {
        return DevFlags.INSPECTOR_AVAILABLE && (dev.oriel.mod.internal.platform.Services.PLATFORM.isDevelopmentEnvironment()
                || dev.oriel.mod.internal.OrielConfig.CLIENT_INSPECTOR.get());
    }

    static @Nullable InspectorAccess open(DocumentDriver target) {
        return enabled() ? new DevInspector(target) : null;
    }

    int resize(int width, int height);
    void close();
    void extract(GuiGraphicsExtractor graphics, @Nullable Document document, int mouseX, int mouseY);
    boolean mouseMoved(double x, double y);
    boolean mouseClicked(MouseButtonEvent event);
    boolean mouseReleased(MouseButtonEvent event);
    boolean mouseScrolled(double x, double y, double scrollX, double scrollY);
    boolean keyPressed(KeyEvent event);
    boolean keyReleased(KeyEvent event);
    boolean charTyped(CharacterEvent event);
}
