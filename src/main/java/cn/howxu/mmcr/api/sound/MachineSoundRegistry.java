package cn.howxu.mmcr.api.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects machine sound event declarations before NeoForge registry events fire.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineSoundRegistry {
    private static final Set<Identifier> REQUESTED_IDS = new LinkedHashSet<>();

    private MachineSoundRegistry() {
    }

    public static synchronized void requestRegistration(Identifier id) {
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            throw new IllegalStateException("Sound event already registered: " + id);
        }
        REQUESTED_IDS.add(id);
    }

    public static void onRegister(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> requestedIds().forEach(
                id -> helper.register(id, SoundEvent.createVariableRangeEvent(id))));
    }

    public static @Nullable SoundEvent get(@Nullable Identifier id) {
        return id == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(id);
    }

    static synchronized Set<Identifier> requestedIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(REQUESTED_IDS));
    }

    static synchronized void registered(Identifier id, SoundEvent soundEvent) {
        if (REQUESTED_IDS.contains(id)) {
            throw new IllegalStateException("Sound event conflicts with pending machine sound: " + id);
        }
    }

    static synchronized void resetForTesting() {
        REQUESTED_IDS.clear();
    }
}
