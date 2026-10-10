package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.api.machine.definition.InterfaceTiers;
import net.minecraft.resources.ResourceLocation;

/** Script-safe interface predicates and requirement factories.
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSInterfaceHelpers {
    private KubeJSInterfaceHelpers() {
    }

    public static BlockPredicate anyOfItemInput() { return convert(InterfacePredicates.anyOfItemInput()); }
    public static BlockPredicate anyOfItemOutput() { return convert(InterfacePredicates.anyOfItemOutput()); }
    public static BlockPredicate anyOfFluidInput() { return convert(InterfacePredicates.anyOfFluidInput()); }
    public static BlockPredicate anyOfFluidOutput() { return convert(InterfacePredicates.anyOfFluidOutput()); }
    public static BlockPredicate anyOfEnergyInput() { return convert(InterfacePredicates.anyOfEnergyInput()); }
    public static BlockPredicate anyOfEnergyOutput() { return convert(InterfacePredicates.anyOfEnergyOutput()); }
    public static BlockPredicate anyOfSourceInput() { return convert(InterfacePredicates.anyOfSourceInput()); }
    public static BlockPredicate anyOfSourceOutput() { return convert(InterfacePredicates.anyOfSourceOutput()); }
    public static BlockPredicate anyOfSourcePorts() { return convert(InterfacePredicates.anyOfSourcePorts()); }
    public static BlockPredicate anySourceInput() { return anyOfSourceInput(); }
    public static BlockPredicate anySourceOutput() { return anyOfSourceOutput(); }
    public static BlockPredicate anySourcePorts() { return anyOfSourcePorts(); }
    public static BlockPredicate anyOfManaInput() { return convert(InterfacePredicates.anyOfManaInput()); }
    public static BlockPredicate anyOfManaOutput() { return convert(InterfacePredicates.anyOfManaOutput()); }
    public static BlockPredicate anyOfManaPorts() { return convert(InterfacePredicates.anyOfManaPorts()); }
    public static BlockPredicate anyManaInput() { return anyOfManaInput(); }
    public static BlockPredicate anyManaOutput() { return anyOfManaOutput(); }
    public static BlockPredicate anyManaPorts() { return anyOfManaPorts(); }
    public static BlockPredicate anyOfItemPorts() { return convert(InterfacePredicates.anyOfItemPorts()); }
    public static BlockPredicate anyOfFluidPorts() { return convert(InterfacePredicates.anyOfFluidPorts()); }
    public static BlockPredicate anyOfEnergyPorts() { return convert(InterfacePredicates.anyOfEnergyPorts()); }
    public static BlockPredicate anyOfChemicalInput() { return convert(InterfacePredicates.anyOfChemicalInput()); }
    public static BlockPredicate anyOfChemicalOutput() { return convert(InterfacePredicates.anyOfChemicalOutput()); }
    public static BlockPredicate anyOfChemicalPorts() { return convert(InterfacePredicates.anyOfChemicalPorts()); }
    public static BlockPredicate anyOfRadioactiveChemicalInput() {
        return convert(InterfacePredicates.anyOfRadioactiveChemicalInput());
    }
    public static BlockPredicate anyOfRadioactiveChemicalOutput() {
        return convert(InterfacePredicates.anyOfRadioactiveChemicalOutput());
    }
    public static BlockPredicate anyOfRadioactiveChemicalPorts() {
        return convert(InterfacePredicates.anyOfRadioactiveChemicalPorts());
    }
    public static BlockPredicate anyOfHeatInput() { return convert(InterfacePredicates.anyOfHeatInput()); }
    public static BlockPredicate anyOfHeatOutput() { return convert(InterfacePredicates.anyOfHeatOutput()); }
    public static BlockPredicate anyOfHeatPorts() { return convert(InterfacePredicates.anyOfHeatPorts()); }
    public static BlockPredicate anyOfStressInput() { return convert(InterfacePredicates.anyOfStressInput()); }
    public static BlockPredicate anyOfStressOutput() { return convert(InterfacePredicates.anyOfStressOutput()); }
    public static BlockPredicate anyOfStressPorts() { return convert(InterfacePredicates.anyOfStressPorts()); }
    public static BlockPredicate anyStressInput() { return anyOfStressInput(); }
    public static BlockPredicate anyStressOutput() { return anyOfStressOutput(); }
    public static BlockPredicate anyStressPorts() { return anyOfStressPorts(); }
    public static BlockPredicate anyOfAirInput() { return convert(InterfacePredicates.anyOfAirInput()); }
    public static BlockPredicate anyOfAirOutput() { return convert(InterfacePredicates.anyOfAirOutput()); }
    public static BlockPredicate anyOfAirPorts() { return convert(InterfacePredicates.anyOfAirPorts()); }
    public static BlockPredicate anyAirInput() { return anyOfAirInput(); }
    public static BlockPredicate anyAirOutput() { return anyOfAirOutput(); }
    public static BlockPredicate anyAirPorts() { return anyOfAirPorts(); }
    public static BlockPredicate anyOfUpgradeBus() { return convert(InterfacePredicates.anyOfUpgradeBus()); }

    public static BlockPredicate anyOfPort(String... ids) {
        if (ids == null || ids.length == 0) throw new IllegalArgumentException("At least one port is required");
        return convert(InterfacePredicates.anyOfPort(ids));
    }

    /**
     * Matches every registered built-in and available compatibility port family.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static BlockPredicate ports() {
        return convert(InterfacePredicates.ports());
    }

    public static BlockPredicate anyOfPort() {
        throw new IllegalArgumentException("At least one port is required");
    }

    public static BlockPredicate anyOfPort(ResourceLocation... ids) {
        return convert(InterfacePredicates.anyOfPort(ids));
    }

    public static BlockPredicate anyOfPort(cn.howxu.mmcr.api.machine.definition.BlockPredicate... predicates) {
        if (predicates == null || predicates.length == 0) throw new IllegalArgumentException("At least one port is required");
        return convert(InterfacePredicates.anyOfPort(predicates));
    }

    public static BlockPredicate parallelControllers() {
        return convert(InterfacePredicates.parallelControllers());
    }

    public static BlockPredicate factoryController() {
        return convert(InterfacePredicates.factoryController());
    }

    public static BlockPredicate smartInterface() { return convert(InterfacePredicates.smartInterface()); }

    public static BlockPredicate dataStorage() { return convert(InterfacePredicates.dataStorage()); }

    public static BlockPredicate networkInterface() { return convert(InterfacePredicates.networkInterface()); }

    public static BlockPredicate port(String id) {
        return convert(InterfacePredicates.anyOfPort(id));
    }

    public static BlockPredicate port(ResourceLocation id) {
        return port(id.toString());
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
        return PortTierRequirementSpec.from(InterfaceTiers.itemInput(id));
    }

    public static PortTierRequirementSpec itemOutputTier(String id) {
        return PortTierRequirementSpec.from(InterfaceTiers.itemOutput(id));
    }

    public static PortTierRequirementSpec fluidInputTier(String id) {
        return PortTierRequirementSpec.from(InterfaceTiers.fluidInput(id));
    }

    public static PortTierRequirementSpec fluidOutputTier(String id) {
        return PortTierRequirementSpec.from(InterfaceTiers.fluidOutput(id));
    }

    public static PortTierRequirementSpec energyInputTier(String id) {
        return PortTierRequirementSpec.from(InterfaceTiers.energyInput(id));
    }

    public static PortTierRequirementSpec energyOutputTier(String id) {
        return PortTierRequirementSpec.from(InterfaceTiers.energyOutput(id));
    }

    public static PortTierRequirementSpec sourceInputTier(String id) { return PortTierRequirementSpec.from(InterfaceTiers.sourceInput(id)); }
    public static PortTierRequirementSpec sourceOutputTier(String id) { return PortTierRequirementSpec.from(InterfaceTiers.sourceOutput(id)); }
    public static PortTierRequirementSpec manaInputTier(String id) { return PortTierRequirementSpec.from(InterfaceTiers.manaInput(id)); }
    public static PortTierRequirementSpec manaOutputTier(String id) { return PortTierRequirementSpec.from(InterfaceTiers.manaOutput(id)); }
    public static PortTierRequirementSpec chemicalInputTier(String id) { return PortTierRequirementSpec.from(InterfaceTiers.chemicalInput(id)); }
    public static PortTierRequirementSpec chemicalOutputTier(String id) { return PortTierRequirementSpec.from(InterfaceTiers.chemicalOutput(id)); }
    public static PortTierRequirementSpec radioactiveChemicalInputTier() { return PortTierRequirementSpec.from(InterfaceTiers.radioactiveChemicalInput()); }
    public static PortTierRequirementSpec radioactiveChemicalOutputTier() { return PortTierRequirementSpec.from(InterfaceTiers.radioactiveChemicalOutput()); }
    public static PortTierRequirementSpec heatInputTier() { return PortTierRequirementSpec.from(InterfaceTiers.heatInput()); }
    public static PortTierRequirementSpec heatOutputTier() { return PortTierRequirementSpec.from(InterfaceTiers.heatOutput()); }
    public static PortTierRequirementSpec stressInputTier() { return PortTierRequirementSpec.from(InterfaceTiers.stressInput()); }
    public static PortTierRequirementSpec stressOutputTier() { return PortTierRequirementSpec.from(InterfaceTiers.stressOutput()); }
    public static PortTierRequirementSpec airInputTier() { return PortTierRequirementSpec.from(InterfaceTiers.airInput()); }
    public static PortTierRequirementSpec airOutputTier() { return PortTierRequirementSpec.from(InterfaceTiers.airOutput()); }

    private static BlockPredicate convert(cn.howxu.mmcr.api.machine.definition.BlockPredicate predicate) {
        if (predicate.isMachineCoupler()) return BlockPredicate.machineCoupler();
        if (predicate.blockState().isPresent()) return new BlockPredicate.OfBlockState(predicate.blockState().get());
        if (predicate.block().isPresent()) return new BlockPredicate.OfBlock(predicate.block().get());
        if (predicate.blockSupplier().isPresent()) return new BlockPredicate.DeferredBlock(predicate.blockSupplier().get());
        if (predicate.tag().isPresent()) return new BlockPredicate.OfTag(predicate.tag().get());
        return new BlockPredicate.AnyOf(predicate.alternatives().stream().map(KubeJSInterfaceHelpers::convert).toList());
    }

}
