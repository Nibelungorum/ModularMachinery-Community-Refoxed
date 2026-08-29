package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.InterfaceTiers;
import net.minecraft.resources.Identifier;

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

    public static BlockPredicate anyOfPort(String... ids) {
        if (ids == null || ids.length == 0) throw new IllegalArgumentException("At least one port is required");
        return convert(InterfacePredicates.anyOfPort(ids));
    }

    public static BlockPredicate anyOfPort() {
        throw new IllegalArgumentException("At least one port is required");
    }

    public static BlockPredicate anyOfPort(Identifier... ids) {
        return convert(InterfacePredicates.anyOfPort(ids));
    }

    public static BlockPredicate anyOfPort(cn.howxu.mmcr.api.publicapi.machine.BlockPredicate... predicates) {
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

    public static BlockPredicate port(String id) {
        return convert(InterfacePredicates.anyOfPort(id));
    }

    public static BlockPredicate port(Identifier id) {
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

    private static BlockPredicate convert(cn.howxu.mmcr.api.publicapi.machine.BlockPredicate predicate) {
        if (predicate.isMachineCoupler()) return BlockPredicate.machineCoupler();
        if (predicate.blockState().isPresent()) return new BlockPredicate.OfBlockState(predicate.blockState().get());
        if (predicate.block().isPresent()) return new BlockPredicate.OfBlock(predicate.block().get());
        if (predicate.blockSupplier().isPresent()) return new BlockPredicate.DeferredBlock(predicate.blockSupplier().get());
        if (predicate.tag().isPresent()) return new BlockPredicate.OfTag(predicate.tag().get());
        return new BlockPredicate.AnyOf(predicate.alternatives().stream().map(KubeJSInterfaceHelpers::convert).toList());
    }

}
