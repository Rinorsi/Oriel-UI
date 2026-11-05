package dev.oriel.mod.internal.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import dev.oriel.mod.internal.registry.OrielRegistry;
import dev.oriel.mod.internal.server.ItemEditor;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Registered extension codecs and deliberately broken/transient components, only installed in test runs. */
public final class ItemEditorComponentsGameTests {
    private static DataComponentType<String> encoded, broken, transientValue;

    private ItemEditorComponentsGameTests() {}

    public static void init() {
        if (Boolean.getBoolean("oriel.componentCodecFixtures")) {
            encoded = DataComponentType.<String>builder().persistent(Codec.STRING).build();
            OrielRegistry.add(Registries.DATA_COMPONENT_TYPE, "test_codec", () -> encoded);
            broken = DataComponentType.<String>builder().persistent(Codec.STRING.xmap(v -> v, v -> {
                        throw new IllegalStateException("deliberately broken extension encoder");
                    })).build();
            OrielRegistry.add(Registries.DATA_COMPONENT_TYPE, "test_broken_codec", () -> broken);
            transientValue = DataComponentType.<String>builder().networkSynchronized(ByteBufCodecs.STRING_UTF8).build();
            OrielRegistry.add(Registries.DATA_COMPONENT_TYPE, "test_transient", () -> transientValue);
        }
        GameTests.add("item_editor_components", ItemEditorComponentsGameTests::run);
        GameTests.add("item_editor_damage_atomicity", ItemEditorComponentsGameTests::damageAtomicity);
    }

    private static void damageAtomicity(GameTestHelper h) {
        OrielGameTests.withPlayer(h, (p, packet) -> {}, player -> {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            stack.setDamageValue(100);
            int slot = player.getInventory().getSelectedSlot();
            player.getInventory().setItem(slot, stack);
            var snapshot = ItemEditor.describe(player, stack);
            ItemStack before = stack.copy();
            var players = h.getLevel().getServer().getPlayerList();
            NameAndId identity = new NameAndId(player.getGameProfile());
            players.op(identity, java.util.Optional.of(LevelBasedPermissionSet.GAMEMASTER), java.util.Optional.of(false));
            try {
                var edit = JsonParser.parseString("{\"name\":\"must-not-commit\",\"maxDamage\":50}").getAsJsonObject();
                boolean rejected = false;
                try { ItemEditor.save(player, slot, before, snapshot, edit); }
                catch (ItemEditor.Rejected expected) { rejected = true; }
                h.assertTrue(rejected, "lowering max damage below existing damage must reject");
                h.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "late durability validation is atomic");
            } finally { players.deop(identity); }
        });
        h.succeed();
    }

    private static void run(GameTestHelper h) {
        h.assertTrue(encoded != null, "GameTest launcher must enable component fixtures");
        OrielGameTests.withPlayer(h, (p, packet) -> {}, player -> {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            stack.setDamageValue(100);
            stack.set(encoded, "extension-value");
            stack.set(broken, "keep-broken");
            stack.set(transientValue, "keep-transient");
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("marker", "keep-custom-data");
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            int slot = player.getInventory().getSelectedSlot();
            player.getInventory().setItem(slot, stack);
            ItemStack before = stack.copy();
            ItemStack persistent = stack.copy();
            persistent.remove(broken);
            persistent.remove(transientValue);
            var ops = player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var exported = ItemStack.CODEC.encodeStart(ops, persistent).getOrThrow();
            var imported = ItemStack.CODEC.parse(ops, JsonParser.parseString(exported.toString())).getOrThrow();
            h.assertTrue(ItemStack.matches(persistent, imported), "registered extension codec survives complete item JSON round trip");
            JsonObject snapshot = JsonParser.parseString(ItemEditor.describe(player, stack).toString()).getAsJsonObject();
            h.assertValueEqual(snapshot.getAsJsonObject("components").get("oriel:test_codec").getAsString(),
                    "extension-value", "registered component codec supplies JSON");
            h.assertTrue(snapshot.getAsJsonArray("unencodableComponents").contains(new com.google.gson.JsonPrimitive("oriel:test_broken_codec")),
                    "throwing codec has an explicit limitation");
            h.assertTrue(snapshot.getAsJsonArray("unencodableComponents").contains(new com.google.gson.JsonPrimitive("oriel:test_transient")),
                    "nonpersistent component has an explicit limitation");
            var players = h.getLevel().getServer().getPlayerList();
            NameAndId identity = new NameAndId(player.getGameProfile());
            players.op(identity, java.util.Optional.of(LevelBasedPermissionSet.GAMEMASTER), java.util.Optional.of(false));
            try {
                ItemStack unchanged = ItemEditor.save(player, slot, before, snapshot, snapshot.deepCopy());
                h.assertTrue(ItemStack.matches(before, unchanged), "snapshot JSON round trip preserves all components");
                JsonObject invalid = new JsonObject();
                invalid.addProperty("name", "must-not-commit");
                invalid.addProperty("maxDamage", 50);
                boolean rejected = false;
                try { ItemEditor.save(player, slot, before, snapshot, invalid); }
                catch (ItemEditor.Rejected expected) { rejected = true; }
                h.assertTrue(rejected, "lowering max damage below existing damage must reject");
                h.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "late validation failure is atomic");
                JsonObject edit = JsonParser.parseString("{\"name\":\"imported-name\"}").getAsJsonObject();
                JsonObject tampered = new JsonObject();
                tampered.addProperty("oriel:test_codec", "must-not-overwrite");
                edit.add("components", tampered);
                var payload = new dev.oriel.mod.internal.net.MessagePayload(1, "save", edit.toString());
                var buffer = io.netty.buffer.Unpooled.buffer();
                try {
                    dev.oriel.mod.internal.net.MessagePayload.STREAM_CODEC.encode(buffer, payload);
                    payload = dev.oriel.mod.internal.net.MessagePayload.STREAM_CODEC.decode(buffer);
                    h.assertValueEqual(buffer.readableBytes(), 0, "import request codec consumes all bytes");
                } finally { buffer.release(); }
                edit = JsonParser.parseString(payload.json()).getAsJsonObject();
                ItemStack saved = ItemEditor.save(player, slot, before, snapshot, edit);
                h.assertValueEqual(saved.get(encoded), "extension-value", "unmanaged codec component survives edits");
                h.assertValueEqual(saved.get(broken), "keep-broken", "broken codec value survives edits");
                h.assertValueEqual(saved.get(transientValue), "keep-transient", "nonpersistent component survives edits");
                h.assertValueEqual(saved.get(DataComponents.CUSTOM_DATA), before.get(DataComponents.CUSTOM_DATA), "custom data survives edits");
                h.assertValueEqual(ItemEditor.describe(player, saved).get("name").getAsString(), "imported-name", "saved data can be exported again");
            } catch (ItemEditor.Rejected failure) { throw new IllegalStateException(failure); }
            finally { players.deop(identity); }
        });
        h.succeed();
    }
}
