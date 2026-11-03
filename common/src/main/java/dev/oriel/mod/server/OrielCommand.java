package dev.oriel.mod.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Server commands: {@code /oriel demo chest | live}. The client registers the rest of {@code /oriel} (open, demo
 * pages, reload) and forwards these two here ({@code OrielClientCommands.SERVER_DEMOS}).
 */
public final class OrielCommand {
    private OrielCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("oriel")
                //? if >=26 {
                .then(Commands.literal("demo").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                //?} else
                //.then(Commands.literal("demo").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("chest").executes(c -> {
                            OrielDemos.openChest(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("live").executes(c -> {
                            OrielDemos.openLive(c.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }
}
