package dev.oriel.mod.client;

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
 * Client commands: {@code /oriel open <url> | demo [preset] | legacy [name] | showcase [page] | reload | canvastest}. Generic in the command source so
 * each loader registers the same tree with its own client source type.
 */
public final class OrielClientCommands {
    /** The client-side demo pages, under {@code assets/oriel/oriel/demo/} (the gallery is {@code index.html}). */
    public static final List<String> DEMOS = List.of("gallery", "settings", "layout", "animation", "templates", "hud");
    /**
     * Demos run by the server ({@code OrielCommand}). The client forwards them itself: Fabric's client dispatcher
     * claims every {@code /oriel ...} command once a client {@code oriel} node exists.
     */
    public static final List<String> SERVER_DEMOS = List.of("chest", "live");
    /** The show-off pages under {@code assets/oriel/oriel/showcase/}. */
    public static final List<String> SHOWCASE = List.of("title", "hud", "shop", "mobdex", "journal", "console", "models",
            "scene");

    private OrielClientCommands() {}

    public static <S> LiteralArgumentBuilder<S> create() {
        // The Oriel UI presets: /oriel demo opens their gallery, /oriel demo hud toggles the potion HUD.
        LiteralArgumentBuilder<S> presets = LiteralArgumentBuilder.<S>literal("demo").executes(c -> later(() -> preset("gallery")));
        for (String name : PRESETS) presets.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> later(() -> preset(name))));
        // The demos inherited from Vellum.
        LiteralArgumentBuilder<S> demo = LiteralArgumentBuilder.<S>literal("legacy").executes(c -> later(() -> demo("gallery")));
        for (String name : DEMOS) demo.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> later(() -> demo(name))));
        demo.then(LiteralArgumentBuilder.<S>literal("toast").executes(c -> later(OrielClientCommands::toast)));
        for (String name : SERVER_DEMOS) demo.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> askServer("oriel demo " + name)));
        LiteralArgumentBuilder<S> showcase = LiteralArgumentBuilder.<S>literal("showcase")
                .executes(c -> later(() -> OrielScreens.open(showcaseUrl("index"))));
        for (String name : SHOWCASE) showcase.then(LiteralArgumentBuilder.<S>literal(name).executes(c -> later(() -> OrielScreens.open(showcaseUrl(name)))));
        return LiteralArgumentBuilder.<S>literal("oriel")
                .then(LiteralArgumentBuilder.<S>literal("open")
                        .then(RequiredArgumentBuilder.<S, String>argument("url", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String url = StringArgumentType.getString(c, "url");
                                    return later(() -> OrielScreens.open(url));
                                })))
                .then(LiteralArgumentBuilder.<S>literal("edit").executes(c -> askServer("oriel edit")))
                .then(presets)
                .then(demo)
                .then(showcase)
                .then(LiteralArgumentBuilder.<S>literal("reload").executes(c -> later(OrielClient::onResourceReload)))
                .then(LiteralArgumentBuilder.<S>literal("canvastest").executes(c -> later(() -> McClient.setScreen(new CanvasTestScreen()))));
    }

    /** The presets {@code /oriel demo} opens: a page each, and the potion HUD. */
    public static final List<String> PRESETS = List.of("inventory", "forge", "music", "settings", "hud", "editor", "catalog");

    /** Opens a preset page, or the gallery; {@code hud} toggles the potion HUD overlay. */
    public static void preset(String name) {
        if (name.equals("hud")) {
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
        return "oriel:oriel/demo/" + (name.equals("gallery") ? "index" : name) + ".html";
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
        //? if >=26 {
        Minecraft.getInstance().schedule(action);
        //?} else
        //Minecraft.getInstance().tell(action);
        return 1;
    }
}
