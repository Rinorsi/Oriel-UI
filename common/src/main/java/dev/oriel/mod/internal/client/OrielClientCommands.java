package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.OrielHud;
import dev.oriel.mod.internal.CommandNames;
import dev.oriel.mod.client.OrielScreen;
import dev.oriel.mod.client.OrielScreens;

import com.google.gson.JsonObject;
import dev.oriel.kit.OrielKit;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.util.List;

/**
 * Client commands: {@code /oriel open <url> | showcase [page] | reload | canvastest}. Generic in the command source so
 * each loader registers the same tree with its own client source type.
 */
public final class OrielClientCommands {
    /** The client-side demo pages, under {@code assets/oriel/oriel/demo/} (the gallery is {@code index.html}). */
    public static final List<String> DEMOS = CommandNames.DEMOS;
    /**
     * Demos run by the server ({@code OrielCommand}). The client forwards them itself: Fabric's client dispatcher
     * claims every {@code /oriel ...} command once a client {@code oriel} node exists.
     */
    public static final List<String> SERVER_DEMOS = CommandNames.SERVER_DEMOS;
    /** The show-off pages under {@code assets/oriel/oriel/showcase/}. */
    public static final List<String> SHOWCASE = CommandNames.SHOWCASE;

    private OrielClientCommands() {}

    public static <S> LiteralArgumentBuilder<S> create() {
        // One demo entry: /oriel showcase opens the gallery home; its words are the kit presets, the show-off pages, the
        // toast overlay, and the server's two demos (the server runs those: past this dispatcher, which would take them again).
        LiteralArgumentBuilder<S> showcase = LiteralArgumentBuilder.<S>literal("showcase").executes(c -> later(() -> preset("gallery")));
        for (String name : PRESETS) showcase.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> later(() -> preset(name))));
        for (String name : SHOWCASE) showcase.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> later(() -> OrielScreens.open(showcaseUrl(name)))));
        for (String name : CommandNames.BASICS) showcase.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> later(() -> demo(basicsPage(name)))));
        showcase.then(LiteralArgumentBuilder.<S>literal(CommandNames.TOAST).executes(c -> later(OrielClientCommands::toast)));
        for (String name : SERVER_DEMOS) showcase.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> askServer("oriel showcase " + name)));
        return LiteralArgumentBuilder.<S>literal("oriel")
                .then(LiteralArgumentBuilder.<S>literal("version").executes(c -> later(() -> McClient.systemMessage(
                        Component.literal("Oriel UI runtime version: " + dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion("oriel"))))))
                .then(LiteralArgumentBuilder.<S>literal("open")
                        .then(RequiredArgumentBuilder.<S, String>argument("url", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String url = StringArgumentType.getString(c, "url");
                                    return later(() -> OrielScreens.open(url));
                                })))
                .then(showcase)
                .then(LiteralArgumentBuilder.<S>literal("reload").executes(c -> later(OrielClient::onResourceReload)))
                .then(LiteralArgumentBuilder.<S>literal("canvastest").executes(c -> later(() -> McClient.setScreen(new CanvasTestScreen()))));
    }

    /** The kit presets under {@code /oriel showcase}. */
    public static final List<String> PRESETS = CommandNames.PRESETS;

    /** Opens a preset page, or the gallery; {@code hud} toggles the potion HUD overlay. */
    public static void preset(String name) {
        if (name.equals("potions")) {
            togglePotionHud();
            return;
        }
        OrielScreens.open(OrielKit.page(name));
    }

    /** Shows or hides the potion HUD preset, which takes the pointer while chat is open. */
    public static void togglePotionHud() {
        if (OrielHud.isShown(OrielClient.POTION_HUD)) OrielHud.hide(OrielClient.POTION_HUD);
        else OrielHud.show(OrielClient.POTION_HUD);
    }

    /** Opens a demo page; {@code hud} toggles the demo HUD overlay instead. */
    public static void demo(String name) {
        if (name.equals("hud")) {
            if (OrielHud.isShown(OrielClient.DEMO_HUD)) OrielHud.hide(OrielClient.DEMO_HUD);
            else OrielHud.show(OrielClient.DEMO_HUD);
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("source", "client");
        data.addProperty("player", Minecraft.getInstance().getUser().getName());
        OrielScreens.open(demoUrl(name), data);
    }

    /**
     * Toggles the interactive toast overlay (a HUD overlay with buttons: press T and click them). The answer comes
     * back as a message and is echoed in chat.
     */
    public static void toast() {
        if (OrielHud.isShown(OrielClient.DEMO_TOAST)) {
            OrielHud.hide(OrielClient.DEMO_TOAST);
            return;
        }
        OrielHud.show(OrielClient.DEMO_TOAST)
                .onMessage("answer", value -> McClient.systemMessage(Component.literal("Rivet's request: " + value.getAsString())));
    }

    public static String demoUrl(String name) {
        return "oriel:oriel/demo/" + name + ".html";
    }

    /** The page a {@link CommandNames#BASICS} word opens. */
    private static String basicsPage(String word) {
        return switch (word) {
            case "forms" -> "settings";
            case "titlecard" -> "hud";
            default -> word;
        };
    }

    public static String showcaseUrl(String name) {
        return "oriel:oriel/showcase/" + name + ".html";
    }

    /** Sends a command straight to the server, past the client dispatcher (which would take it again). */
    private static int askServer(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(new ServerboundChatCommandPacket(command));
        return 1;
    }

    /** Runs after the chat screen that sent the command has closed (it would close a screen opened right away). */
    private static int later(Runnable action) {
        Minecraft.getInstance().schedule(action);
        return 1;
    }
}
