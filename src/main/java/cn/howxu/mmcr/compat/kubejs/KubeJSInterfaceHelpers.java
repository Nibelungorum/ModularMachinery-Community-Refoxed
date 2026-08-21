package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration;
import cn.howxu.mmcr.api.publicapi.machine.ParallelTier;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Script-safe interface predicates and requirement factories.
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSInterfaceHelpers {
    private KubeJSInterfaceHelpers() {
    }

    public static BlockPredicate anyOfItemInput() { return anyOfPort("item_input_bus", "item_input_bus_tiny", "item_input_bus_small", "item_input_bus_reinforced", "item_input_bus_big", "item_input_bus_huge", "item_input_bus_ludicrous"); }
    public static BlockPredicate anyOfItemOutput() { return anyOfPort("item_output_bus", "item_output_bus_tiny", "item_output_bus_small", "item_output_bus_reinforced", "item_output_bus_big", "item_output_bus_huge", "item_output_bus_ludicrous"); }
    public static BlockPredicate anyOfFluidInput() { return anyOfPort("fluid_input_hatch", "fluid_input_hatch_tiny", "fluid_input_hatch_small", "fluid_input_hatch_reinforced", "fluid_input_hatch_big", "fluid_input_hatch_huge", "fluid_input_hatch_ludicrous", "fluid_input_hatch_vacuum"); }
    public static BlockPredicate anyOfFluidOutput() { return anyOfPort("fluid_output_hatch", "fluid_output_hatch_tiny", "fluid_output_hatch_small", "fluid_output_hatch_reinforced", "fluid_output_hatch_big", "fluid_output_hatch_huge", "fluid_output_hatch_ludicrous", "fluid_output_hatch_vacuum"); }
    public static BlockPredicate anyOfEnergyInput() { return anyOfPort("energy_input_hatch", "energy_input_hatch_tiny", "energy_input_hatch_small", "energy_input_hatch_reinforced", "energy_input_hatch_big", "energy_input_hatch_huge", "energy_input_hatch_ludicrous", "energy_input_hatch_ultimate"); }
    public static BlockPredicate anyOfEnergyOutput() { return anyOfPort("energy_output_hatch", "energy_output_hatch_tiny", "energy_output_hatch_small", "energy_output_hatch_reinforced", "energy_output_hatch_big", "energy_output_hatch_huge", "energy_output_hatch_ludicrous", "energy_output_hatch_ultimate"); }

    public static BlockPredicate anyOfPort(String... ids) {
        if (ids == null || ids.length == 0) throw new IllegalArgumentException("At least one port is required");
        List<BlockPredicate> predicates = new ArrayList<>();
        for (String id : ids) predicates.add(port(id));
        return new BlockPredicate.AnyOf(predicates);
    }

    public static BlockPredicate parallelControllers() {
        return anyOfPort(java.util.Arrays.stream(ParallelTier.values()).map(ParallelTier::idSuffix).toArray(String[]::new));
    }

    public static BlockPredicate smartInterface() { return port("smart_interface"); }

    public static BlockPredicate port(String id) {
        return new BlockPredicate.DeferredBlock(PublicBuiltinRegistration.block(id));
    }

    public static BlockPredicate port(Identifier id) {
        return port(id.getPath());
    }

    public static MachineRequirement smartInterfaceInput(String type, float value) {
        return SmartInterfaceRequirement.input(type, value);
    }

    public static MachineRequirement smartInterfaceInput(String type, float min, float max) {
        return SmartInterfaceRequirement.input(type, min, max);
    }

    public static MachineRequirement smartInterfaceOutput(String type, float value) {
        return SmartInterfaceRequirement.output(type, value);
    }

    public static PortTierRequirementSpec itemInputTier(String id) {
        return PortTierRequirementSpec.builder().minItemInput(itemTier(id)).build();
    }

    public static PortTierRequirementSpec itemOutputTier(String id) {
        return PortTierRequirementSpec.builder().minItemOutput(itemTier(id)).build();
    }

    public static PortTierRequirementSpec fluidInputTier(String id) {
        return PortTierRequirementSpec.builder().minFluidInput(fluidTier(id)).build();
    }

    public static PortTierRequirementSpec fluidOutputTier(String id) {
        return PortTierRequirementSpec.builder().minFluidOutput(fluidTier(id)).build();
    }

    public static PortTierRequirementSpec energyInputTier(String id) {
        return PortTierRequirementSpec.builder().minEnergyInput(energyTier(id)).build();
    }

    public static PortTierRequirementSpec energyOutputTier(String id) {
        return PortTierRequirementSpec.builder().minEnergyOutput(energyTier(id)).build();
    }

    private static cn.howxu.mmcr.internal.port.ItemBusSize itemTier(String id) {
        return java.util.Arrays.stream(cn.howxu.mmcr.internal.port.ItemBusSize.values())
                .filter(tier -> tier.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown item tier: " + id));
    }

    private static cn.howxu.mmcr.internal.port.FluidHatchSize fluidTier(String id) {
        return java.util.Arrays.stream(cn.howxu.mmcr.internal.port.FluidHatchSize.values())
                .filter(tier -> tier.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown fluid tier: " + id));
    }

    private static cn.howxu.mmcr.internal.port.EnergyHatchSize energyTier(String id) {
        return java.util.Arrays.stream(cn.howxu.mmcr.internal.port.EnergyHatchSize.values())
                .filter(tier -> tier.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown energy tier: " + id));
    }
}
