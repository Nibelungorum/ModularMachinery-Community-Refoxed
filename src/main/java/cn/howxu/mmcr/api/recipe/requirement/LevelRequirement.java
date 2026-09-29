package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * @author howxu <dev@howxu.cn>
 */
public record LevelRequirement(RecipeModifier.IOType io, ResourceLocation typeId, ResourceLocation levelId)
        implements MachineRequirement {
    private static final ResourceLocation TYPE_ID = MMCR.id("level");
    public static final MapCodec<LevelRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(LevelRequirement::io),
            ResourceLocation.CODEC.fieldOf("level_type").forGetter(LevelRequirement::typeId),
            ResourceLocation.CODEC.fieldOf("level").forGetter(LevelRequirement::levelId)
    ).apply(instance, (ignored, io, typeId, levelId) ->
            new LevelRequirement(io, typeId, levelId)));
    private static final RequirementHandler<LevelRequirement> HANDLER = new LevelRequirementHandler();
    public static final RequirementType<LevelRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, LevelRequirement::copy);

    public LevelRequirement {
        if (io != RecipeModifier.IOType.INPUT) {
            throw new IllegalArgumentException("Level requirements must use input direction");
        }
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(levelId, "levelId");
    }

    public static LevelRequirement input(ResourceLocation typeId, ResourceLocation levelId) {
        return new LevelRequirement(RecipeModifier.IOType.INPUT, typeId, levelId);
    }

    private static LevelRequirement copy(LevelRequirement requirement) {
        return input(requirement.typeId(), requirement.levelId());
    }

    @Override
    public RequirementType<LevelRequirement> type() {
        return TYPE;
    }
}
