package cn.howxu.mmcr.client.recipe;

import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/** Viewer-independent resource candidates resolved when the display snapshot is built.
 * @author howxu <dev@howxu.cn>
 */
public record RecipeDisplayEntry(IOType io, ResourceLocation typeId, Kind kind,
                                 MachineRequirement requirement, long amount, float chance,
                                 List<ItemStack> items, List<FluidStack> fluids,
                                 List<ResourceLocation> chemicals, DataComponentPredicateSet components,
                                 boolean exactComponents, List<Component> tooltip) {
    public enum Kind { ITEM, FLUID, CHEMICAL, SOURCE, TEXT }

    public RecipeDisplayEntry {
        items = items.stream().map(ItemStack::copy).toList();
        fluids = fluids.stream().map(FluidStack::copy).toList();
        chemicals = List.copyOf(chemicals);
        components = components == null ? DataComponentPredicateSet.EMPTY : components;
        tooltip = tooltip.stream().map(ComponentSnapshots::copy).toList();
        requirement = MachineRequirement.copyOf(requirement);
    }

    @Override public MachineRequirement requirement() { return MachineRequirement.copyOf(requirement); }
    @Override public List<ItemStack> items() { return items.stream().map(ItemStack::copy).toList(); }
    @Override public List<FluidStack> fluids() { return fluids.stream().map(FluidStack::copy).toList(); }
    @Override public List<Component> tooltip() { return tooltip.stream().map(ComponentSnapshots::copy).toList(); }
}
