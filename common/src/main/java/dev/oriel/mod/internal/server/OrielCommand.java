package dev.oriel.mod.internal.server;

import dev.oriel.mod.internal.CommandNames;
import dev.oriel.mod.server.ForgeMenu;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Server commands: {@code /oriel demo chest | live}, {@code /oriel edit} (the item data editor, ItemEditor). The client registers the rest of {@code /oriel} (open, demo
 * pages, reload) and forwards these two here ({@code OrielClientCommands.SERVER_DEMOS}).
 *
 * <p>The server also lists the client-only words as stubs. The client's own {@code oriel} node is dropped from the tree
 * the chat box highlights and completes with when the server sent one with the same name, so without the stubs
 * {@code /oriel open ...} runs but shows red ("Incorrect argument for command"). The client runs these itself, before
 * the server would see them.
 */
public final class OrielCommand {
    private OrielCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("oriel-forge").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> ForgeMenu.open(c.getSource().getPlayerOrException()) ? 1 : 0));
        // "demo" itself is open to everyone (the client's presets hang under it); only the server's two demos need permission.
        LiteralArgumentBuilder<CommandSourceStack> demo = clientTree("demo", CommandNames.PRESETS);
        demo.then(Commands.literal("chest").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
            OrielDemos.openChest(c.getSource().getPlayerOrException());
            return 1;
        }));
        demo.then(Commands.literal("live").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
            OrielDemos.openLive(c.getSource().getPlayerOrException());
            return 1;
        }));
        d.register(Commands.literal("oriel")
                .then(Commands.literal("version").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Oriel UI runtime version: "
                            + dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion("oriel")), false);
                    return 1;
                }))
                .then(demo)
                .then(Commands.literal("edit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> ItemEditor.open(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("open").then(Commands.argument("url", StringArgumentType.greedyString()).executes(OrielCommand::clientOnly)))
                .then(legacy())
                .then(clientTree("showcase", CommandNames.SHOWCASE))
                .then(clientTree("reload", List.of()))
                .then(clientTree("canvastest", List.of())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> legacy() {
        LiteralArgumentBuilder<CommandSourceStack> node = clientTree("legacy", CommandNames.DEMOS);
        for (String name : CommandNames.SERVER_DEMOS) node.then(Commands.literal(name).executes(OrielCommand::clientOnly));
        return node.then(Commands.literal("toast").executes(OrielCommand::clientOnly));
    }

    /** A client-only literal with a literal child for each word the client runs under it. */
    private static LiteralArgumentBuilder<CommandSourceStack> clientTree(String name, List<String> children) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name).executes(OrielCommand::clientOnly);
        for (String child : children) node.then(Commands.literal(child).executes(OrielCommand::clientOnly));
        return node;
    }

    /** Reached only when the sender has no Oriel on their client. */
    private static int clientOnly(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.literal("This /oriel command runs on the client; install Oriel UI on your client to use it."));
        return 0;
    }
}
