package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Neutral source output, expressed as a real total quantity.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SourceOutput(long amount, float chance) implements MachineOutput {
    public static final MapCodec<SourceOutput> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> ArsSourceIds.SOURCE.toString()),
            Codec.LONG.fieldOf("amount").forGetter(SourceOutput::amount),
            Codec.floatRange(0F, 1F).optionalFieldOf("chance", 1F).forGetter(SourceOutput::chance)
    ).apply(instance, (ignored, amount, chance) -> new SourceOutput(amount, chance)));

    public static final OutputType<SourceOutput> TYPE = new OutputType.Definition<>(
            ArsSourceIds.SOURCE, CODEC, SourceOutput::withChance,
            (value, modifiers) -> new SourceOutput(SourceRecipeDeclarations.applyAmount(value.amount(),
                    RecipeModifier.IOType.OUTPUT, modifiers), SourceRecipeDeclarations.applyChance(value.chance(),
                    RecipeModifier.IOType.OUTPUT, modifiers)), value -> value,
            OutputType.Presentation.defaults(ArsSourceIds.SOURCE), ArsSourceIds.SOURCE.toString(),
            (value, tags) -> new SourceRequirement(RecipeModifier.IOType.OUTPUT, value.amount(), value.chance(), 1F, tags),
            value -> value instanceof SourceRequirement source && source.io() == RecipeModifier.IOType.OUTPUT,
            value -> value instanceof SourceRequirement source && source.io() == RecipeModifier.IOType.OUTPUT
                    ? new SourceOutput(source.amount(), source.chance()) : null, RecipeSyncCodec.json(CODEC.codec()));

    public SourceOutput(long amount) {
        this(amount, 1F);
    }

    public SourceOutput {
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
    }

    @Override
    public OutputType<SourceOutput> outputType() {
        return TYPE;
    }

    @Override
    public SourceOutput withChance(float chance) {
        return this.chance == chance ? this : new SourceOutput(amount, chance);
    }
}
