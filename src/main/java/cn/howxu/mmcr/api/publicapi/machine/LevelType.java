package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Public declaration of a machine level type.
 * @author howxu <dev@howxu.cn>
 */
public record LevelType(ResourceLocation id, Component displayName) {
    public LevelType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
