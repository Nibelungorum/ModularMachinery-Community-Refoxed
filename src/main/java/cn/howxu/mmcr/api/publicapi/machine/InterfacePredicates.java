package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.internal.registration.BuiltinRegistration;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;

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

    public static BlockPredicate anyOfUpgradeBus() {
        List<BlockPredicate> predicates = new ArrayList<>();
        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            predicates.add(port("upgrade_bus_" + size.id()));
        }
        return BlockPredicate.anyOf(predicates);
    }

    public static BlockPredicate anyUpgradeBus() {
        return anyOfUpgradeBus();
    }

    public static BlockPredicate ports() {
        return BlockPredicate.any(anyItemInput(), anyItemOutput(), anyFluidInput(), anyFluidOutput(),
                anyEnergyInput(), anyEnergyOutput());
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

    public static BlockPredicate anyOfPort(Identifier... ids) {
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

    public static BlockPredicate port(Identifier id) {
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

    private static BlockPredicate anyOfPorts(Identifier familyId, IOType ioType) {
        List<BlockPredicate> predicates = new ArrayList<>();
        for (IOPortKind kind : PortKinds.all()) {
            if (kind.ioType() != ioType) continue;
            boolean exposesFamily = kind.families().stream()
                    .anyMatch(family -> family.familyId().equals(familyId) && family.ioType() == ioType);
            if (exposesFamily) predicates.add(port(kind.id()));
        }
        return BlockPredicate.anyOf(predicates);
    }

}
