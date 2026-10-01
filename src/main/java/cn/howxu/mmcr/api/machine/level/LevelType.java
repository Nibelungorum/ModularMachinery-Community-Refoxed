package cn.howxu.mmcr.api.machine.level;

import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
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
        displayName = ComponentSnapshots.copy(Objects.requireNonNull(displayName, "displayName"));
    }

    @Override
    public Component displayName() {
        return ComponentSnapshots.copy(displayName);
    }
}
