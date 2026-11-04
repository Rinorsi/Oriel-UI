package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.OrielScreen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.net.ClosedPayload;
import dev.oriel.mod.internal.net.DataPayload;
import dev.oriel.mod.internal.net.MessagePayload;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.server.ServerProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Nonvisual network test driver. Uses existing pages; never changes their DOM or styles. */
public final class ServerProbeClient {
    public static final boolean ENABLED = !System.getProperty("oriel.probeRole", "").isEmpty();
    private static final boolean OWNER = System.getProperty("oriel.probeRole", "").equals("A");
    private static final long START = System.nanoTime();
    private static int opens, session = -1, attack = -1, delay, stopAfter = -1;
    private static boolean sent, checked;

    private ServerProbeClient() {}

    public static void received(CustomPacketPayload payload) {
        if (payload instanceof OpenPayload p) {
            opens++;
            session = p.session();
        }
        if (payload instanceof DataPayload p && p.session() == ServerProbe.CONTROL) {
            JsonObject data = JsonParser.parseString(p.data()).getAsJsonObject();
            if (data.has("targetSession")) attack = data.get("targetSession").getAsInt();
            if (data.has("done")) {
                boolean okay = OWNER ? opens == 1 && sent : opens == 0 && checked && !(McClient.screen() instanceof OrielScreen);
                Constants.LOG.info("ORIEL_CLIENT_PROBE {} role={} opens={}", okay ? "PASS" : "FAIL", OWNER ? "A" : "B", opens);
                stopAfter = 10;
            }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if ((System.getProperty("oriel.probeProtocol") != null || Boolean.getBoolean("oriel.probeMissing"))
                && (McClient.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                || McClient.screen() != null && McClient.screen().getClass().getName().equals(
                        "net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen"))) {
            Constants.LOG.info("ORIEL_CLIENT_PROBE PASS {} disconnected", Boolean.getBoolean("oriel.probeMissing") ? "missing client" : "mismatched protocol");
            mc.stop();
            return;
        }
        if (stopAfter >= 0 && stopAfter-- == 0) { mc.stop(); return; }
        if (System.nanoTime() - START > 180_000_000_000L) {
            Constants.LOG.error("ORIEL_CLIENT_PROBE FAIL timeout");
            mc.stop();
            return;
        }
        if (mc.player == null) return;
        if (OWNER && session >= 0 && !sent && McClient.screen() instanceof OrielScreen screen
                && screen.driver().document() != null && ++delay > 40) {
            OrielClient.sendToServer(new MessagePayload(session, "probe-reply", new JsonPrimitive("owner-only").toString()));
            sent = true;
        }
        if (!OWNER && attack >= 0 && !checked) {
            OrielClient.sendToServer(new MessagePayload(attack, "probe-reply", new JsonPrimitive("owner-only").toString()));
            OrielClient.sendToServer(new ClosedPayload(attack));
            if (opens != 0 || McClient.screen() instanceof OrielScreen) {
                Constants.LOG.error("ORIEL_CLIENT_PROBE FAIL observer got a page");
                mc.stop();
                return;
            }
            mc.player.connection.sendCommand("oriel-probe observer-ok");
            checked = true;
        }
    }
}
