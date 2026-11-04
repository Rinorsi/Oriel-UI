package dev.oriel.mod.server;


import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.TokenBucket;
import dev.oriel.mod.internal.OrielConfig;
import dev.oriel.mod.internal.net.ClosePayload;
import dev.oriel.mod.internal.net.DataPayload;
import dev.oriel.mod.internal.net.PatchPayload;
import dev.oriel.mod.internal.net.OrielNetwork;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One UI open on one player's screen, opened by {@link OrielServer}. Push data to it, listen for its
 * {@code oriel.send(channel, value)} messages, and close it. Use it on the server thread only.
 */
public final class OrielSession {
    /** SENT means delivery was attempted on a negotiated channel, not that the client accepted or rendered it. */
    public enum OpenStatus { SENT, CLIENT_UNSUPPORTED }

    private OpenStatus openStatus = OpenStatus.SENT;
    private final int id;
    private final ServerPlayer player;
    private final Map<String, List<BiConsumer<ServerPlayer, JsonElement>>> handlers = new HashMap<>();
    private final List<Runnable> closeHandlers = new ArrayList<>();
    private boolean open = true;
    /** Messages the page may send: {@code server.messageBurst}, then {@code server.messagesPerSecond}. */
    private final TokenBucket rate = new TokenBucket(OrielConfig.SERVER_MESSAGE_BURST.get(), OrielConfig.SERVER_MESSAGES_PER_SECOND.get());
    private boolean warnedRate, warnedPatch;

    @org.jetbrains.annotations.ApiStatus.Internal
    public OrielSession(int id, ServerPlayer player) {
        this.id = id;
        this.player = player;
    }

    public int id() {
        return id;
    }

    /** The player the session was opened for (after a respawn, handlers receive the current player object). */
    public ServerPlayer player() {
        return player;
    }

    public boolean isOpen() {
        return open;
    }

    /** The synchronous result of opening, retained after the session closes. */
    public OpenStatus openStatus() {
        return openStatus;
    }

    /** Runs immediately when opening failed, including when registered after the already-closed result was returned. */
    public OrielSession onOpenFailure(java.util.function.Consumer<OpenStatus> handler) {
        if (openStatus != OpenStatus.SENT) handler.accept(openStatus);
        return this;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void unsupportedClient() {
        openStatus = OpenStatus.CLIENT_UNSUPPORTED;
    }

    /** Replaces {@code oriel.data} on the client; the page's {@code oriel.on('data', fn)} listeners run. */
    public OrielSession push(JsonElement data) {
        if (open) OrielNetwork.sendToPlayer(player, new DataPayload(id, OrielServer.json(data)));
        return this;
    }

    /**
     * Sets one top-level field of the client's {@code oriel.data}, or removes it when {@code value} is null or JSON
     * null, and keeps the rest: only {@code value} is sent. The page's {@code oriel.on('patch', fn)} listeners get
     * {@code {key, value}}, then its 'data' listeners get {@code (oriel.data, key)}. The value counts against the
     * same limits as {@link #push}, and the client drops a patch that would take its data over
     * {@code client.maxDataChars}.
     *
     * @throws IllegalArgumentException when the value is over {@code server.maxDataChars} or the key is over
     *                                  {@link PatchPayload#MAX_KEY} characters
     */
    public OrielSession patch(String key, @Nullable JsonElement value) {
        if (key.length() > PatchPayload.MAX_KEY) {
            throw new IllegalArgumentException("Oriel data key is " + key.length() + " chars; the limit is " + PatchPayload.MAX_KEY);
        }
        String json = OrielServer.json(value == null ? JsonNull.INSTANCE : value);
        if (open && !OrielNetwork.sendToPlayer(player, new PatchPayload(id, key, json)) && !warnedPatch) {
            warnedPatch = true;
            Constants.LOG.warn("Oriel session {}: {}'s client can't take patches (an older Oriel); use push", id, player.getGameProfile().name());
        }
        return this;
    }

    /** Runs {@code handler} for each {@code oriel.send(channel, value)} from the page. */
    public OrielSession onMessage(String channel, BiConsumer<ServerPlayer, JsonElement> handler) {
        handlers.computeIfAbsent(channel, c -> new ArrayList<>()).add(handler);
        return this;
    }

    /** Runs once when the session ends: the player closed the screen, left, or {@link #close()} was called. */
    public OrielSession onClose(Runnable handler) {
        if (open) closeHandlers.add(handler);
        else handler.run();
        return this;
    }

    /** Closes the player's screen (if it still shows this session) and ends the session. */
    public void close() {
        if (!open) return;
        OrielNetwork.sendToPlayer(player, new ClosePayload(id));
        OrielServer.end(this);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void dispatch(ServerPlayer sender, String channel, JsonElement value) {
        List<BiConsumer<ServerPlayer, JsonElement>> list = handlers.get(channel);
        if (list == null) {
            Constants.LOG.debug("Oriel session {}: no handler for channel '{}'", id, channel);
            return;
        }
        for (BiConsumer<ServerPlayer, JsonElement> h : List.copyOf(list)) {
            try {
                h.accept(sender, value);
            } catch (RuntimeException e) {
                Constants.LOG.error("Oriel session {}: handler for '{}' failed", id, channel, e);
            }
        }
    }

    /** False when the page sends faster than its rate limit allows. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public boolean tryAcquire() {
        if (rate.tryTake()) return true;
        if (!warnedRate) {
            warnedRate = true;
            Constants.LOG.warn("Oriel session {}: {} is sending too many messages; dropping some", id, player.getGameProfile().name());
        }
        return false;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void ended() {
        open = false;
        handlers.clear();
        for (Runnable r : closeHandlers) {
            try {
                r.run();
            } catch (RuntimeException e) {
                Constants.LOG.error("Oriel session {}: close handler failed", id, e);
            }
        }
        closeHandlers.clear();
    }
}
