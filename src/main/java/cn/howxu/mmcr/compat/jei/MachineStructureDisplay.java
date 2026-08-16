package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable JEI display data for one machine's resolved multiblock structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureDisplay(Machine machine, StructurePreviewSchema schema, List<ItemStack> ingredients) {
    public MachineStructureDisplay {
        machine = Objects.requireNonNull(machine, "machine");
        schema = Objects.requireNonNull(schema, "schema");
        ingredients = List.copyOf(ingredients.stream().map(ItemStack::copy).toList());
    }

    @Override
    public List<ItemStack> ingredients() {
        return ingredients.stream().map(ItemStack::copy).toList();
    }

    public static MachineStructureDisplay from(Machine machine) {
        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);
        LinkedHashSet<Item> items = new LinkedHashSet<>();
        items.add(ModBlocks.controllerFor(machine.registryName()).get().asItem());
        schema.states().values().stream()
                .map(state -> state.getBlock().asItem())
                .filter(item -> item != Items.AIR)
                .forEach(items::add);
        return new MachineStructureDisplay(machine, schema, items.stream().map(ItemStack::new).toList());
    }
}
