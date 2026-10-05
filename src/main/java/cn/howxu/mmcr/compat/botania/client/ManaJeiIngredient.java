package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.util.ReadableNumber;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.network.chat.Component;

/** Neutral recipe resource retaining exact long totals and IO direction.
 * @author howxu <dev@howxu.cn>
 */
public record ManaJeiIngredient(long amount, boolean input) {
    public static final IIngredientType<ManaJeiIngredient> TYPE = () -> ManaJeiIngredient.class;
    public static final Codec<ManaJeiIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.validate(amount -> amount > 0 ? DataResult.success(amount)
                    : DataResult.error(() -> "Mana amount must be positive"))
                    .fieldOf("amount").forGetter(ManaJeiIngredient::amount),
            Codec.BOOL.optionalFieldOf("input", true).forGetter(ManaJeiIngredient::input)
    ).apply(instance, ManaJeiIngredient::new));

    public ManaJeiIngredient {
        if (amount <= 0L) throw new IllegalArgumentException("Mana amount must be positive");
    }

    public Component tooltip() {
        return Component.translatable(input ? "jei.mmcr.machine_recipe.mana_input"
                : "jei.mmcr.machine_recipe.mana_output", ReadableNumber.formatExact(amount));
    }
}
