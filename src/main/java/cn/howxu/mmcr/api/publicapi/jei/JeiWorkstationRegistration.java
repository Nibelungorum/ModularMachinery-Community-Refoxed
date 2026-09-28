package cn.howxu.mmcr.api.publicapi.jei;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * A workstation association contributed to an MMCR or JEI recipe category.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface JeiWorkstationRegistration {

    record RecipePoolItem(Identifier recipePoolId, Identifier itemId) implements JeiWorkstationRegistration {
        public RecipePoolItem {
            Objects.requireNonNull(recipePoolId, "recipePoolId");
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    record RecipePoolStack(Identifier recipePoolId, ItemStack workstation) implements JeiWorkstationRegistration {
        public RecipePoolStack {
            Objects.requireNonNull(recipePoolId, "recipePoolId");
            Objects.requireNonNull(workstation, "workstation");
            if (workstation.isEmpty()) throw new IllegalArgumentException("workstation must not be empty");
            workstation = workstation.copyWithCount(1);
        }

        @Override
        public ItemStack workstation() {
            return workstation.copy();
        }
    }

    record Machine(Identifier machineId, Identifier recipeTypeId) implements JeiWorkstationRegistration {
        public Machine {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(recipeTypeId, "recipeTypeId");
        }
    }
}
