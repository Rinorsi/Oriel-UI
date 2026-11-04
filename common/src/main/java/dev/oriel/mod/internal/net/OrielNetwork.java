package dev.oriel.mod.internal.net;


import dev.oriel.mod.internal.platform.Network;
import dev.oriel.mod.internal.platform.Services;
import dev.oriel.mod.server.OrielServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/** The payload registry both loaders iterate, and the common way to send clientbound payloads. */
public final class OrielNetwork {
    /** Bump when a payload's wire format changes; both loaders require the matching version capability. */
    public static final String PROTOCOL_VERSION = "2";

    /** Test override is only accepted in the opt-in network probe process. */
    public static String protocolVersion() {
        return System.getProperty("oriel.probeRole", "").isEmpty() ? PROTOCOL_VERSION
                : System.getProperty("oriel.probeProtocol", PROTOCOL_VERSION);
    }

    /** A server-to-client payload. Its client handler is a case in {@code OrielClient.handle}. */
    public record Clientbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                             StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {}

    /** A client-to-server payload and its handler, which runs on the server thread. */
    public record Serverbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                             StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                             BiConsumer<ServerPlayer, T> handler) {}

    public static final List<Clientbound<?>> CLIENTBOUND = List.of(
            new Clientbound<>(ProtocolPayload.TYPE, ProtocolPayload.STREAM_CODEC),
            new Clientbound<>(OpenPayload.TYPE, OpenPayload.STREAM_CODEC),
            new Clientbound<>(DataPayload.TYPE, DataPayload.STREAM_CODEC),
            new Clientbound<>(PatchPayload.TYPE, PatchPayload.STREAM_CODEC),
            new Clientbound<>(ClosePayload.TYPE, ClosePayload.STREAM_CODEC));

    public static final List<Serverbound<?>> SERVERBOUND = List.of(
            new Serverbound<>(MessagePayload.TYPE, MessagePayload.STREAM_CODEC, OrielServer::handleMessage),
            new Serverbound<>(ClosedPayload.TYPE, ClosedPayload.STREAM_CODEC, OrielServer::handleClosed));

    private static volatile @Nullable Network impl;
    private static volatile @Nullable BiConsumer<ServerPlayer, CustomPacketPayload> interceptor;

    private OrielNetwork() {}

    /** A supporting channel alone is insufficient: Fabric must negotiate the exact protocol capability too. */
    public static boolean compatible(java.util.function.Predicate<CustomPacketPayload.Type<?>> available) {
        return available.test(ProtocolPayload.TYPE) && available.test(OpenPayload.TYPE)
                && available.test(DataPayload.TYPE) && available.test(PatchPayload.TYPE) && available.test(ClosePayload.TYPE);
    }

    /** Ported from the main-checkout WIP rule, with an exact protocol check instead of an open-channel-only check. */
    public static boolean requireOnClient(ServerPlayer player) {
        // GameTest's loopback mock players do not negotiate channels. This exemption never applies to a game server.
        if (player.level().getServer() instanceof net.minecraft.gametest.framework.GameTestServer) return true;
        if (compatible(type -> canReceive(player, type))) return true;
        String message = ProtocolMessages.rejection(canReceive(player, OpenPayload.TYPE));
        dev.oriel.mod.internal.Constants.LOG.warn("Oriel: rejecting {}: {}", player.getGameProfile().name(), message);
        player.connection.disconnect(net.minecraft.network.chat.Component.literal(message));
        return false;
    }

    /** Capability query for menus or other operations that need a supporting client before sending vanilla packets. */
    public static boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        Network n = impl;
        if (n == null) impl = n = Services.load(Network.class);
        return n.canReceive(player, type);
    }

    /** Sends a clientbound payload; false if the player's client cannot receive it (it lacks Oriel). */
    public static boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        BiConsumer<ServerPlayer, CustomPacketPayload> sink = interceptor;
        if (sink != null) {
            sink.accept(player, payload);
            return true;
        }
        if (!compatible(type -> canReceive(player, type))) return false;
        Network n = impl;
        if (n == null) impl = n = Services.load(Network.class);
        return n.sendToPlayer(player, payload);
    }

    /**
     * Test hook: routes clientbound payloads to {@code sink} instead of the network, so GameTests can run sessions
     * against mock players (which have no negotiated channels). Pass null to restore the network.
     */
    @VisibleForTesting
    public static void intercept(@Nullable BiConsumer<ServerPlayer, CustomPacketPayload> sink) {
        interceptor = sink;
    }
}
