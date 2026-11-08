package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.OrielScreen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.PageData;
import dev.oriel.mod.internal.RecentEvents;
import dev.oriel.mod.internal.TokenBucket;
import dev.oriel.mod.internal.OrielConfig;
import dev.oriel.mod.internal.net.ClosePayload;
import dev.oriel.mod.internal.net.ClosedPayload;
import dev.oriel.mod.internal.net.DataPayload;
import dev.oriel.mod.internal.net.JsonLimits;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.net.PatchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * What a client lets a server do with pages. Every server page goes through here:
 * <ul>
 *   <li>{@code client.serverPages}: allow, ask once per server visit, or block;</li>
 *   <li>size and nesting caps on the page and its data ({@code client.maxInlineHtmlChars}, {@code client.maxDataChars},
 *       {@code client.maxDataDepth}), counting what patches add to the data;</li>
 *   <li>a page waits while the player is in another kind of screen (chat, the pause menu, options, a sign, a
 *       confirmation), so a server cannot take keystrokes meant for chat or keep the player out of the pause menu;
 *       container screens and other Oriel screens are replaced, as vanilla replaces them with a container;</li>
 *   <li>a rate limit on opens ({@code client.openBurst}, {@code client.opensPerSecond}); a waiting page is replaced
 *       by a newer one;</li>
 *   <li>a cap on reopen loops: a server that opens a page within a second of the player closing one with Escape,
 *       {@code client.reopenStrikes} times in a row within 15 seconds, can't open pages for
 *       {@code client.reopenBlockSeconds}.</li>
 * </ul>
 * A page that is refused, replaced while waiting or blocked is reported closed, so the server's session ends.
 * Render thread only; the state is per connection.
 */
public final class ServerPages {
    /**
     * An open this soon after the player closed a server page with Escape is a reopen. A server loop reopens within a
     * tick or two plus the round trip; a player who closes a shop and clicks its keeper again takes longer.
     */
    private static final long REOPEN_MS = 1000;
    private static final long STRIKE_WINDOW_MS = 15_000;

    private static @Nullable ClientPacketListener connection;
    private static @Nullable OpenPayload pending;
    /** The waiting page's data, with the server's later pushes and patches. */
    private static final PageData pendingData = new PageData();
    /** Whether the player allowed pages from this server (ask mode), or null until asked. */
    private static @Nullable Boolean allowed;
    private static boolean asking, replacing;
    private static TokenBucket opens = newOpenBucket();
    private static long lastPlayerClose = Long.MIN_VALUE / 2, blockedUntil;
    private static final RecentEvents strikes = new RecentEvents(STRIKE_WINDOW_MS);
    private static final Set<String> refusalsLogged = new HashSet<>();

    private ServerPages() {}

    // ---- Payloads ----

    public static void open(OpenPayload p) {
        sameConnection();
        long now = Util.getMillis();
        String refused = refusal(p, now);
        if (refused != null) {
            // Once per reason and visit: a hostile server answering each refusal with a new page would flood the log.
            if (refusalsLogged.add(refused)) Constants.LOG.info("Oriel: not showing the server's pages: {}", refused);
            refuse(p.session());
            return;
        }
        if (now - lastPlayerClose >= REOPEN_MS) strikes.clear(); // only reopens in a row count
        else if (strike(now)) {
            refuse(p.session());
            return;
        }
        if (pending != null) refuse(pending.session());
        pending = p;
        pendingData.set(p.data());
        tryShow();
    }

    public static void data(DataPayload p) {
        if (!dataFits(p.data())) {
            Constants.LOG.warn("Oriel: dropped data for session {}: over client.maxDataChars or client.maxDataDepth", p.session());
            return;
        }
        if (pending != null && pending.session() == p.session()) {
            pendingData.set(p.data());
            return;
        }
        OrielScreen screen = sessionScreen(p.session());
        if (screen != null) screen.driver().pushData(p.data());
    }

    /**
     * A patch is checked as data is: the value must be well-formed JSON within {@code client.maxDataDepth} (counting
     * the object it goes in) and the data it makes within {@code client.maxDataChars}.
     */
    public static void patch(PatchPayload p) {
        boolean waiting = pending != null && pending.session() == p.session();
        OrielScreen screen = waiting ? null : sessionScreen(p.session());
        if (!waiting && screen == null) return;
        PageData data = waiting ? pendingData : screen.driver().pageData();
        JsonElement value;
        try {
            value = JsonLimits.parse(p.value(), OrielConfig.CLIENT_MAX_DATA_DEPTH.get() - 1);
        } catch (JsonParseException e) {
            value = null;
        }
        String json = value == null || value.isJsonNull() ? null : p.value();
        if (value == null || json != null && data.charsWith(p.key(), json.length()) > OrielConfig.CLIENT_MAX_DATA.get()) {
            Constants.LOG.warn("Oriel: dropped a patch for session {}: malformed, or over client.maxDataChars or client.maxDataDepth", p.session());
            return;
        }
        if (waiting) data.put(p.key(), json);
        else screen.driver().patchJson(p.key(), json);
    }

    public static void close(ClosePayload p) {
        if (pending != null && pending.session() == p.session()) {
            pending = null;
            return;
        }
        OrielScreen screen = sessionScreen(p.session());
        if (screen != null) {
            screen.driver().closedByServer();
            screen.onClose();
        }
    }

    /** A server page closed without the server asking; {@code byEscape} when the player pressed Escape to close it. */
    public static void closed(boolean byEscape) {
        if (byEscape && !replacing) lastPlayerClose = Util.getMillis();
    }

    /** Whether this server is stopped from opening pages for reopening them (for the dev autopilot). */
    public static boolean blocked() {
        return Util.getMillis() < blockedUntil;
    }

    /** Every client tick: shows a waiting page once the player's screen and the rate limit allow it. */
    public static void tick() {
        sameConnection();
        if (pending != null) tryShow();
    }

    // ---- Internals ----

    private static @Nullable String refusal(OpenPayload p, long now) {
        if (OrielConfig.CLIENT_SERVER_PAGES.get() == OrielConfig.ServerPages.BLOCK) return "server pages are blocked (client.serverPages)";
        if (Boolean.FALSE.equals(allowed)) return "the player declined pages from this server";
        if (now < blockedUntil) return "the server kept reopening pages";
        if (p.html().length() > OrielConfig.CLIENT_MAX_INLINE_HTML.get()) return "the page is over client.maxInlineHtmlChars";
        if (p.html().isEmpty() && OrielPages.isFolderUrl(p.url())) return "the page is in a folder on this computer, which servers may not open";
        if (!dataFits(p.data())) return "its data is over client.maxDataChars or client.maxDataDepth";
        return null;
    }

    private static boolean dataFits(String json) {
        return json.length() <= OrielConfig.CLIENT_MAX_DATA.get() && JsonLimits.depth(json) <= OrielConfig.CLIENT_MAX_DATA_DEPTH.get();
    }

    /** Counts a reopen; true (and the server is blocked for a while) when there were too many lately. */
    private static boolean strike(long now) {
        if (!strikes.record(now, OrielConfig.CLIENT_REOPEN_STRIKES.get())) return false;
        int seconds = OrielConfig.CLIENT_REOPEN_BLOCK_SECONDS.get();
        blockedUntil = now + seconds * 1000L;
        refusePending();
        Constants.LOG.warn("Oriel: the server reopened pages as they were closed; blocking its pages for {} s", seconds);
        McClient.systemMessage(Component.translatable("oriel.serverPages.blocked", seconds));
        return true;
    }

    private static void tryShow() {
        OpenPayload p = pending;
        if (p == null || asking) return;
        if (!replaceable(McClient.screen())) return;
        if (OrielConfig.CLIENT_SERVER_PAGES.get() == OrielConfig.ServerPages.ASK && allowed == null) {
            ask();
            return;
        }
        if (Boolean.FALSE.equals(allowed) || OrielConfig.CLIENT_SERVER_PAGES.get() == OrielConfig.ServerPages.BLOCK) {
            refusePending();
            return;
        }
        if (!opens.tryTake()) return;
        pending = null;
        OrielScreen screen = new OrielScreen(p.html().isEmpty() ? p.url() : "", p.html().isEmpty() ? null : p.html(), p.session());
        screen.driver().pushData(pendingData);
        pendingData.clear();
        // The screen this replaces reports its session closed; that is not the player closing it.
        replacing = true;
        try {
            McClient.setScreen(screen);
        } finally {
            replacing = false;
        }
    }

    /** Screens a server page may replace: none, a container screen, a Oriel screen. Others make it wait. */
    private static boolean replaceable(@Nullable Screen screen) {
        return screen == null || screen instanceof OrielScreen || screen instanceof AbstractContainerScreen<?>;
    }

    /** Asks once per server visit; the question replaces the screen, as the page would have. */
    private static void ask() {
        asking = true;
        McClient.setScreen(new ConfirmScreen(yes -> {
            asking = false;
            allowed = yes;
            McClient.setScreen(null);
            if (!yes) refusePending();
            tryShow();
        }, Component.translatable("oriel.serverPages.ask.title"), Component.translatable("oriel.serverPages.ask.message"),
                Component.translatable("oriel.serverPages.ask.allow"), Component.translatable("oriel.serverPages.ask.deny")));
    }

    private static void refuse(int session) {
        if (!OrielClient.sendToServer(new ClosedPayload(session)))
            Constants.LOG.warn("Oriel: refused session {} locally; server was not notified", session);
    }

    /** Refuses the waiting page, if there is one. */
    private static void refusePending() {
        if (pending == null) return;
        refuse(pending.session());
        pending = null;
    }

    /** Starts over when the client joined another server (or the same one again). */
    private static void sameConnection() {
        ClientPacketListener now = Minecraft.getInstance().getConnection();
        if (now == connection) return;
        connection = now;
        pending = null;
        pendingData.clear();
        allowed = null;
        asking = false;
        opens = newOpenBucket();
        strikes.clear();
        refusalsLogged.clear();
        blockedUntil = 0;
        lastPlayerClose = Long.MIN_VALUE / 2;
    }

    private static TokenBucket newOpenBucket() {
        return new TokenBucket(OrielConfig.CLIENT_OPEN_BURST.get(), OrielConfig.CLIENT_OPENS_PER_SECOND.get());
    }

    private static @Nullable OrielScreen sessionScreen(int session) {
        return McClient.screen() instanceof OrielScreen s && s.driver().session() == session ? s : null;
    }
}
