package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Internal chemical output payload for the optional bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LoadedChemicalOutput(ResourceLocation id, long amount, float chance) implements MachineOutput {
    public static final OutputType<LoadedChemicalOutput> TYPE = new OutputType.Definition<>(
            MekanismRecipeTypes.CHEMICAL, codec(), LoadedChemicalOutput::withChance,
            (output, modifiers) -> new LoadedChemicalOutput(output.id(),
                    IntegrationTypeHelper.applyChemical(modifiers, output.amount(), RecipeModifier.IOType.OUTPUT),
                    IntegrationTypeHelper.applyChemicalOutputChance(modifiers, output.chance())),
            output -> output, OutputType.Presentation.defaults(MekanismRecipeTypes.CHEMICAL),
            MekanismRecipeTypes.CHEMICAL.toString(),
            (output, tags) -> LoadedChemicalRequirement.output(output.id(), output.amount(), output.chance()),
            requirement -> requirement instanceof LoadedChemicalRequirement chemical
                    && chemical.io() == RecipeModifier.IOType.OUTPUT,
            requirement -> requirement instanceof LoadedChemicalRequirement chemical
                    && chemical.io() == RecipeModifier.IOType.OUTPUT
                    ? new LoadedChemicalOutput(chemical.ingredient().id(), chemical.ingredient().amount(), chemical.chance())
                    : null,
            RecipeSyncCodec.json(codec().codec()));

    public LoadedChemicalOutput {
        Objects.requireNonNull(id, "id");
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
    }

    @Override
    public OutputType<LoadedChemicalOutput> outputType() {
        return TYPE;
    }

    @Override
    public LoadedChemicalOutput withChance(float chance) {
        return new LoadedChemicalOutput(id, amount, chance);
    }

    @Override
    public LoadedChemicalOutput applyModifiers(List<RecipeModifier> modifiers) {
        return TYPE.applyModifiers(this, modifiers);
    }

    private static MapCodec<LoadedChemicalOutput> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ignored -> MekanismRecipeTypes.CHEMICAL.toString()),
                ResourceLocation.CODEC.fieldOf("id").forGetter(LoadedChemicalOutput::id),
                Codec.LONG.fieldOf("amount").forGetter(LoadedChemicalOutput::amount),
                Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(LoadedChemicalOutput::chance)
        ).apply(instance, (ignored, id, amount, chance) -> new LoadedChemicalOutput(id, amount, chance)));
    }
}
