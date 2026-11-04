package dev.oriel.mod;

import com.mojang.brigadier.CommandDispatcher;
import dev.oriel.mod.gametest.OrielGameTests;
import dev.oriel.mod.server.OrielCommand;
import dev.oriel.mod.server.OrielDemos;
import dev.oriel.mod.server.OrielServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-independent entry points. Each loader calls these from its own events: {@link #init()} at mod construction
 * (before registration), then the server hooks.
 */
public final class OrielCommon {
    private OrielCommon() {}

    /** Reads the settings and declares every registry entry. Call before the loader registers {@code OrielRegistry.entries()}. */
    public static void init() {
        OrielConfig.load();
        OrielDemos.init();
        dev.oriel.mod.server.ForgeMenu.init();
        OrielGameTests.init();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        OrielCommand.register(dispatcher);
        dev.oriel.mod.server.ServerProbe.register(dispatcher);
    }

    public static void onPlayerLeft(ServerPlayer player) {
        OrielServer.onPlayerLeft(player);
    }

    public static void onServerStopping() {
        OrielServer.onServerStopping();
    }
}
