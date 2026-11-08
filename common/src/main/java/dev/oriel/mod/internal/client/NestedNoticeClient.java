package dev.oriel.mod.internal.client;

import com.google.gson.JsonObject;
import dev.oriel.kit.OrielKit;
import dev.oriel.mod.client.OrielScreen;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.OrielConfig;
import dev.oriel.mod.internal.nested.NestedRuntime;
import dev.oriel.mod.internal.platform.Services;
import net.minecraft.client.gui.screens.TitleScreen;
import java.io.IOException;
import java.util.Map;

/** Only the physical client calls this bridge; dedicated servers never link a screen class. */
final class NestedNoticeClient {
    private static boolean titleVisited;
    private NestedNoticeClient() {}

    static void tick() {
        if (titleVisited || McClient.loadingOverlay() || !(McClient.screen() instanceof TitleScreen previousTitle)) return;
        titleVisited = true;
        var report = NestedRuntime.notice(OrielConfig.CLIENT_NESTED_NOTICE.get() == OrielConfig.NestedNotice.ON);
        if (report == null) return;
        JsonObject data = new JsonObject();
        data.addProperty("parents", report.parents());
        data.addProperty("active", report.active().label());
        var screen = new OrielScreen(OrielKit.page("nested-notice"), null, -1) {
            @Override public void onClose() { McClient.setScreen(previousTitle); }
        };
        screen.driver().push(data);
        screen.driver().onMessage(OrielKit.CHANNEL, request -> {
            if (!request.isJsonObject()) return;
            var object = request.getAsJsonObject();
            if (object.size() != 2 || !object.has("op") || !object.get("op").isJsonPrimitive()
                    || !object.has("disable") || !object.get("disable").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("disable").isBoolean()) return;
            String op = object.get("op").getAsString();
            if (!op.equals("nested-dismiss") && !op.equals("nested-setting")) return;
            try {
                if (op.equals("nested-setting") || object.get("disable").getAsBoolean()) {
                    String error = OrielConfig.update(Services.PLATFORM.configDir().resolve(OrielConfig.FILE_NAME),
                            Map.of("client.nestedNotice", object.get("disable").getAsBoolean() ? "off" : "on"));
                    if (error != null) throw new IOException(error);
                }
                if (op.equals("nested-dismiss")) screen.onClose();
            } catch (IOException e) { Constants.LOG.warn("Oriel: cannot save nested notice preference: {}", e.toString()); }
        });
        McClient.setScreen(screen);
        NestedRuntime.noticed();
    }
}
