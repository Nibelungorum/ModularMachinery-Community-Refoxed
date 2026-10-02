package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Neutral deterministic source output, expressed as a real total quantity.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SourceOutput(long amount) implements MachineOutput {
    public static final MapCodec<SourceOutput> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> ArsSourceIds.SOURCE.toString()),
            Codec.LONG.fieldOf("amount").forGetter(SourceOutput::amount)
    ).apply(instance, (ignored, amount) -> new SourceOutput(amount)));

    public static final OutputType<SourceOutput> TYPE = new OutputType.Definition<>(
            ArsSourceIds.SOURCE, CODEC, SourceOutput::withChance,
            (value, modifiers) -> new SourceOutput(SourceRecipeDeclarations.applyAmount(value.amount(),
                    RecipeModifier.IOType.OUTPUT, modifiers)), value -> value,
            OutputType.Presentation.defaults(ArsSourceIds.SOURCE), ArsSourceIds.SOURCE.toString(),
            (value, tags) -> new SourceRequirement(RecipeModifier.IOType.OUTPUT, value.amount(), tags),
            value -> value instanceof SourceRequirement source && source.io() == RecipeModifier.IOType.OUTPUT,
            value -> value instanceof SourceRequirement source && source.io() == RecipeModifier.IOType.OUTPUT
                    ? new SourceOutput(source.amount()) : null, RecipeSyncCodec.json(CODEC.codec()));

    public SourceOutput {
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
    }

    @Override
    public OutputType<SourceOutput> outputType() {
        return TYPE;
    }

    @Override
    public float chance() {
        return 1F;
    }

    @Override
    public SourceOutput withChance(float chance) {
        if (chance != 1F) throw new IllegalArgumentException("Source probability is unsupported");
        return this;
    }
}
