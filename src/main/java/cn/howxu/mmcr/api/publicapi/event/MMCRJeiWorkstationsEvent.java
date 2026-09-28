package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.jei.JeiWorkstationRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client event for adding workstations to MMCR recipe pools or arbitrary JEI recipe types.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRJeiWorkstationsEvent extends Event {
    private final List<JeiWorkstationRegistration> entries = new ArrayList<>();
    private boolean frozen;

    public void addRecipePoolWorkstation(Identifier recipePoolId, ItemLike workstation) {
        Objects.requireNonNull(workstation, "workstation");
        Identifier itemId = BuiltInRegistries.ITEM.getKey(workstation.asItem());
        if (itemId == null) throw new IllegalArgumentException("workstation item is not registered");
        addRecipePoolWorkstation(recipePoolId, itemId);
    }

    public void addRecipePoolWorkstation(Identifier recipePoolId, Identifier itemId) {
        requireOpen();
        entries.add(new JeiWorkstationRegistration.RecipePoolItem(recipePoolId, itemId));
    }

    public void addRecipePoolWorkstation(Identifier recipePoolId, ItemStack workstation) {
        requireOpen();
        entries.add(new JeiWorkstationRegistration.RecipePoolStack(recipePoolId, workstation));
    }

    public void addMachineWorkstation(Identifier machineId, Identifier recipeTypeId) {
        requireOpen();
        entries.add(new JeiWorkstationRegistration.Machine(machineId, recipeTypeId));
    }

    public void freeze() {
        frozen = true;
    }

    public List<JeiWorkstationRegistration> entries() {
        return List.copyOf(entries);
    }

    private void requireOpen() {
        if (frozen) throw new IllegalStateException("JEI workstation registrations are frozen");
    }
}
