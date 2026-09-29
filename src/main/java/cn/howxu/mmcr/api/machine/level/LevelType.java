package cn.howxu.mmcr.api.machine.level;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Describes a category of machine levels.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelType(ResourceLocation id, Component displayName) {
    public LevelType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
