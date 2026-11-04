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

public final class OrielFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        OrielCommon.init();
        registerAll();
        for (OrielNetwork.Clientbound<?> c : OrielNetwork.CLIENTBOUND) clientbound(c);
        for (OrielNetwork.Serverbound<?> s : OrielNetwork.SERVERBOUND) serverbound(s);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> OrielCommon.registerCommands(dispatcher));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OrielCommon.onPlayerLeft(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> OrielCommon.onServerStopping());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerAll() {
        for (Entry<?> entry : OrielRegistry.entries()) {
            Registry registry = BuiltInRegistries.REGISTRY.getValue(entry.registry().identifier());
            if (registry == null) throw new IllegalStateException("Unknown registry " + entry.registry());
            Registry.register(registry, entry.id(), entry.create());
        }
    }

    private static <T extends CustomPacketPayload> void clientbound(OrielNetwork.Clientbound<T> c) {
        PayloadTypeRegistry.clientboundPlay().register(c.type(), c.codec());
    }

    private static <T extends CustomPacketPayload> void serverbound(OrielNetwork.Serverbound<T> s) {
        PayloadTypeRegistry.serverboundPlay().register(s.type(), s.codec());
        ServerPlayNetworking.registerGlobalReceiver(s.type(),
                (payload, context) -> context.server().execute(() -> s.handler().accept(context.player(), payload)));
    }
}
