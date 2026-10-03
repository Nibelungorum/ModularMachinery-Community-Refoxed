package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.util.ReadableNumber;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.network.chat.Component;

/**
 * Recipe-only source ingredient retaining the exact amount and IO direction.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SourceJeiIngredient(long amount, boolean input) {
    public static final IIngredientType<SourceJeiIngredient> TYPE = () -> SourceJeiIngredient.class;
    public static final Codec<SourceJeiIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("amount", 1L).forGetter(SourceJeiIngredient::amount),
            Codec.BOOL.optionalFieldOf("input", true).forGetter(SourceJeiIngredient::input)
    ).apply(instance, SourceJeiIngredient::new));

    public SourceJeiIngredient {
        if (amount <= 0L) throw new IllegalArgumentException("Source amount must be positive");
    }

    public Component tooltip() {
        return Component.translatable(input
                ? "jei.mmcr.machine_recipe.source_input" : "jei.mmcr.machine_recipe.source_output",
                ReadableNumber.formatExact(amount));
    }
}
