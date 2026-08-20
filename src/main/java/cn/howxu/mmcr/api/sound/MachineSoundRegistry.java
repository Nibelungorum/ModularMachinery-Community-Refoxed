package cn.howxu.mmcr.api.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/** Resolves machine sound references from the finalized sound registry.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineSoundRegistry {
    private MachineSoundRegistry() {
    }

    public static @Nullable SoundEvent get(@Nullable Identifier id) {
        return id == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(id);
    }
}
