package dev.oriel.mod.client;

import dev.oriel.kit.GameEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.locale.Language;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client-thread bridge from the local player's authoritative effects to the MC-independent kit. */
public final class PlayerEffects {
    private static GameEffects.Snapshot snapshot = new GameEffects.Snapshot(List.of());
    private static List<State> previous = List.of();
    private static Language language;

    private record State(MobEffect effect, int level, int seconds, boolean ambient, boolean visible, boolean showIcon) {}

    private PlayerEffects() {}

    public static void init() {
        GameEffects.provide(() -> snapshot);
    }

    /** Checked at 20 Hz, never per rendered frame; unchanged snapshots are reused by every open effects page. */
    public static void tick() {
        var player = Minecraft.getInstance().player;
        List<State> states = new ArrayList<>();
        if (player != null) for (MobEffectInstance instance : player.getActiveEffects()) {
            int seconds = instance.isInfiniteDuration() ? -1 : (int) Math.ceil(instance.getDuration() / 20.0);
            states.add(new State(instance.getEffect().value(), instance.getAmplifier() + 1, seconds,
                    instance.isAmbient(), instance.isVisible(), instance.showIcon()));
        }
        states.sort(Comparator.comparing(s -> s.effect().getDescriptionId()));
        Language currentLanguage = Language.getInstance();
        if (states.equals(previous) && language == currentLanguage) return;
        previous = states;
        language = currentLanguage;
        List<GameEffects.Entry> next = new ArrayList<>();
        for (State state : states) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(state.effect());
            next.add(new GameEffects.Entry(id.toString(), state.effect().getDisplayName().getString(), state.level(),
                    GameEffects.Kind.valueOf(state.effect().getCategory().name()), state.seconds(),
                    id.getNamespace() + ":textures/mob_effect/" + id.getPath() + ".png", state.ambient(), state.visible(), state.showIcon()));
        }
        next.sort(Comparator.comparing(GameEffects.Entry::id));
        if (!next.equals(snapshot.effects())) snapshot = new GameEffects.Snapshot(next);
    }

    public static boolean hasEffects() { return !snapshot.effects().isEmpty(); }
}
