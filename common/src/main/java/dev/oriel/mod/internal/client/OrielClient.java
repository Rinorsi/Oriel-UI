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
        // The server's repair forge (/oriel-forge): a real container on the kit's forge station page.
        OrielScreens.registerContainer(dev.oriel.mod.server.ForgeMenu.TYPE, OrielKit.FORGE_STATION, dev.oriel.mod.server.ForgeMenu::pageData);
        OrielHud.register(DEMO_HUD, "oriel:oriel/demo/hud.html");
        OrielHud.register(DEMO_TOAST, "oriel:oriel/demo/toast.html", OrielHud.Input.WHEN_CHAT_OPEN);
        OrielScreens.onPageLoad(Mobdex.URL, Mobdex::load);
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
        // The potion HUD shows the player's own effects.
        PlayerEffects.init();
        InventoryEffects.init();
        // The template centre's favourites, recently viewed and saved copies (config/oriel/).
        TemplateLibrary.init();
        OrielHud.register(POTION_HUD, OrielKit.POTIONS, OrielHud.Input.WHEN_CHAT_OPEN);
        // The gallery's HUD card: every lasting effect on the player (to see the HUD and the inventory's panel full),
        // then the potion HUD over the game, closing the gallery so it can be seen.
        OrielScreens.onPageLoad(OrielKit.GALLERY, driver -> driver.onMessage(OrielKit.CHANNEL, value -> {
            if (value.isJsonPrimitive() && value.getAsString().equals("hud")) {
                // After the click that sent it has been handled: the page must not close under its own event.
                Minecraft.getInstance().schedule(() -> {
                    McClient.setScreen(null);
                    giveEveryLastingEffect();
                    if (!OrielHud.isShown(POTION_HUD)) OrielHud.show(POTION_HUD);
                });
            }
        }));
    }

    /**
     * Gives the player every registered effect that lasts (instant ones such as instant health are left out), at
     * varied levels and durations, through {@code /effect}: so it works only where the player may cheat, and says so
     * in chat otherwise, as the command does. The ones that hide the screen or the player are left out too, since the
     * point is to look at the effect lists: blindness, darkness, nausea and invisibility.
     */
    private static final java.util.Set<String> OBSCURING = java.util.Set.of(
            "minecraft:blindness", "minecraft:darkness", "minecraft:nausea", "minecraft:invisibility");

    static void giveEveryLastingEffect() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int i = 0;
        for (net.minecraft.world.effect.MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            //? if >=26.2 {
            if (effect.isInstantaneous()) continue;
            //?} else
            //if (effect.isInstantenous()) continue;
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id == null || OBSCURING.contains(id.toString())) continue;
            int seconds = 40 + (i * 53) % 560, amplifier = i % 3;
            // With their particles: hiding them hides the effect's icon too (vanilla ties showIcon to them).
            player.connection.sendCommand("effect give @s " + id + " " + seconds + " " + amplifier);
            i++;
        }
    }

    /** Whether vanilla's effect icons at the top right are left out: the potion HUD shows the effects instead. */
    public static boolean hidesVanillaEffects() {
        return OrielHud.isShown(POTION_HUD);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        serverSender.accept(payload);
    }

    /** Handles every clientbound payload in {@code OrielNetwork.CLIENTBOUND}; {@link ServerPages} decides what servers may show. */
    public static void handle(CustomPacketPayload payload) {
        if (ServerProbeClient.ENABLED) ServerProbeClient.received(payload);
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
        if (ServerProbeClient.ENABLED) ServerProbeClient.tick();
        ServerPages.tick();
        Mobdex.tick();
        PlayerEffects.tick();
        InventoryEffects.tick();
    }
}
