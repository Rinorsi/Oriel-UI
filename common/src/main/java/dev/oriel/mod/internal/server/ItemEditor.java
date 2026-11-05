package dev.oriel.mod.internal.server;

import dev.oriel.mod.server.OrielServer;
import dev.oriel.mod.server.OrielSession;
import dev.oriel.kit.LocalizedMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.oriel.mod.internal.Constants;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The item data editor's server side ({@code /oriel edit}): opens {@code oriel:ui/item_editor.html} with a snapshot
 * of the main-hand item's data components and applies an edit the page sends back. The server owns the item: a
 * save is refused unless the player may still use the command, holds the same stack in the same slot, and every
 * field is in range; then only the fields that changed are written, on the server thread, and every component the
 * editor does not know is kept as it was.
 */
public final class ItemEditor {
    public static final String PAGE = "oriel:ui/item_editor.html";
    public static final int MAX_NAME = 50, MAX_LORE_LINES = 16, MAX_LORE_LENGTH = 100, MAX_ENCHANTMENTS = 32,
            MAX_ATTRIBUTES = 16, MAX_LEVEL = 255, MAX_DURABILITY = 100_000;
    public static final double MAX_AMOUNT = 2048;

    private ItemEditor() {}

    /** Opens the editor on the main-hand item; false for an empty hand or a client without Oriel. */
    public static boolean open(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oriel.server.item_editor.empty_hand"));
            return false;
        }
        int slot = player.getInventory().getSelectedSlot();
        // The stack the editor was opened on and its snapshot; a save moves both on, so the next save still matches.
        ItemStack[] original = {held.copy()};
        JsonObject[] before = {describe(player, original[0])};
        OrielSession[] session = new OrielSession[1];
        session[0] = OrielServer.open(player, PAGE, before[0])
                .onMessage("save", (p, value) -> {
                    JsonObject reply = new JsonObject();
                    try {
                        ItemStack saved = save(p, slot, original[0], before[0], value.getAsJsonObject());
                        original[0] = saved.copy();
                        before[0] = describe(p, saved);
                        reply.addProperty("status", "saved");
                        reply.add("item", before[0]);
                        Constants.LOG.info("Oriel: {} edited {}", p.getName().getString(), BuiltInRegistries.ITEM.getKey(saved.getItem()));
                    } catch (Rejected e) {
                        reply.addProperty("status", "rejected");
                        reply.add("reason", e.reason.json());
                    } catch (RuntimeException e) {
                        reply.addProperty("status", "rejected");
                        reply.add("reason", LocalizedMessage.text("oriel.server.item_editor.bad_data").json());
                        Constants.LOG.warn("Oriel: a malformed item edit from {}", p.getName().getString(), e);
                    }
                    session[0].patch("result", reply);
                });
        return session[0].openStatus() == OrielSession.OpenStatus.SENT;
    }

    /** A refused save; the message is shown to the player. */
    public static final class Rejected extends Exception {
        @java.io.Serial private static final long serialVersionUID = 1L;
        public final transient LocalizedMessage reason;
        Rejected(String key, Object... args) {
            this(LocalizedMessage.text(key, args));
        }
        Rejected(LocalizedMessage message) {
            super(message.key());
            reason = message;
        }
    }

    // ---- Snapshot ----

    /** What the page edits, plus the choices it offers (enchantments, attributes) and every component, read only. */
    public static JsonObject describe(ServerPlayer player, ItemStack stack) {
        JsonObject o = new JsonObject();
        o.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        o.addProperty("count", stack.getCount());
        o.addProperty("defaultName", stack.getItem().getName(stack).getString());
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            o.addProperty("name", custom.getString());
            TextColor colour = custom.getStyle().getColor();
            if (colour != null) o.addProperty("nameColor", String.format("#%06x", colour.getValue() & 0xffffff));
        }
        Rarity rarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        o.addProperty("rarity", rarity.getSerializedName());
        JsonArray lore = new JsonArray();
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) lore.add(line.getString());
        o.add("lore", lore);
        JsonArray enchants = new JsonArray();
        for (var e : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            JsonObject en = new JsonObject();
            en.addProperty("id", e.getKey().unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
            en.addProperty("level", e.getIntValue());
            enchants.add(en);
        }
        o.add("enchantments", enchants);
        o.addProperty("damage", stack.getDamageValue());
        o.addProperty("maxDamage", stack.getMaxDamage());
        o.addProperty("unbreakable", stack.has(DataComponents.UNBREAKABLE));
        Boolean glint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        o.add("glint", glint == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(glint));
        JsonArray attrs = new JsonArray();
        for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            JsonObject a = new JsonObject();
            a.addProperty("attribute", entry.attribute().unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
            a.addProperty("modifier", entry.modifier().id().toString());
            a.addProperty("amount", entry.modifier().amount());
            a.addProperty("operation", entry.modifier().operation().getSerializedName());
            a.addProperty("slot", entry.slot().getSerializedName());
            attrs.add(a);
        }
        o.add("attributes", attrs);

        // The choices.
        Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        JsonArray enchantChoices = new JsonArray();
        for (Identifier id : sorted(enchantments.keySet())) {
            JsonObject c = new JsonObject();
            c.addProperty("id", id.toString());
            c.addProperty("name", enchantments.getValue(id).description().getString());
            c.addProperty("max", enchantments.getValue(id).getMaxLevel());
            enchantChoices.add(c);
        }
        o.add("enchantmentChoices", enchantChoices);
        JsonArray attrChoices = new JsonArray();
        for (Identifier id : sorted(BuiltInRegistries.ATTRIBUTE.keySet())) {
            JsonObject c = new JsonObject();
            c.addProperty("id", id.toString());
            c.addProperty("name", Component.translatable(BuiltInRegistries.ATTRIBUTE.getValue(id).getDescriptionId()).getString());
            attrChoices.add(c);
        }
        o.add("attributeChoices", attrChoices);

        // Every component as JSON, for the tree (read only; the unknown ones are kept as they are).
        RegistryOps<JsonElement> ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject all = new JsonObject();
        JsonArray unencodable = new JsonArray();
        for (TypedDataComponent<?> c : stack.getComponents()) {
            Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
            if (key == null) continue;
            Optional<JsonElement> encoded;
            try {
                encoded = c.encodeValue(ops).result();
            } catch (RuntimeException failure) {
                // An extension codec must not turn a preserved, read-only component into a failed save.
                Constants.LOG.warn("Oriel: cannot encode read-only item component {}", key, failure);
                encoded = Optional.empty();
            }
            if (encoded.isEmpty()) unencodable.add(key.toString());
            all.add(key.toString(), encoded.orElse(new com.google.gson.JsonPrimitive("oriel.server.item_editor.unencodable")));
        }
        o.add("components", all);
        o.add("unencodableComponents", unencodable);
        return o;
    }

    private static List<Identifier> sorted(java.util.Set<Identifier> ids) {
        List<Identifier> list = new ArrayList<>(ids);
        list.sort((a, b) -> a.toString().compareTo(b.toString()));
        return list;
    }

    // ---- Save ----

    public static ItemStack save(ServerPlayer player, int slot, ItemStack original, JsonObject before, JsonObject edit) throws Rejected {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) throw new Rejected("oriel.server.item_editor.no_permission");
        if (player.getInventory().getSelectedSlot() != slot) throw new Rejected("oriel.server.item_editor.wrong_slot");
        ItemStack current = player.getInventory().getItem(slot);
        if (!ItemStack.matches(current, original)) throw new Rejected("oriel.server.item_editor.item_changed");
        ItemStack next = current.copy();

        if (changed(before, edit, "name") || changed(before, edit, "nameColor")) {
            String name = string(edit, "name");
            if (name == null || name.isBlank()) next.remove(DataComponents.CUSTOM_NAME);
            else {
                if (name.length() > MAX_NAME) throw new Rejected(LocalizedMessage.plural("oriel.server.item_editor.name_limit", MAX_NAME));
                Style style = Style.EMPTY.withItalic(false);
                String colour = string(edit, "nameColor");
                if (colour != null && colour.matches("#[0-9a-fA-F]{6}")) style = style.withColor(TextColor.fromRgb(Integer.parseInt(colour.substring(1), 16)));
                next.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style));
            }
        }
        if (changed(before, edit, "rarity")) {
            String r = string(edit, "rarity");
            Rarity rarity = null;
            for (Rarity value : Rarity.values()) if (value.getSerializedName().equals(r)) rarity = value;
            if (rarity == null) throw new Rejected("oriel.server.item_editor.unknown_rarity", r);
            next.set(DataComponents.RARITY, rarity);
        }
        if (changed(before, edit, "lore")) {
            JsonArray lines = edit.getAsJsonArray("lore");
            if (lines.size() > MAX_LORE_LINES) throw new Rejected(LocalizedMessage.plural("oriel.server.item_editor.lore_lines_limit", MAX_LORE_LINES));
            List<Component> lore = new ArrayList<>();
            for (JsonElement l : lines) {
                String text = l.getAsString();
                if (text.length() > MAX_LORE_LENGTH) throw new Rejected(LocalizedMessage.plural("oriel.server.item_editor.lore_length_limit", MAX_LORE_LENGTH));
                lore.add(Component.literal(text).withStyle(Style.EMPTY.withItalic(true).withColor(TextColor.fromRgb(0xaa00aa))));
            }
            if (lore.isEmpty()) next.remove(DataComponents.LORE);
            else next.set(DataComponents.LORE, new ItemLore(lore));
        }
        if (changed(before, edit, "enchantments")) {
            JsonArray list = edit.getAsJsonArray("enchantments");
            if (list.size() > MAX_ENCHANTMENTS) throw new Rejected(LocalizedMessage.plural("oriel.server.item_editor.enchantments_limit", MAX_ENCHANTMENTS));
            Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (JsonElement e : list) {
                JsonObject en = e.getAsJsonObject();
                Identifier id = Identifier.tryParse(en.get("id").getAsString());
                Optional<Holder.Reference<Enchantment>> holder = id == null ? Optional.empty() : registry.get(id);
                if (holder.isEmpty()) throw new Rejected("oriel.server.item_editor.unknown_enchantment", en.get("id").getAsString());
                int level = en.get("level").getAsInt();
                if (level < 1 || level > MAX_LEVEL) throw new Rejected("oriel.server.item_editor.enchantment_level", MAX_LEVEL);
                enchants.set(holder.get(), level);
            }
            EnchantmentHelper.setEnchantments(next, enchants.toImmutable());
        }
        if (changed(before, edit, "maxDamage") && next.isDamageableItem()) {
            int max = edit.get("maxDamage").getAsInt();
            if (max < 1 || max > MAX_DURABILITY) throw new Rejected("oriel.server.item_editor.max_durability", MAX_DURABILITY);
            next.set(DataComponents.MAX_DAMAGE, max);
        }
        if (changed(before, edit, "damage") && next.isDamageableItem()) {
            int damage = edit.get("damage").getAsInt();
            if (damage < 0 || damage >= next.getMaxDamage()) throw new Rejected("oriel.server.item_editor.damage", next.getMaxDamage() - 1);
            next.setDamageValue(damage);
        }
        if ((changed(before, edit, "maxDamage") || changed(before, edit, "damage"))
                && next.isDamageableItem() && next.getDamageValue() >= next.getMaxDamage())
            throw new Rejected("oriel.server.item_editor.damage", next.getMaxDamage() - 1);
        if (changed(before, edit, "unbreakable")) {
            if (edit.get("unbreakable").getAsBoolean()) next.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            else next.remove(DataComponents.UNBREAKABLE);
        }
        if (changed(before, edit, "glint")) {
            JsonElement g = edit.get("glint");
            if (g == null || g.isJsonNull()) next.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            else next.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, g.getAsBoolean());
        }
        if (changed(before, edit, "attributes")) {
            JsonArray list = edit.getAsJsonArray("attributes");
            if (list.size() > MAX_ATTRIBUTES) throw new Rejected(LocalizedMessage.plural("oriel.server.item_editor.attributes_limit", MAX_ATTRIBUTES));
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            int n = 0;
            for (JsonElement e : list) {
                JsonObject a = e.getAsJsonObject();
                Identifier id = Identifier.tryParse(a.get("attribute").getAsString());
                Optional<Holder.Reference<Attribute>> attribute = id == null ? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(id);
                if (attribute.isEmpty()) throw new Rejected("oriel.server.item_editor.unknown_attribute", a.get("attribute").getAsString());
                double amount = a.get("amount").getAsDouble();
                if (!Double.isFinite(amount) || Math.abs(amount) > MAX_AMOUNT) throw new Rejected("oriel.server.item_editor.attribute_amount", (int) MAX_AMOUNT);
                AttributeModifier.Operation operation = null;
                for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) if (op.getSerializedName().equals(string(a, "operation"))) operation = op;
                EquipmentSlotGroup group = null;
                for (EquipmentSlotGroup g : EquipmentSlotGroup.values()) if (g.getSerializedName().equals(string(a, "slot"))) group = g;
                if (operation == null || group == null) throw new Rejected("oriel.server.item_editor.invalid_attribute_operation");
                String modifier = string(a, "modifier");
                Identifier modifierId = modifier != null && Identifier.tryParse(modifier) != null ? Identifier.tryParse(modifier)
                        : Identifier.fromNamespaceAndPath(Constants.MOD_ID, "edit_" + n);
                builder.add(attribute.get(), new AttributeModifier(modifierId, amount, operation), group);
                n++;
            }
            next.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        }
        player.getInventory().setItem(slot, next);
        player.containerMenu.broadcastChanges();
        return next;
    }

    private static boolean changed(JsonObject before, JsonObject edit, String key) {
        if (!edit.has(key)) return false;
        JsonElement a = before.get(key), b = edit.get(key);
        if (a == null) a = JsonNull.INSTANCE;
        return !a.equals(b);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
