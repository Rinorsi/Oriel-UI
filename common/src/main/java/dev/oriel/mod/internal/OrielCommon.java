package dev.oriel.mod.internal;


import com.mojang.brigadier.CommandDispatcher;
import dev.oriel.mod.internal.gametest.OrielGameTests;
import dev.oriel.mod.internal.server.OrielCommand;
import dev.oriel.mod.internal.server.OrielDemos;
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
        Constants.LOG.info("Oriel UI runtime version: {}", dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion(Constants.MOD_ID));
        if (Boolean.getBoolean("oriel.jijProbe")) Constants.LOG.info("ORIEL_JIJ_PROBE parents={},{}",
                dev.oriel.mod.internal.platform.Services.PLATFORM.isModLoaded("oriel_bundle_1"),
                dev.oriel.mod.internal.platform.Services.PLATFORM.isModLoaded("oriel_bundle_2"));
        Constants.LOG.info("Oriel UI distribution: {} version={} {}",
                dev.oriel.kit.ReferenceContent.available() ? "full" : "slim",
                dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion(Constants.MOD_ID),
                dev.oriel.kit.ReferenceContent.available() ? "reference content included" : "参考内容未包含（官方模板/showcase/demo）");
        OrielConfig.load();
        if (dev.oriel.kit.ReferenceContent.available()) OrielDemos.init();
        if (dev.oriel.kit.ReferenceContent.available()) dev.oriel.mod.internal.server.ForgeMenu.init();
        if (dev.oriel.kit.ReferenceContent.available()) OrielGameTests.init();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        OrielCommand.register(dispatcher);
        if (dev.oriel.kit.ReferenceContent.available()) dev.oriel.mod.internal.server.ServerProbe.register(dispatcher);
    }

    public static void onPlayerLeft(ServerPlayer player) {
        OrielServer.onPlayerLeft(player);
    }

    public static void onServerStopping() {
        OrielServer.onServerStopping();
    }
}
