package dev.oriel.neoforge.client;

import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.client.DevAutopilot;
import dev.oriel.mod.internal.client.DevFlags;
import dev.oriel.mod.internal.client.DevPointer;
import dev.oriel.mod.internal.client.McClient;
import dev.oriel.mod.internal.client.TourCursor;
import dev.oriel.mod.internal.client.OrielClient;
import dev.oriel.mod.internal.client.OrielClientCommands;
import dev.oriel.mod.internal.client.InventoryEffects;
import dev.oriel.mod.client.OrielHud;
import dev.oriel.mod.client.OrielScreens;
import dev.oriel.mod.internal.client.replaced.ItemTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import dev.oriel.mod.internal.client.render.GuiSceneRenderState;
import dev.oriel.mod.internal.client.render.GuiSceneRenderer;
import dev.oriel.mod.internal.client.render.LayerRenderState;
import dev.oriel.mod.internal.client.render.LayerRenderer;
import dev.oriel.mod.internal.client.render.Layers;
import net.minecraft.client.gui.render.GuiRenderer;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import java.util.List;
import dev.oriel.mod.internal.net.OrielNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only entry point: FML constructs this class on the physical client only. */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class OrielNeoForgeClient {
    public OrielNeoForgeClient(IEventBus modBus, ModContainer container) {
        OrielClient.init(payload -> ClientPacketDistributor.sendToServer(payload));
        // <item tooltip>: pass the stack on to NeoForge's tooltip events, as vanilla's item tooltips do.
        //? if >=26.3 {
        ItemTooltips.install((g, font, lines, stack, x, y) -> g.setTooltipForNextFrame(font, lines, stack.getTooltipImage(),
                stack, x, y, stack.get(DataComponents.TOOLTIP_STYLE), true));
        //?} else {
        /*ItemTooltips.install((g, font, lines, stack, x, y) -> g.setTooltipForNextFrame(font, lines, stack.getTooltipImage(),
                stack, x, y, stack.get(DataComponents.TOOLTIP_STYLE)));
        *///?}
        modBus.addListener(OrielNeoForgeClient::registerPayloadHandlers);
        modBus.addListener((RegisterPictureInPictureRenderersEvent e) -> {
            e.register(GuiSceneRenderState.class, GuiSceneRenderer::new);
            e.register(LayerRenderState.class, LayerRenderer::new);
        });
        // The GUI renderers of McCanvas's layers draw scenes and layers inside layers.
        //? if >=26.2 {
        Layers.renderers(state -> new GuiRenderer(state, Minecraft.getInstance().gameRenderer.featureRenderDispatcher(), List.of(
                new PictureInPictureRendererRegistration<>(GuiSceneRenderState.class, GuiSceneRenderer::new),
                new PictureInPictureRendererRegistration<>(LayerRenderState.class, LayerRenderer::new))));
        //?} else {
        /*Layers.renderers(state -> {
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            var features = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
            return new GuiRenderer(state, buffers, features.getSubmitNodeStorage(), features, List.of(
                    new PictureInPictureRendererRegistration<>(GuiSceneRenderState.class, GuiSceneRenderer::new),
                    new PictureInPictureRendererRegistration<>(LayerRenderState.class, LayerRenderer::new)));
        });
        *///?}
        modBus.addListener((AddClientReloadListenersEvent e) ->
                e.addListener(Constants.id("documents"), (ResourceManagerReloadListener) resources -> OrielClient.onResourceReload()));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.TITLE, Constants.id("hud"), OrielHud::extract));
        // The potion HUD shows the effects instead of vanilla's icons at the top right.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) -> {
            if (e.getName().equals(VanillaGuiLayers.EFFECTS) && OrielClient.hidesVanillaEffects()) e.setCanceled(true);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> e.register(dev.oriel.mod.internal.client.OrielKeys.DEMOS));
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            for (OrielScreens.ContainerBinding<?> b : OrielScreens.takeContainers()) registerMenuScreen(e, b);
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(OrielClientCommands.create()));
        // The effects panel beside vanilla's inventories, in place of vanilla's list.
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre e) -> InventoryEffects.prepare(e.getScreen()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.RenderInventoryMobEffects e) -> {
            if (InventoryEffects.hideVanilla(e.getScreen())) e.setCanceled(true);
        });
        // Interactive HUD overlays: drawn above the screen (not its background layers) and given its pointer input first.
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) -> {
            if (e.getScreen() == McClient.screen()) OrielHud.extractAboveScreen(e.getGuiGraphics(), e.getPartialTick());
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (OrielHud.mouseClicked(e.getMouseButtonEvent())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (OrielHud.mouseReleased(e.getMouseButtonEvent())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseDragged.Pre e) -> {
            if (OrielHud.mouseDragged(e.getMouseButtonEvent())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            if (OrielHud.mouseScrolled(e.getMouseX(), e.getMouseY(), e.getScrollDeltaX(), e.getScrollDeltaY())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> OrielClient.tick());
        if (DevFlags.AUTOPILOT) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> DevAutopilot.tick(Minecraft.getInstance()));
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Pre e) -> DevPointer.beforeScreenChange());
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing e) -> DevPointer.beforeScreenChange());
        }
        // The dev tour's cursor, over everything: after the screen's tooltips and the overlays above it, else last in the HUD.
        if (DevFlags.TOUR) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post e) -> {
                if (e.getScreen() == McClient.screen()) TourCursor.extractAboveScreen(e.getGuiGraphics());
            });
            modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(Constants.id("tour_cursor"), TourCursor::extractHud));
        }
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        if (Boolean.getBoolean("oriel.probeMissing") && !System.getProperty("oriel.probeRole", "").isEmpty()) return;
        // Runs on the client main thread (HandlerThread.MAIN is the default for this overload).
        for (OrielNetwork.Clientbound<?> c : OrielNetwork.CLIENTBOUND) register(event, c);
    }

    private static <T extends CustomPacketPayload> void register(RegisterClientPayloadHandlersEvent event, OrielNetwork.Clientbound<T> c) {
        event.register(c.type(), (payload, context) -> OrielClient.handle(payload));
    }

    private static <M extends AbstractContainerMenu> void registerMenuScreen(RegisterMenuScreensEvent event, OrielScreens.ContainerBinding<M> b) {
        event.register(b.type().get(), b::create);
    }
}
