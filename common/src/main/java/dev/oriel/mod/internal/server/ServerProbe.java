package dev.oriel.mod.internal.server;

import dev.oriel.mod.server.OrielServer;
import dev.oriel.mod.server.OrielSession;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.net.DataPayload;
import dev.oriel.mod.internal.net.OrielNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** Opt-in local integration probe. No commands are registered unless the dedicated test process enables it. */
public final class ServerProbe {
    public static final int CONTROL = -2000001;
    private static OrielSession session;
    private static ServerPlayer target, observer;
    private static int replies;
    private static boolean checkedObserver;

    private ServerProbe() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!Boolean.getBoolean("oriel.serverProbe")) return;
        dispatcher.register(Commands.literal("oriel-probe")
                .then(Commands.literal("start").requires(source -> source.getEntity() == null)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("observer", EntityArgument.player()).executes(c -> {
                                    start(EntityArgument.getPlayer(c, "target"), EntityArgument.getPlayer(c, "observer"));
                                    return 1;
                                }))))
                .then(Commands.literal("observer-ok").executes(c -> {
                    if (c.getSource().getPlayerOrException() != observer) return 0;
                    checkedObserver = true;
                    finish();
                    return 1;
                })));
        Constants.LOG.info("ORIEL_SERVER_PROBE loaded");
    }

    private static void start(ServerPlayer a, ServerPlayer b) {
        if (a == b) throw new IllegalArgumentException("Probe needs two distinct connections");
        if (session != null) session.close();
        target = a;
        observer = b;
        replies = 0;
        checkedObserver = false;
        JsonObject data = new JsonObject();
        data.addProperty("probe", "owner-only");
        session = OrielServer.open(a, OrielDemos.LIVE_PAGE, data);
        if (!session.isOpen()) throw new IllegalStateException("Target cannot receive Oriel pages");
        session.onMessage("probe-reply", (sender, value) -> {
            if (sender != target || !value.getAsString().equals("owner-only"))
                throw new IllegalStateException("Unexpected probe reply");
            replies++;
            finish();
        });
        JsonObject attack = new JsonObject();
        attack.addProperty("targetSession", session.id());
        if (!OrielNetwork.sendToPlayer(b, new DataPayload(CONTROL, attack.toString())))
            throw new IllegalStateException("Observer cannot receive control packet");
    }

    private static void finish() {
        if (!checkedObserver || replies == 0) return;
        if (replies != 1 || OrielServer.session(observer, session.id()) != null || !session.isOpen()) {
            Constants.LOG.error("ORIEL_SERVER_PROBE FAIL isolation");
            return;
        }
        session.close();
        Constants.LOG.info("ORIEL_SERVER_PROBE PASS two connections, one page, one owner reply, spoofed reply/close rejected");
        JsonObject done = new JsonObject();
        done.addProperty("done", true);
        OrielNetwork.sendToPlayer(target, new DataPayload(CONTROL, done.toString()));
        OrielNetwork.sendToPlayer(observer, new DataPayload(CONTROL, done.toString()));
    }
}
