package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.util.IOType;
import java.util.Objects;
/** Small factories for independent item, fluid, and energy port tiers.
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceTiers {
    private InterfaceTiers() {
    }

    public static PortTiers item(String id) {
        return item(find(id, PortTiers.ItemTier.values()));
    }

    public static PortTiers item(PortTiers.ItemTier tier) {
        return PortTiers.combine(itemInput(tier), itemOutput(tier));
    }

    public static PortTiers item(PortTiers.ItemTier tier, IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? itemInput(tier) : itemOutput(tier);
    }

    public static PortTiers fluid(String id) {
        return fluid(find(id, PortTiers.FluidTier.values()));
    }

    public static PortTiers fluid(PortTiers.FluidTier tier) {
        return PortTiers.combine(fluidInput(tier), fluidOutput(tier));
    }

    public static PortTiers fluid(PortTiers.FluidTier tier, IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? fluidInput(tier) : fluidOutput(tier);
    }

    public static PortTiers energy(String id) {
        return energy(find(id, PortTiers.EnergyTier.values()));
    }

    public static PortTiers energy(PortTiers.EnergyTier tier) {
        return PortTiers.combine(energyInput(tier), energyOutput(tier));
    }

    public static PortTiers energy(PortTiers.EnergyTier tier, IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? energyInput(tier) : energyOutput(tier);
    }

    public static PortTiers itemInput(PortTiers.ItemTier tier) {
        return PortTiers.builder().minItemInput(tier).build();
    }

    public static PortTiers itemInput(String id) { return itemInput(find(id, PortTiers.ItemTier.values())); }

    public static PortTiers itemOutput(PortTiers.ItemTier tier) {
        return PortTiers.builder().minItemOutput(tier).build();
    }

    public static PortTiers itemOutput(String id) { return itemOutput(find(id, PortTiers.ItemTier.values())); }

    public static PortTiers fluidInput(PortTiers.FluidTier tier) {
        return PortTiers.builder().minFluidInput(tier).build();
    }

    public static PortTiers fluidInput(String id) { return fluidInput(find(id, PortTiers.FluidTier.values())); }

    public static PortTiers fluidOutput(PortTiers.FluidTier tier) {
        return PortTiers.builder().minFluidOutput(tier).build();
    }

    public static PortTiers fluidOutput(String id) { return fluidOutput(find(id, PortTiers.FluidTier.values())); }

    public static PortTiers energyInput(PortTiers.EnergyTier tier) {
        return PortTiers.builder().minEnergyInput(tier).build();
    }

    public static PortTiers energyInput(String id) { return energyInput(find(id, PortTiers.EnergyTier.values())); }

    public static PortTiers energyOutput(PortTiers.EnergyTier tier) {
        return PortTiers.builder().minEnergyOutput(tier).build();
    }

    public static PortTiers energyOutput(String id) { return energyOutput(find(id, PortTiers.EnergyTier.values())); }

    public static PortTiers combine(PortTiers... declarations) {
        return PortTiers.combine(declarations);
    }

    private static <T extends Enum<T>> T find(String id, T[] values) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("tier id blank");
        for (T value : values) if (value.name().equalsIgnoreCase(id)) return value;
        throw new IllegalArgumentException("Unknown tier id: " + id);
    }
}
