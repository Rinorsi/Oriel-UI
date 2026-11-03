package dev.oriel.fabric;

import dev.oriel.mod.OrielCommon;
import dev.oriel.mod.net.OrielNetwork;
import dev.oriel.mod.registry.Entry;
import dev.oriel.mod.registry.OrielRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if <26 {
/*import dev.oriel.mod.gametest.GameTests;
import net.minecraft.gametest.framework.GameTestRegistry;
*///?}

public final class OrielFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        OrielCommon.init();
        registerAll();
        for (OrielNetwork.Clientbound<?> c : OrielNetwork.CLIENTBOUND) clientbound(c);
        for (OrielNetwork.Serverbound<?> s : OrielNetwork.SERVERBOUND) serverbound(s);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> OrielCommon.registerCommands(dispatcher));
        //? if <26 {
        /*// 1.21.1's GameTests come from annotated classes: Oriel's are made by a generator (on 26.x they are registry
        // entries, registered above).
        if (System.getProperty("fabric-api.gametest") != null) GameTestRegistry.register(GameTests.class);
        *///?}
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OrielCommon.onPlayerLeft(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> OrielCommon.onServerStopping());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerAll() {
        for (Entry<?> entry : OrielRegistry.entries()) {
            //? if >=26 {
            Registry registry = BuiltInRegistries.REGISTRY.getValue(entry.registry().identifier());
            //?} else
            //Registry registry = BuiltInRegistries.REGISTRY.get(entry.registry().location());
            if (registry == null) throw new IllegalStateException("Unknown registry " + entry.registry());
            Registry.register(registry, entry.id(), entry.create());
        }
    }

    private static <T extends CustomPacketPayload> void clientbound(OrielNetwork.Clientbound<T> c) {
        //? if >=26 {
        PayloadTypeRegistry.clientboundPlay().register(c.type(), c.codec());
        //?} else
        //PayloadTypeRegistry.playS2C().register(c.type(), c.codec());
    }

    private static <T extends CustomPacketPayload> void serverbound(OrielNetwork.Serverbound<T> s) {
        //? if >=26 {
        PayloadTypeRegistry.serverboundPlay().register(s.type(), s.codec());
        //?} else
        //PayloadTypeRegistry.playC2S().register(s.type(), s.codec());
        ServerPlayNetworking.registerGlobalReceiver(s.type(),
                (payload, context) -> context.server().execute(() -> s.handler().accept(context.player(), payload)));
    }
}
