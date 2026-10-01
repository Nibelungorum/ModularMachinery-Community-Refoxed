package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * @author howxu <dev@howxu.cn>
 */
public record StageRequirement(RecipeModifier.IOType io, int minStage) implements MachineRequirement {
    private static final ResourceLocation TYPE_ID = MMCR.id("stage");
    public static final MapCodec<StageRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.fieldOf("io").orElse(RecipeModifier.IOType.INPUT)
                    .forGetter(StageRequirement::io),
            Codec.INT.fieldOf("min_stage").forGetter(StageRequirement::minStage)
    ).apply(instance, (ignored, io, minStage) -> new StageRequirement(io, minStage)));
    private static final RequirementHandler<StageRequirement> HANDLER = new StageRequirementHandler();
    public static final RequirementType<StageRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, StageRequirement::copy);

    public StageRequirement {
        if (io != RecipeModifier.IOType.INPUT) {
            throw new IllegalArgumentException("Stage requirements must use input direction");
        }
        if (minStage < 1 || minStage > 64) throw new IllegalArgumentException("Stage minimum must be in [1, 64]");
    }

    public static StageRequirement input(int minStage) {
        return new StageRequirement(RecipeModifier.IOType.INPUT, minStage);
    }

    public StageRequirement(int minStage) {
        this(RecipeModifier.IOType.INPUT, minStage);
    }

    private static StageRequirement copy(StageRequirement requirement) {
        return input(requirement.minStage());
    }

    @Override
    public RequirementType<StageRequirement> type() {
        return TYPE;
    }
}
