package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Neutral deterministic mana output, expressed as a real total quantity.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ManaOutput(long amount) implements MachineOutput {
    public static final MapCodec<ManaOutput> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> BotaniaManaIds.MANA.toString()),
            Codec.LONG.fieldOf("amount").forGetter(ManaOutput::amount)
    ).apply(instance, (ignored, amount) -> new ManaOutput(amount)));
    public static final RecipeSyncCodec<ManaOutput> SYNC_CODEC = RecipeSyncCodec.json(CODEC.codec());

    public static final OutputType<ManaOutput> TYPE = new OutputType.Definition<>(
            BotaniaManaIds.MANA, CODEC, ManaOutput::withChance,
            (value, modifiers) -> new ManaOutput(ManaRecipeDeclarations.applyAmount(value.amount(),
                    RecipeModifier.IOType.OUTPUT, modifiers)), value -> value,
            OutputType.Presentation.defaults(BotaniaManaIds.MANA), BotaniaManaIds.MANA.toString(),
            (value, tags) -> new ManaRequirement(RecipeModifier.IOType.OUTPUT, value.amount(), tags),
            value -> value instanceof ManaRequirement mana && mana.io() == RecipeModifier.IOType.OUTPUT,
            value -> value instanceof ManaRequirement mana && mana.io() == RecipeModifier.IOType.OUTPUT
                    ? new ManaOutput(mana.amount()) : null, SYNC_CODEC);

    public ManaOutput {
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
    }

    @Override
    public OutputType<ManaOutput> outputType() { return TYPE; }

    @Override
    public float chance() { return 1F; }

    @Override
    public ManaOutput withChance(float chance) {
        if (chance != 1F) throw new IllegalArgumentException("Mana probability is unsupported");
        return this;
    }
}
