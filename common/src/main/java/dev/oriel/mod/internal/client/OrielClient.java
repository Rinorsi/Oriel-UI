package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;
import dev.oriel.mod.client.OrielHud;
import dev.oriel.mod.client.OrielScreen;
import dev.oriel.mod.client.OrielScreens;

import dev.oriel.kit.GameRegistry;
import dev.oriel.kit.OrielKit;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.client.render.FolderTextures;
import dev.oriel.mod.internal.client.render.McFontMetrics;
import dev.oriel.mod.internal.client.render.McGui;
import dev.oriel.mod.internal.client.render.McImages;
import dev.oriel.mod.internal.client.showcase.Mobdex;
import dev.oriel.mod.internal.net.ClosePayload;
import dev.oriel.mod.internal.net.DataPayload;
import dev.oriel.mod.internal.net.PatchPayload;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.server.OrielDemos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client entry points. Only loader client entrypoints (Fabric's {@code ClientModInitializer}, NeoForge's
 * {@code dist = CLIENT} mod class) may reference this class; handlers run on the client thread.
 */
public final class OrielClient {
    public static final Identifier DEMO_HUD = Constants.id("demo_hud");
    public static final Identifier DEMO_TOAST = Constants.id("demo_toast");

    private static ClientSender<CustomPacketPayload> serverSender = new ClientSender<>(
            () -> false, p -> false, p -> {}, reason -> Constants.LOG.warn("Oriel: upstream unavailable: {}", reason));

    private OrielClient() {}

    /** Called by the loader's client entrypoint with its way of sending serverbound payloads. */
    public static void init(java.util.function.Predicate<CustomPacketPayload> canSend, Consumer<CustomPacketPayload> sender) {
        serverSender = new ClientSender<>(() -> Minecraft.getInstance().getConnection() != null
                && Minecraft.getInstance().getConnection().getConnection().isConnected(), canSend, sender,
                reason -> Constants.LOG.warn("Oriel: upstream unavailable: {}", reason));
        OrielResources.init();
        if (dev.oriel.kit.ReferenceContent.available()) {
            OrielScreens.registerContainer(OrielDemos.CHEST, OrielDemos.CHEST_PAGE);
            OrielScreens.registerContainer(dev.oriel.mod.internal.server.ForgeMenu.TYPE, OrielKit.FORGE_STATION, dev.oriel.mod.internal.server.ForgeMenu::pageData);
            OrielHud.register(DEMO_HUD, "oriel:oriel/demo/hud.html");
            OrielHud.register(DEMO_TOAST, "oriel:oriel/demo/toast.html", OrielHud.Input.WHEN_CHAT_OPEN);
            OrielScreens.onPageLoad(Mobdex.URL, Mobdex::load);
        }
        OrielKit.register();
        if (DevFlags.RELOAD_AVAILABLE) DevReload.init();
        // Pickers list every registered item (vanilla and modded) by the name the game shows in its language.
        GameRegistry.provide(() -> {
            List<GameRegistry.Entry> all = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String name = new ItemStack(item).getHoverName().getString();
                all.add(new GameRegistry.Entry(BuiltInRegistries.ITEM.getKey(item).toString(), name, item instanceof BlockItem));
            }
            return all;
        });
        // And every entity type that can be summoned, for the editor's 3D entity.
        GameRegistry.provideEntities(() -> {
            List<GameRegistry.Entry> all = new ArrayList<>();
            for (net.minecraft.world.entity.EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!type.canSummon() || key.toString().equals("minecraft:player")) continue;
                all.add(new GameRegistry.Entry(key.toString(), type.getDescription().getString(), false));
            }
            return all;
        });
        // The template centre's favourites, recently viewed and saved copies (config/oriel/).
        MyPages.init();
        if (dev.oriel.kit.ReferenceContent.available()) TemplateLibrary.init();
        ModSettings.init();
    }

    public static boolean sendToServer(CustomPacketPayload payload) {
        return serverSender.send(payload);
    }

    /** Handles every clientbound payload in {@code OrielNetwork.CLIENTBOUND}; {@link ServerPages} decides what servers may show. */
    public static void handle(CustomPacketPayload payload) {
        if (dev.oriel.kit.ReferenceContent.available() && ServerProbeClient.ENABLED) ServerProbeClient.received(payload);
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
        NestedNoticeClient.tick();
        OrielKeys.tick();
        if (dev.oriel.kit.ReferenceContent.available() && ServerProbeClient.ENABLED) ServerProbeClient.tick();
        ServerPages.tick();
        if (dev.oriel.kit.ReferenceContent.available()) Mobdex.tick();
    }
}
