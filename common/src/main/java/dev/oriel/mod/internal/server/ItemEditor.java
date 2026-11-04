package dev.oriel.mod.internal.server;

import dev.oriel.mod.server.OrielServer;
import dev.oriel.mod.server.OrielSession;

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
            player.sendSystemMessage(Component.literal("[Oriel] 手里没有物品可以编辑"));
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
                        reply.addProperty("reason", e.getMessage());
                    } catch (RuntimeException e) {
                        reply.addProperty("status", "rejected");
                        reply.addProperty("reason", "数据格式不对");
                        Constants.LOG.warn("Oriel: a malformed item edit from {}", p.getName().getString(), e);
                    }
                    session[0].patch("result", reply);
                });
        return session[0].openStatus() == OrielSession.OpenStatus.SENT;
    }

    /** A refused save; the message is shown to the player. */
    static final class Rejected extends Exception {
        Rejected(String message) {
            super(message);
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
        for (TypedDataComponent<?> c : stack.getComponents()) {
            Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
            if (key == null) continue;
            all.add(key.toString(), c.encodeValue(ops).result().orElse(new com.google.gson.JsonPrimitive("(不可编码)")));
        }
        o.add("components", all);
        return o;
    }

    private static List<Identifier> sorted(java.util.Set<Identifier> ids) {
        List<Identifier> list = new ArrayList<>(ids);
        list.sort((a, b) -> a.toString().compareTo(b.toString()));
        return list;
    }

    // ---- Save ----

    public static ItemStack save(ServerPlayer player, int slot, ItemStack original, JsonObject before, JsonObject edit) throws Rejected {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) throw new Rejected("没有编辑物品的权限");
        if (player.getInventory().getSelectedSlot() != slot) throw new Rejected("你换了快捷栏格子，请重新打开编辑器");
        ItemStack current = player.getInventory().getItem(slot);
        if (!ItemStack.matches(current, original)) throw new Rejected("手里的物品已经变了，请重新打开编辑器");
        ItemStack next = current.copy();

        if (changed(before, edit, "name") || changed(before, edit, "nameColor")) {
            String name = string(edit, "name");
            if (name == null || name.isBlank()) next.remove(DataComponents.CUSTOM_NAME);
            else {
                if (name.length() > MAX_NAME) throw new Rejected("名称最多 " + MAX_NAME + " 个字");
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
            if (rarity == null) throw new Rejected("未知稀有度 " + r);
            next.set(DataComponents.RARITY, rarity);
        }
        if (changed(before, edit, "lore")) {
            JsonArray lines = edit.getAsJsonArray("lore");
            if (lines.size() > MAX_LORE_LINES) throw new Rejected("说明最多 " + MAX_LORE_LINES + " 行");
            List<Component> lore = new ArrayList<>();
            for (JsonElement l : lines) {
                String text = l.getAsString();
                if (text.length() > MAX_LORE_LENGTH) throw new Rejected("每行说明最多 " + MAX_LORE_LENGTH + " 个字");
                lore.add(Component.literal(text).withStyle(Style.EMPTY.withItalic(true).withColor(TextColor.fromRgb(0xaa00aa))));
            }
            if (lore.isEmpty()) next.remove(DataComponents.LORE);
            else next.set(DataComponents.LORE, new ItemLore(lore));
        }
        if (changed(before, edit, "enchantments")) {
            JsonArray list = edit.getAsJsonArray("enchantments");
            if (list.size() > MAX_ENCHANTMENTS) throw new Rejected("附魔最多 " + MAX_ENCHANTMENTS + " 个");
            Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (JsonElement e : list) {
                JsonObject en = e.getAsJsonObject();
                Identifier id = Identifier.tryParse(en.get("id").getAsString());
                Optional<Holder.Reference<Enchantment>> holder = id == null ? Optional.empty() : registry.get(id);
                if (holder.isEmpty()) throw new Rejected("未知附魔 " + en.get("id").getAsString());
                int level = en.get("level").getAsInt();
                if (level < 1 || level > MAX_LEVEL) throw new Rejected("附魔等级要在 1–" + MAX_LEVEL + " 之间");
                enchants.set(holder.get(), level);
            }
            EnchantmentHelper.setEnchantments(next, enchants.toImmutable());
        }
        if (changed(before, edit, "maxDamage") && next.isDamageableItem()) {
            int max = edit.get("maxDamage").getAsInt();
            if (max < 1 || max > MAX_DURABILITY) throw new Rejected("最大耐久要在 1–" + MAX_DURABILITY + " 之间");
            next.set(DataComponents.MAX_DAMAGE, max);
        }
        if (changed(before, edit, "damage") && next.isDamageableItem()) {
            int damage = edit.get("damage").getAsInt();
            if (damage < 0 || damage >= next.getMaxDamage()) throw new Rejected("损耗要在 0–" + (next.getMaxDamage() - 1) + " 之间");
            next.setDamageValue(damage);
        }
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
            if (list.size() > MAX_ATTRIBUTES) throw new Rejected("属性修饰最多 " + MAX_ATTRIBUTES + " 条");
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            int n = 0;
            for (JsonElement e : list) {
                JsonObject a = e.getAsJsonObject();
                Identifier id = Identifier.tryParse(a.get("attribute").getAsString());
                Optional<Holder.Reference<Attribute>> attribute = id == null ? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(id);
                if (attribute.isEmpty()) throw new Rejected("未知属性 " + a.get("attribute").getAsString());
                double amount = a.get("amount").getAsDouble();
                if (!Double.isFinite(amount) || Math.abs(amount) > MAX_AMOUNT) throw new Rejected("属性数值要在 ±" + (int) MAX_AMOUNT + " 之内");
                AttributeModifier.Operation operation = null;
                for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) if (op.getSerializedName().equals(string(a, "operation"))) operation = op;
                EquipmentSlotGroup group = null;
                for (EquipmentSlotGroup g : EquipmentSlotGroup.values()) if (g.getSerializedName().equals(string(a, "slot"))) group = g;
                if (operation == null || group == null) throw new Rejected("属性的运算或部位不对");
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
