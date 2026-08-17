package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable JEI display metadata for one machine's multiblock structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureDisplay(Machine machine, List<ItemStack> ingredients) {
    public MachineStructureDisplay {
        machine = Objects.requireNonNull(machine, "machine");
        ingredients = List.copyOf(ingredients.stream().map(ItemStack::copy).toList());
    }

    @Override
    public List<ItemStack> ingredients() {
        return ingredients.stream().map(ItemStack::copy).toList();
    }

    public static MachineStructureDisplay from(Machine machine) {
        LinkedHashSet<Item> items = new LinkedHashSet<>();
        items.add(ModBlocks.controllerFor(machine.registryName()).get().asItem());
        return new MachineStructureDisplay(machine, items.stream().map(ItemStack::new).toList());
    }
}
