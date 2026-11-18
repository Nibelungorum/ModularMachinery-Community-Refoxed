package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Type-tagged recipe IO value passed to JEI adapters.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeIoEntry(RecipeIngredientRole role, ResourceLocation typeId,
                            Object value, long amount, float chance, boolean declaredValues) {
    public RecipeIoEntry(RecipeIngredientRole role, ResourceLocation typeId, Object value, long amount, float chance) {
        this(role, typeId, value, amount, chance, true);
    }

    public RecipeIoEntry(RecipeIngredientRole role, MachineRequirement value) {
        this(role, value.type().id(), value, 0L, 1F, false);
    }
    /** Owner-checked payload access through the addon's public RequirementKind. */
    public Optional<RequirementSpec> requirement() {
        return value instanceof MachineRequirement declaration ? Optional.of(RequirementAdapters.wrap(declaration)) : Optional.empty();
    }

    public RecipeIoEntry {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(value, "value");
        if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException("chance must be in [0, 1]");
        }
    }
}
