package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.OrielHud;

import com.google.gson.JsonObject;
import dev.oriel.kit.OrielKit;
import dev.oriel.kit.preset.InventoryEffectsPreset;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.OrielConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The player's effects beside vanilla's survival and creative inventories, in place of vanilla's list: the kit's
 * {@link OrielKit#INVENTORY_EFFECTS} page as a HUD overlay over those screens (interactive there, so its groups
 * fold), placed in the free space beside the inventory and moved when the recipe book opens or the window resizes.
 * The inventory screens themselves are vanilla's.
 *
 * <p>Vanilla's list is hidden while the panel shows: on NeoForge by cancelling its event; on Fabric, which has none,
 * by wrapping the screen's {@link EffectsInInventory} (widened for this) in one that draws nothing then, and
 * delegates to the original otherwise. With {@code client.inventoryEffects} off, or no room beside the inventory,
 * vanilla's list stays.
 */
public final class InventoryEffects {
    public static final Identifier PANEL = Constants.id("inventory_effects");
    /** Between the inventory and the panel and the screen edge; the widest the panel gets; the narrowest it shows at. */
    private static final int GAP = 4, WIDTH = 140, MIN_WIDTH = 30;
    private static @Nullable JsonObject placed;

    private InventoryEffects() {}

    public static void init() {
        OrielHud.register(PANEL, OrielKit.INVENTORY_EFFECTS, InventoryEffects::accepts);
    }

    private static boolean accepts(@Nullable Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }

    /** Every client tick, and before an inventory is drawn: shows, moves or hides the panel. */
    public static void tick() {
        prepare(McClient.screen());
    }

    public static void prepare(@Nullable Screen screen) {
        JsonObject place = accepts(screen) && PlayerEffects.hasEffects() && OrielConfig.CLIENT_INVENTORY_EFFECTS.get()
                ? place((AbstractContainerScreen<?>) screen) : null;
        if (place == null) {
            if (OrielHud.isShown(PANEL)) OrielHud.hide(PANEL);
            placed = null;
            return;
        }
        if (!place.equals(placed)) {
            placed = place;
            OrielHud.patch(PANEL, InventoryEffectsPreset.PLACE, place);
        }
        if (!OrielHud.isShown(PANEL)) OrielHud.show(PANEL);
    }

    /** Right of the inventory, where vanilla's list goes; left of it (and of an open recipe book) when that is roomier. */
    private static @Nullable JsonObject place(AbstractContainerScreen<?> screen) {
        int right = screen.leftPos + screen.imageWidth + GAP, rightRoom = screen.width - right - GAP;
        int leftEdge = screen.leftPos;
        // The recipe book's tabs reach past its left edge.
        if (screen instanceof InventoryScreen inventory && inventory.recipeBookComponent.isVisible()) leftEdge -= RecipeBookComponent.IMAGE_WIDTH + 30;
        int leftRoom = leftEdge - 2 * GAP;
        boolean onRight = rightRoom >= Math.min(WIDTH, leftRoom);
        int room = onRight ? rightRoom : leftRoom;
        if (room < MIN_WIDTH) return null;
        int width = Math.min(WIDTH, room), y = Math.max(GAP, screen.topPos);
        JsonObject place = new JsonObject();
        place.addProperty("x", onRight ? right : leftEdge - GAP - width);
        place.addProperty("y", y);
        place.addProperty("width", width);
        place.addProperty("height", Math.max(32, screen.height - y - GAP));
        return place;
    }

    /** Whether vanilla's list is left out now: the panel is there in its place. */
    public static boolean hideVanilla(Screen screen) {
        return screen == McClient.screen() && accepts(screen) && OrielHud.isShown(PANEL) && !McClient.hudHidden()
                && !McClient.loadingOverlay() && OrielConfig.CLIENT_HIDE_INVENTORY_EFFECTS.get();
    }

    /** Fabric: wraps a new inventory screen's effects list so that it can be left out ({@link #hideVanilla}). */
    public static void wrapVanilla(Screen screen) {
        if (screen instanceof InventoryScreen inventory && !(inventory.effects instanceof Hideable)) {
            inventory.effects = new Hideable(inventory, inventory.effects);
        } else if (screen instanceof CreativeModeInventoryScreen creative && !(creative.effects instanceof Hideable)) {
            creative.effects = new Hideable(creative, creative.effects);
        }
        prepare(screen);
    }

    /** Vanilla's effects list, or another mod's in its place, drawn unless {@link #hideVanilla}. */
    private static final class Hideable extends EffectsInInventory {
        private final Screen owner;
        private final EffectsInInventory original;

        Hideable(AbstractContainerScreen<?> owner, EffectsInInventory original) {
            super(owner);
            this.owner = owner;
            this.original = original;
        }

        @Override
        public boolean canSeeEffects() {
            return !hideVanilla(owner) && original.canSeeEffects();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!hideVanilla(owner)) original.extractRenderState(graphics, mouseX, mouseY);
        }
    }
}
