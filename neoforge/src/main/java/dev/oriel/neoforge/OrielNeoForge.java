package dev.oriel.neoforge;

import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.OrielCommon;
import dev.oriel.mod.internal.net.OrielNetwork;
import dev.oriel.mod.internal.registry.Entry;
import dev.oriel.mod.internal.registry.OrielRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Constants.MOD_ID)
public final class OrielNeoForge {

    public OrielNeoForge(IEventBus modBus, ModContainer container) {
        OrielCommon.init();
        modBus.addListener(OrielNeoForge::register);
        modBus.addListener(OrielNeoForge::registerPayloads);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((RegisterCommandsEvent e) -> OrielCommon.registerCommands(e.getDispatcher()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) OrielNetwork.requireOnClient(player);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) OrielCommon.onPlayerLeft(player);
        });
        bus.addListener((ServerStoppingEvent e) -> OrielCommon.onServerStopping());
    }

    /** Registers every common entry whose registry matches this event. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(RegisterEvent event) {
        for (Entry<?> entry : OrielRegistry.entries()) {
            if (!entry.registry().equals(event.getRegistryKey())) continue;
            Entry raw = entry;
            event.register((ResourceKey<? extends Registry<Object>>) (ResourceKey) entry.registry(), entry.id(), raw::create);
        }
    }

    /** Clientbound payloads; their client handlers are added by {@code OrielNeoForgeClient}. */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Clients can join servers without Oriel. The server separately requires matching client capabilities at join.
        // Stable transport identity: the capability gate supplies bilingual rejection before UI packets.
        PayloadRegistrar registrar = event.registrar("oriel-capabilities-v1").optional();
        if (Boolean.getBoolean("oriel.probeMissing") && !System.getProperty("oriel.probeRole", "").isEmpty()) return;
        for (OrielNetwork.Clientbound<?> c : OrielNetwork.CLIENTBOUND) clientbound(registrar, c);
        for (OrielNetwork.Serverbound<?> s : OrielNetwork.SERVERBOUND) serverbound(registrar, s);
    }

    private static <T extends CustomPacketPayload> void clientbound(PayloadRegistrar registrar, OrielNetwork.Clientbound<T> c) {
        registrar.playToClient(c.type(), c.codec());
    }

    /** Serverbound handlers run on the server thread (the registrar's default HandlerThread.MAIN). */
    private static <T extends CustomPacketPayload> void serverbound(PayloadRegistrar registrar, OrielNetwork.Serverbound<T> s) {
        registrar.playToServer(s.type(), s.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) s.handler().accept(player, payload);
        });
    }
}
