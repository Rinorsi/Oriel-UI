package dev.oriel.mod.server;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.net.OrielNetwork;
import dev.oriel.mod.internal.registry.OrielRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Server-owned repair forge. Client instances only mirror vanilla slot and data-slot packets. No screen is registered here. */
public final class ForgeMenu extends AbstractContainerMenu {
    public static final MenuType<ForgeMenu> TYPE = new MenuType<>(ForgeMenu::new, FeatureFlags.VANILLA_SET);
    public static final int INPUT = 0, MATERIAL = 1, OUTPUT = 2, PLAYER_START = 3, HOTBAR_START = 30, END = 39;
    public static final int WORK_TICKS = 40;
    public static final int IDLE = 0, WORKING = 1, OUTPUT_BLOCKED = 2;

    /** Stable recipe ids for the page; matching and result creation are only called by the server. */
    public record Recipe(String id, Item input, Item material) {
        boolean matches(ItemStack stack, ItemStack reagent) {
            return stack.is(input) && stack.isDamageableItem() && stack.getDamageValue() > 0 && reagent.is(material);
        }

        ItemStack result(ItemStack stack) {
            ItemStack result = stack.copyWithCount(1);
            result.setDamageValue(Math.max(0, result.getDamageValue() - Math.max(1, result.getMaxDamage() / 4)));
            return result;
        }
    }

    public static final List<Recipe> RECIPES = List.of(
            new Recipe("oriel:repair_iron_sword", Items.IRON_SWORD, Items.IRON_INGOT),
            new Recipe("oriel:repair_diamond_sword", Items.DIAMOND_SWORD, Items.DIAMOND));

    private final Player owner;
    private final SimpleContainer storage = new SimpleContainer(3);
    // progress, duration, selected recipe (index + 1; zero means none), state
    private final ContainerData data = new SimpleContainerData(4);
    private ItemStack previousInput = ItemStack.EMPTY, previousMaterial = ItemStack.EMPTY;
    private boolean closed;
    private long lastWorkTick = Long.MIN_VALUE;

    public ForgeMenu(int id, Inventory inventory) {
        super(TYPE, id);
        owner = inventory.player;
        addSlot(new Slot(storage, INPUT, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) {
                return RECIPES.stream().anyMatch(r -> stack.is(r.input()));
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(storage, MATERIAL, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) {
                return RECIPES.stream().anyMatch(r -> stack.is(r.material()));
            }
        });
        addSlot(new Slot(storage, OUTPUT, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int slot = 9; slot < 36; slot++) addSlot(new Slot(inventory, slot, 0, 0));
        for (int slot = 0; slot < 9; slot++) addSlot(new Slot(inventory, slot, 0, 0));
        data.set(1, WORK_TICKS);
        addDataSlots(data);
    }

    public static void init() { OrielRegistry.add(Registries.MENU, "forge", () -> TYPE); }

    /** Opens the server menu; the consuming client must register its screen for TYPE. */
    public static boolean open(ServerPlayer player) {
        if (!OrielNetwork.canReceive(player, OpenPayload.TYPE)) {
            player.sendSystemMessage(Component.literal("Oriel is required on the client to use this forge."));
            return false;
        }
        return player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ForgeMenu(id, inventory),
                Component.literal("Oriel Forge"))).isPresent();
    }

    @Override public boolean stillValid(Player player) { return !closed && player == owner && player.isAlive(); }

    /** Vanilla calls broadcastChanges once per server player tick; no extra loader tick or client timer is needed. */
    @Override public void broadcastChanges() {
        if (owner instanceof ServerPlayer && !closed && lastWorkTick != owner.level().getGameTime()) {
            lastWorkTick = owner.level().getGameTime();
            advance();
        }
        super.broadcastChanges();
    }

    private void advance() {
        ItemStack input = storage.getItem(INPUT), material = storage.getItem(MATERIAL);
        if (!ItemStack.matches(input, previousInput) || !ItemStack.matches(material, previousMaterial)) data.set(0, 0);
        previousInput = input.copy();
        previousMaterial = material.copy();
        int selected = -1;
        for (int i = 0; i < RECIPES.size(); i++) if (RECIPES.get(i).matches(input, material)) { selected = i; break; }
        data.set(2, selected + 1);
        if (!storage.getItem(OUTPUT).isEmpty()) {
            data.set(0, 0);
            data.set(3, OUTPUT_BLOCKED);
        } else if (selected < 0) {
            data.set(0, 0);
            data.set(3, IDLE);
        } else {
            data.set(3, WORKING);
            data.set(0, data.get(0) + 1);
            if (data.get(0) >= WORK_TICKS) {
                ItemStack result = RECIPES.get(selected).result(input);
                // Commit on the server thread, only after validating the current inputs and empty output.
                storage.removeItem(INPUT, 1);
                storage.removeItem(MATERIAL, 1);
                storage.setItem(OUTPUT, result);
                data.set(0, 0);
                data.set(2, 0);
                data.set(3, OUTPUT_BLOCKED);
            }
        }
    }

    /** Data for OrielScreens.registerContainer's extra function, from values synchronized by vanilla. */
    public JsonObject pageData() {
        JsonObject result = new JsonObject();
        result.addProperty("progress", data.get(0));
        result.addProperty("duration", data.get(1));
        result.addProperty("state", data.get(3));
        int selected = data.get(2) - 1;
        JsonArray available = new JsonArray();
        if (selected >= 0 && selected < RECIPES.size()) available.add(RECIPES.get(selected).id());
        result.add("availableRecipes", available);
        result.addProperty("selectedRecipe", selected >= 0 && selected < RECIPES.size() ? RECIPES.get(selected).id() : "");
        return result;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, END, true)) return ItemStack.EMPTY;
        } else if (slots.get(INPUT).mayPlace(stack)) {
            if (!moveItemStackTo(stack, INPUT, INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (slots.get(MATERIAL).mayPlace(stack)) {
            if (!moveItemStackTo(stack, MATERIAL, MATERIAL + 1, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!closed && owner instanceof ServerPlayer) clearContainer(player, storage);
        closed = true;
    }
}
