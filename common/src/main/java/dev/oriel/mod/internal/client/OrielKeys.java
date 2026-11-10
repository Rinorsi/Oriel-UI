package dev.oriel.mod.internal.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.oriel.kit.OrielKit;
import dev.oriel.mod.client.OrielScreens;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.platform.Services;
import net.minecraft.client.KeyMapping;

/**
 * Oriel's key bindings, listed under Options > Controls > Oriel UI. The loaders register {@link #HOME}; {@link #tick()} acts on it.
 * The home key is bound to F8 in a development run and unbound for players (a library should not take a key in a modpack by
 * itself); either way it can be set in the controls screen.
 */
public final class OrielKeys {
    // NeoForge 26.1 deprecates Category.register in favour of its own event; the vanilla call works on every target.
    @SuppressWarnings("deprecation")
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Constants.id("ui"));
    /** Opens Oriel's home page (the gallery; its Demos button leads to the finished showcase pages). */
    public static final KeyMapping HOME = new KeyMapping("key.oriel.home",
            Services.PLATFORM.isDevelopmentEnvironment() ? InputConstants.KEY_F8 : InputConstants.UNKNOWN.getValue(), CATEGORY);

    private OrielKeys() {}

    /** Once a client tick: opens the home page for each press (the game only delivers the key while no screen is open). */
    public static void tick() {
        while (HOME.consumeClick()) {
            OrielScreens.open(dev.oriel.kit.ReferenceContent.home());
        }
    }
}
