package cn.howxu.mmcr.api.publicapi.recipe;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable public machine level requirement.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelRequirement(RecipeIo io, ResourceLocation typeId, ResourceLocation levelId)
        implements RecipeRequirement {
    public LevelRequirement(ResourceLocation typeId, ResourceLocation levelId) {
        this(RecipeIo.INPUT, typeId, levelId);
    }

    public LevelRequirement {
        Objects.requireNonNull(io, "io");
        if (io != RecipeIo.INPUT) {
            throw new IllegalArgumentException("Level requirements must use input direction");
        }
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(levelId, "levelId");
    }
}
