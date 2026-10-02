package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.api.recipe.ParallelTier;

import cn.howxu.mmcr.internal.registration.BuiltinRegistration;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.create.CreateBridge;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Reusable predicates for built-in machine interfaces and controllers.
 * @author howxu <dev@howxu.cn>
 */
public final class InterfacePredicates {
    private InterfacePredicates() {
    }

    public static BlockPredicate anyOfItemInput() {
        return anyOfPorts(PortFamilyIds.ITEM, IOType.INPUT);
    }

    public static BlockPredicate anyItemInput() {
        return anyOfItemInput();
    }

    public static BlockPredicate anyOfItemOutput() {
        return anyOfPorts(PortFamilyIds.ITEM, IOType.OUTPUT);
    }

    public static BlockPredicate anyItemOutput() {
        return anyOfItemOutput();
    }

    public static BlockPredicate anyOfFluidInput() {
        return anyOfPorts(PortFamilyIds.FLUID, IOType.INPUT);
    }

    public static BlockPredicate anyFluidInput() {
        return anyOfFluidInput();
    }

    public static BlockPredicate anyOfFluidOutput() {
        return anyOfPorts(PortFamilyIds.FLUID, IOType.OUTPUT);
    }

    public static BlockPredicate anyFluidOutput() {
        return anyOfFluidOutput();
    }

    public static BlockPredicate anyOfEnergyInput() {
        return anyOfPorts(PortFamilyIds.ENERGY, IOType.INPUT);
    }

    public static BlockPredicate anyEnergyInput() {
        return anyOfEnergyInput();
    }

    public static BlockPredicate anyOfEnergyOutput() {
        return anyOfPorts(PortFamilyIds.ENERGY, IOType.OUTPUT);
    }

    public static BlockPredicate anyEnergyOutput() {
        return anyOfEnergyOutput();
    }

    public static BlockPredicate anyOfItemPorts() {
        return combine(anyOfItemInput(), anyOfItemOutput());
    }

    public static BlockPredicate anyItemPorts() {
        return anyOfItemPorts();
    }

    public static BlockPredicate anyOfFluidPorts() {
        return combine(anyOfFluidInput(), anyOfFluidOutput());
    }

    public static BlockPredicate anyFluidPorts() {
        return anyOfFluidPorts();
    }

    public static BlockPredicate anyOfEnergyPorts() {
        return combine(anyOfEnergyInput(), anyOfEnergyOutput());
    }

    public static BlockPredicate anyEnergyPorts() {
        return anyOfEnergyPorts();
    }

    public static BlockPredicate anyOfChemicalInput() {
        return anyOfChemicalPorts(IOType.INPUT, false);
    }

    public static BlockPredicate anyChemicalInput() {
        return anyOfChemicalInput();
    }

    public static BlockPredicate anyOfChemicalOutput() {
        return anyOfChemicalPorts(IOType.OUTPUT, false);
    }

    public static BlockPredicate anyChemicalOutput() {
        return anyOfChemicalOutput();
    }

    public static BlockPredicate anyOfRadioactiveChemicalInput() {
        return anyOfChemicalPorts(IOType.INPUT, true);
    }

    public static BlockPredicate anyRadioactiveChemicalInput() {
        return anyOfRadioactiveChemicalInput();
    }

    public static BlockPredicate anyOfRadioactiveChemicalOutput() {
        return anyOfChemicalPorts(IOType.OUTPUT, true);
    }

    public static BlockPredicate anyRadioactiveChemicalOutput() {
        return anyOfRadioactiveChemicalOutput();
    }

    public static BlockPredicate anyOfChemicalPorts() {
        return combine(anyOfChemicalInput(), anyOfChemicalOutput());
    }

    public static BlockPredicate anyChemicalPorts() {
        return anyOfChemicalPorts();
    }

    public static BlockPredicate anyOfRadioactiveChemicalPorts() {
        return combine(anyOfRadioactiveChemicalInput(), anyOfRadioactiveChemicalOutput());
    }

    public static BlockPredicate anyRadioactiveChemicalPorts() {
        return anyOfRadioactiveChemicalPorts();
    }

    public static BlockPredicate anyOfHeatInput() {
        return anyOfPorts(MekanismPortFamilies.HEAT, IOType.INPUT);
    }

    public static BlockPredicate anyHeatInput() {
        return anyOfHeatInput();
    }

    public static BlockPredicate anyOfHeatOutput() {
        return anyOfPorts(MekanismPortFamilies.HEAT, IOType.OUTPUT);
    }

    public static BlockPredicate anyHeatOutput() {
        return anyOfHeatOutput();
    }

    public static BlockPredicate anyOfHeatPorts() {
        return combine(anyOfHeatInput(), anyOfHeatOutput());
    }

    public static BlockPredicate anyHeatPorts() {
        return anyOfHeatPorts();
    }

    public static BlockPredicate anyOfUpgradeBus() {
        List<BlockPredicate> predicates = new ArrayList<>();
        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            predicates.add(port("upgrade_bus_" + size.id()));
        }
        return BlockPredicate.anyOf(predicates);
    }

    public static BlockPredicate anyOfStressInput() {
        return CreateBridge.get().available() ? anyOfPorts(CreateRecipeTypes.STRESS, IOType.INPUT) : BlockPredicate.none();
    }

    public static BlockPredicate anyStressInput() { return anyOfStressInput(); }

    public static BlockPredicate anyOfStressOutput() {
        return CreateBridge.get().available() ? anyOfPorts(CreateRecipeTypes.STRESS, IOType.OUTPUT) : BlockPredicate.none();
    }

    public static BlockPredicate anyStressOutput() { return anyOfStressOutput(); }

    public static BlockPredicate anyOfStressPorts() { return combine(anyOfStressInput(), anyOfStressOutput()); }

    public static BlockPredicate anyStressPorts() { return anyOfStressPorts(); }

    public static BlockPredicate anyUpgradeBus() {
        return anyOfUpgradeBus();
    }

    public static BlockPredicate ports() {
        List<BlockPredicate> predicates = new ArrayList<>(List.of(
                anyItemInput(), anyItemOutput(), anyFluidInput(), anyFluidOutput(),
                anyEnergyInput(), anyEnergyOutput()));
        addIfPresent(predicates, anyOfChemicalInput());
        addIfPresent(predicates, anyOfChemicalOutput());
        addIfPresent(predicates, anyOfRadioactiveChemicalInput());
        addIfPresent(predicates, anyOfRadioactiveChemicalOutput());
        addIfPresent(predicates, anyOfHeatInput());
        addIfPresent(predicates, anyOfHeatOutput());
        addIfPresent(predicates, anyOfStressInput());
        addIfPresent(predicates, anyOfStressOutput());
        return BlockPredicate.any(predicates.toArray(BlockPredicate[]::new));
    }

    public static BlockPredicate anyOfPort() {
        throw new IllegalArgumentException("At least one port is required");
    }

    public static BlockPredicate anyOfPort(String... ids) {
        if (ids == null || ids.length == 0) throw new IllegalArgumentException("At least one port is required");
        List<BlockPredicate> predicates = new ArrayList<>(ids.length);
        for (String id : ids) predicates.add(port(id));
        return BlockPredicate.anyOf(predicates);
    }

    public static BlockPredicate anyOfPort(ResourceLocation... ids) {
        if (ids == null || ids.length == 0) throw new IllegalArgumentException("At least one port is required");
        String[] paths = new String[ids.length];
        for (int i = 0; i < ids.length; i++) paths[i] = ids[i].toString();
        return anyOfPort(paths);
    }

    public static BlockPredicate anyOfPort(BlockPredicate... predicates) {
        if (predicates == null || predicates.length == 0) {
            throw new IllegalArgumentException("At least one port is required");
        }
        return BlockPredicate.anyOf(List.of(predicates));
    }

    public static BlockPredicate port(String id) {
        return BlockPredicate.deferredBlock(BuiltinRegistration.block(id));
    }

    public static BlockPredicate port(ResourceLocation id) {
        return BlockPredicate.deferredBlock(BuiltinRegistration.block(id));
    }

    public static BlockPredicate parallelControllers() {
        List<BlockPredicate> predicates = new ArrayList<>();
        for (ParallelTier tier : ParallelTier.values()) predicates.add(port(tier.idSuffix()));
        return BlockPredicate.anyOf(predicates);
    }

    public static BlockPredicate factoryController() {
        return port("factory_controller");
    }

    public static BlockPredicate smartInterface() {
        return port("smart_interface");
    }

    public static BlockPredicate dataStorage() {
        return port("data_storage");
    }

    public static BlockPredicate networkInterface(){return port("network_interface");}

    private static BlockPredicate anyOfPorts(ResourceLocation familyId, IOType ioType) {
        if (isUnavailableMekanismFamily(familyId)) return BlockPredicate.none();
        List<BlockPredicate> predicates = new ArrayList<>();
        for (IOPortKind kind : PortKinds.all()) {
            boolean exposesFamily = kind.families().stream()
                    .anyMatch(family -> family.familyId().equals(familyId) && family.ioType() == ioType
                            && kind.bindings().stream().anyMatch(family::matches));
            if (exposesFamily) predicates.add(port(kind.id()));
        }
        return predicates.isEmpty() ? BlockPredicate.none() : BlockPredicate.anyOf(predicates);
    }

    private static BlockPredicate anyOfChemicalPorts(IOType ioType, boolean radioactive) {
        if (!MekanismBridge.get().available()) return BlockPredicate.none();
        return anyOfPorts(radioactive ? MekanismPortFamilies.RADIOACTIVE_CHEMICAL
                : MekanismPortFamilies.CHEMICAL, ioType);
    }

    private static void addIfPresent(List<BlockPredicate> predicates, BlockPredicate predicate) {
        if (!predicate.alternatives().isEmpty()) predicates.add(predicate);
    }

    private static BlockPredicate combine(BlockPredicate... predicates) {
        List<BlockPredicate> alternatives = new ArrayList<>();
        for (BlockPredicate predicate : predicates) addIfPresent(alternatives, predicate);
        return alternatives.isEmpty() ? BlockPredicate.none() : BlockPredicate.anyOf(alternatives);
    }

    private static boolean isUnavailableMekanismFamily(ResourceLocation familyId) {
        return (familyId.equals(MekanismPortFamilies.HEAT)
                || familyId.equals(MekanismPortFamilies.CHEMICAL)
                || familyId.equals(MekanismPortFamilies.RADIOACTIVE_CHEMICAL))
                && !MekanismBridge.get().available();
    }

}
