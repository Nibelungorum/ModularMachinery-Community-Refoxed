package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Preset for a permanent factory recipe thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactoryThreadSpec(String name, List<Identifier> recipeIds) {
    public FactoryThreadSpec {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        recipeIds = List.copyOf(recipeIds == null ? List.of() : recipeIds);
    }
}
