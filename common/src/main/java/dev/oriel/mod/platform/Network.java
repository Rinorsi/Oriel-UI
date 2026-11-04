package dev.oriel.mod.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-specific networking (META-INF/services). Payload types are registered by each loader's hooks from
 * {@code OrielNetwork}; common code only sends.
 */
public interface Network {
    /** Whether a play channel was negotiated, without sending or opening a page. */
    boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type);

    /**
     * Sends a clientbound play payload to one player.
     *
     * @return false if the player's client cannot receive this payload (it lacks Oriel)
     */
    boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
