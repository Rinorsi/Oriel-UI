package dev.oriel.mod.client;

import dev.oriel.kit.OrielKit;
import dev.oriel.mod.Constants;
import dev.oriel.mod.client.render.FolderTextures;
import dev.oriel.mod.client.render.McFontMetrics;
import dev.oriel.mod.client.render.McGui;
import dev.oriel.mod.client.render.McImages;
import dev.oriel.mod.client.showcase.Mobdex;
import dev.oriel.mod.net.ClosePayload;
import dev.oriel.mod.net.DataPayload;
import dev.oriel.mod.net.PatchPayload;
import dev.oriel.mod.net.OpenPayload;
import dev.oriel.mod.server.OrielDemos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Client entry points. Only loader client entrypoints (Fabric's {@code ClientModInitializer}, NeoForge's
 * {@code dist = CLIENT} mod class) may reference this class; handlers run on the client thread.
 */
public final class OrielClient {
    public static final Identifier DEMO_HUD = Constants.id("demo_hud");
    public static final Identifier DEMO_TOAST = Constants.id("demo_toast");
    /** The potion HUD preset ({@code /oriel demo hud}). */
    public static final Identifier POTION_HUD = Constants.id("potion_hud");

    private static Consumer<CustomPacketPayload> serverSender = p ->
            Constants.LOG.warn("Oriel: no client network sender installed; dropped {}", p.type().id());

    private OrielClient() {}

    /** Called by the loader's client entrypoint with its way of sending serverbound payloads. */
    public static void init(Consumer<CustomPacketPayload> sender) {
        serverSender = sender;
        OrielResources.init();
        OrielScreens.registerContainer(OrielDemos.CHEST, OrielDemos.CHEST_PAGE);
        OrielHud.register(DEMO_HUD, "oriel:oriel/demo/hud.html");
        OrielHud.register(DEMO_TOAST, "oriel:oriel/demo/toast.html", OrielHud.Input.WHEN_CHAT_OPEN);
        OrielScreens.onPageLoad(Mobdex.URL, Mobdex::load);
        OrielKit.register();
        OrielHud.register(POTION_HUD, OrielKit.POTIONS, OrielHud.Input.WHEN_CHAT_OPEN);
        // The gallery's HUD card: show the potion HUD over the game, closing the gallery so it can be seen.
        OrielScreens.onPageLoad(OrielKit.GALLERY, driver -> driver.onMessage(OrielKit.CHANNEL, value -> {
            if (value.isJsonPrimitive() && value.getAsString().equals("hud")) {
                // After the click that sent it has been handled: the page must not close under its own event.
                Minecraft.getInstance().schedule(() -> {
                    McClient.setScreen(null);
                    if (!OrielHud.isShown(POTION_HUD)) OrielHud.show(POTION_HUD);
                });
            }
        }));
    }

    public static void sendToServer(CustomPacketPayload payload) {
        serverSender.accept(payload);
    }

    /** Handles every clientbound payload in {@code OrielNetwork.CLIENTBOUND}; {@link ServerPages} decides what servers may show. */
    public static void handle(CustomPacketPayload payload) {
        switch (payload) {
            case OpenPayload p -> ServerPages.open(p);
            case DataPayload p -> ServerPages.data(p);
            case PatchPayload p -> ServerPages.patch(p);
            case ClosePayload p -> ServerPages.close(p);
            default -> Constants.LOG.warn("Oriel: unhandled payload {}", payload.type().id());
        }
    }

    /**
     * Resources reloaded (F3+T, resource packs): fonts, images and shaders may have changed, so reload every open page
     * and try the shape shader again.
     */
    public static void onResourceReload() {
        McFontMetrics.INSTANCE.clearCache();
        McImages.clearCaches();
        FolderTextures.clear();
        OrielPages.checkPacks();
        McGui.reloadShapes();
        DocumentDriver.reloadAll();
    }

    /** Called by the loaders at the end of every client tick. */
    public static void tick() {
        ServerPages.tick();
        Mobdex.tick();
    }
}
