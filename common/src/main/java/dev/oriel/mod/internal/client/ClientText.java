package dev.oriel.mod.internal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/** Native client messages use Minecraft's current language and resource packs, including the shared kit catalog. */
public final class ClientText {
    private ClientText() {}

    public static String t(String key, Object... args) { return I18n.get(key, args); }

    public static String plural(String key, long count, Object... args) {
        Object[] values = new Object[args.length + 1];
        values[0] = count;
        System.arraycopy(args, 0, values, 1, args.length);
        String locale = Minecraft.getInstance().getLanguageManager().getSelected();
        return t(key + (locale.startsWith("en") && Math.abs(count) == 1 ? ".one" : ".other"), values);
    }
}
