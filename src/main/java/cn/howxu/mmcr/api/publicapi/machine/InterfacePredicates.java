package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration;
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
        return anyOfPorts("item_input_bus", PortTiers.ItemTier.values());
    }

    public static BlockPredicate anyOfItemOutput() {
        return anyOfPorts("item_output_bus", PortTiers.ItemTier.values());
    }

    public static BlockPredicate anyOfFluidInput() {
        return anyOfPorts("fluid_input_hatch", PortTiers.FluidTier.values());
    }

    public static BlockPredicate anyOfFluidOutput() {
        return anyOfPorts("fluid_output_hatch", PortTiers.FluidTier.values());
    }

    public static BlockPredicate anyOfEnergyInput() {
        return anyOfPorts("energy_input_hatch", PortTiers.EnergyTier.values());
    }

    public static BlockPredicate anyOfEnergyOutput() {
        return anyOfPorts("energy_output_hatch", PortTiers.EnergyTier.values());
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
        return BlockPredicate.anyOf(List.of(predicates));
    }

    public static BlockPredicate parallelControllers() {
        List<BlockPredicate> predicates = new ArrayList<>();
        for (ParallelTier tier : ParallelTier.values()) predicates.add(port(tier.idSuffix()));
        return BlockPredicate.anyOf(predicates);
    }

    public static BlockPredicate smartInterface() {
        return port("smart_interface");
    }

    private static BlockPredicate anyOfPorts(String base, Enum<?>[] tiers) {
        List<BlockPredicate> predicates = new ArrayList<>(tiers.length + 1);
        predicates.add(port(base));
        for (Enum<?> tier : tiers) {
            String id = tier instanceof PortTiers.ItemTier item ? item.id()
                    : tier instanceof PortTiers.FluidTier fluid ? fluid.id()
                    : ((PortTiers.EnergyTier) tier).id();
            if (!id.equals("normal")) predicates.add(port(base + "_" + id));
        }
        return BlockPredicate.anyOf(predicates);
    }

    private static BlockPredicate port(String id) {
        return BlockPredicate.deferredBlock(PublicBuiltinRegistration.block(id));
    }
}
