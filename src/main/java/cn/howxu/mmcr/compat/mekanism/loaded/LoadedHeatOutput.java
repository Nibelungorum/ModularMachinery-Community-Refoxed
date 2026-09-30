package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Internal heat output payload for the optional bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LoadedHeatOutput(double heat) implements MachineOutput {
    public static final OutputType<LoadedHeatOutput> TYPE = new OutputType.Definition<>(
            MekanismRecipeTypes.HEAT, codec(), (output, ignored) -> output,
            (output, modifiers) -> new LoadedHeatOutput(IntegrationTypeHelper.applyHeat(
                    modifiers, output.heat(), RecipeModifier.IOType.OUTPUT)), output -> output,
            OutputType.Presentation.defaults(MekanismRecipeTypes.HEAT), MekanismRecipeTypes.HEAT.toString(),
            (output, tags) -> LoadedHeatRequirement.outputHeat(output.heat()),
            requirement -> requirement instanceof LoadedHeatRequirement heat
                    && heat.type() == LoadedHeatRequirement.HEAT_TYPE,
            requirement -> requirement instanceof LoadedHeatRequirement heat
                    && heat.type() == LoadedHeatRequirement.HEAT_TYPE
                    ? new LoadedHeatOutput(heat.heat().value()) : null,
            RecipeSyncCodec.json(codec().codec()));

    public LoadedHeatOutput {
        if (!Double.isFinite(heat) || heat < 0D) throw new IllegalArgumentException("heat must be non-negative");
    }

    @Override
    public OutputType<LoadedHeatOutput> outputType() {
        return TYPE;
    }

    @Override
    public float chance() {
        return 1F;
    }

    @Override
    public LoadedHeatOutput withChance(float chance) {
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
        return this;
    }

    @Override
    public LoadedHeatOutput applyModifiers(List<RecipeModifier> modifiers) {
        return TYPE.applyModifiers(this, modifiers);
    }

    private static MapCodec<LoadedHeatOutput> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ignored -> MekanismRecipeTypes.HEAT.toString()),
                Codec.STRING.optionalFieldOf("io", RecipeModifier.IOType.OUTPUT.getKey()).forGetter(ignored -> "output"),
                Codec.DOUBLE.fieldOf("value").forGetter(LoadedHeatOutput::heat)
        ).apply(instance, (ignored, io, heat) -> new LoadedHeatOutput(heat)));
    }
}
