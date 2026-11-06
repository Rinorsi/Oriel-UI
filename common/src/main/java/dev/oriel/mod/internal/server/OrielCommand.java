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
 * Server commands: {@code /oriel showcase chest | live}. The client registers the rest of {@code /oriel} (open, showcase
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
        // "showcase" itself is open to everyone (the client's pages hang under it); only the server's two demos need permission.
        LiteralArgumentBuilder<CommandSourceStack> showcase = clientTree("showcase", clientWords());
        showcase.then(Commands.literal("chest").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
            OrielDemos.openChest(c.getSource().getPlayerOrException());
            return 1;
        }));
        showcase.then(Commands.literal("live").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
            OrielDemos.openLive(c.getSource().getPlayerOrException());
            return 1;
        }));
        d.register(Commands.literal("oriel")
                .then(Commands.literal("version").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Oriel UI runtime version: "
                            + dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion("oriel")), false);
                    return 1;
                }))
                .then(showcase)
                .then(Commands.literal("open").then(Commands.argument("url", StringArgumentType.greedyString()).executes(OrielCommand::clientOnly)))
                .then(clientTree("reload", List.of()))
                .then(clientTree("canvastest", List.of())));
    }

    private static List<String> clientWords() {
        List<String> words = new java.util.ArrayList<>(CommandNames.PRESETS);
        words.addAll(CommandNames.SHOWCASE);
        words.addAll(CommandNames.BASICS);
        words.add(CommandNames.TOAST);
        return words;
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
