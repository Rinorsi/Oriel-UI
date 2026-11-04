package dev.oriel.fabric.client;

import dev.oriel.mod.Constants;
import dev.oriel.mod.client.DevAutopilot;
import dev.oriel.mod.client.DevFlags;
import dev.oriel.mod.client.DevPointer;
import dev.oriel.mod.client.TourCursor;
import dev.oriel.mod.client.OrielClient;
import dev.oriel.mod.client.OrielClientCommands;
import dev.oriel.mod.client.OrielHud;
import dev.oriel.mod.client.OrielScreens;
import dev.oriel.mod.net.OrielNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import dev.oriel.mod.client.render.GuiSceneRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Fabric client entrypoint (fabric.mod.json "client"): registration only; the logic lives in common. */
public final class OrielFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OrielClient.init(ClientPlayNetworking::send);
        for (OrielNetwork.Clientbound<?> c : OrielNetwork.CLIENTBOUND) register(c);
        //? if >=26.2 {
        PictureInPictureRendererRegistry.register(context -> new GuiSceneRenderer());
        //?} else
        //PictureInPictureRendererRegistry.register(context -> new GuiSceneRenderer(context.bufferSource()));
        HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE, Constants.id("hud"), OrielHud::extract);
        // Interactive HUD overlays: drawn above every screen and given its pointer input first (per-screen events).
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, a) -> OrielHud.extractAboveScreen(g, a));
            // The dev tour's cursor, over everything: after the screen's tooltips and the overlays above it.
            if (DevFlags.TOUR) ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, a) -> TourCursor.extractAboveScreen(g));
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !OrielHud.mouseClicked(e));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !OrielHud.mouseReleased(e));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> !OrielHud.mouseDragged(e));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, dx, dy) -> !OrielHud.mouseScrolled(x, y, dx, dy));
        });
        // After every mod's client entrypoint has had the chance to call OrielScreens.registerContainer.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            for (OrielScreens.ContainerBinding<?> b : OrielScreens.takeContainers()) registerMenuScreen(b);
        });
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Constants.id("documents"),
                (ResourceManagerReloadListener) resources -> OrielClient.onResourceReload());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(OrielClientCommands.create()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> OrielClient.tick());
        if (DevFlags.AUTOPILOT) {
            ClientTickEvents.END_CLIENT_TICK.register(DevAutopilot::tick);
            ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
                DevPointer.beforeScreenChange();
                ScreenEvents.remove(screen).register(s -> DevPointer.beforeScreenChange());
            });
        }
        if (DevFlags.TOUR) HudElementRegistry.addLast(Constants.id("tour_cursor"), TourCursor::extractHud);
    }

    private static <T extends CustomPacketPayload> void register(OrielNetwork.Clientbound<T> c) {
        ClientPlayNetworking.registerGlobalReceiver(c.type(), (payload, context) -> context.client().execute(() -> OrielClient.handle(payload)));
    }

    private static <M extends AbstractContainerMenu> void registerMenuScreen(OrielScreens.ContainerBinding<M> b) {
        MenuScreens.register(b.type().get(), b::create);
    }
}
