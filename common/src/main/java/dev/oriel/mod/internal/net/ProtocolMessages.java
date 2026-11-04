package dev.oriel.mod.internal.net;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Server-side bilingual fallback: a client without this mod cannot resolve our translation keys. */
public final class ProtocolMessages {
    private ProtocolMessages() {}

    public static String rejection(boolean installed) {
        String key = installed ? "oriel.disconnect.protocol" : "oriel.disconnect.required";
        return translated("en_us", key) + (char) 10 + translated("zh_cn", key);
    }

    private static String translated(String locale, String key) {
        String resource = "/assets/oriel/lang/" + locale + ".json";
        try (var stream = ProtocolMessages.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing protocol language file: " + resource);
            String template = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().get(key).getAsString();
            return String.format(Locale.ROOT, template, OrielNetwork.PROTOCOL_VERSION);
        } catch (java.io.IOException e) { throw new IllegalStateException(resource, e); }
    }
}
