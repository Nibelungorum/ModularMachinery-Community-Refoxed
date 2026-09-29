package cn.howxu.mmcr.api.compat.mekanism;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Mekanism-neutral chemical ingredient declaration.
 *
 * @param kind whether the identifier names a chemical or a tag
 * @param id the chemical or tag identifier
 * @param amount the required amount
 * @author howxu <dev@howxu.cn>
 */
public record ChemicalIngredient(Kind kind, ResourceLocation id, long amount) {
    public ChemicalIngredient {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
    }

    public enum Kind {
        CHEMICAL,
        TAG
    }

    public static ChemicalIngredient chemical(ResourceLocation id, long amount) {
        return new ChemicalIngredient(Kind.CHEMICAL, id, amount);
    }

    public static ChemicalIngredient tag(ResourceLocation id, long amount) {
        return new ChemicalIngredient(Kind.TAG, id, amount);
    }
}
