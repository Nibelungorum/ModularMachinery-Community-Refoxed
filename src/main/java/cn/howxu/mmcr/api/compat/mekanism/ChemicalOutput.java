package cn.howxu.mmcr.api.compat.mekanism;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Mekanism-neutral chemical output declaration.
 *
 * @param id the chemical identifier
 * @param amount the produced amount
 * @param chance the production chance
 * @author howxu <dev@howxu.cn>
 */
public record ChemicalOutput(ResourceLocation id, long amount, float chance) {
    public ChemicalOutput {
        Objects.requireNonNull(id, "id");
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
    }

    public static ChemicalOutput of(ResourceLocation id, long amount, float chance) {
        return new ChemicalOutput(id, amount, chance);
    }
}
