package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.util.IOType;
import java.util.Objects;
/** Small factories for independent port tiers and single-tier port presence requirements.
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

    public static PortTiers chemical(String id) { return chemical(find(id, PortTiers.ChemicalTier.values())); }

    public static PortTiers chemical(PortTiers.ChemicalTier tier) {
        return PortTiers.combine(chemicalInput(tier), chemicalOutput(tier));
    }

    public static PortTiers chemical(PortTiers.ChemicalTier tier, IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? chemicalInput(tier) : chemicalOutput(tier);
    }

    public static PortTiers chemicalInput(String id) { return chemicalInput(find(id, PortTiers.ChemicalTier.values())); }

    public static PortTiers chemicalInput(PortTiers.ChemicalTier tier) {
        return PortTiers.builder().minChemicalInput(tier).build();
    }

    public static PortTiers chemicalOutput(String id) { return chemicalOutput(find(id, PortTiers.ChemicalTier.values())); }

    public static PortTiers chemicalOutput(PortTiers.ChemicalTier tier) {
        return PortTiers.builder().minChemicalOutput(tier).build();
    }

    public static PortTiers radioactiveChemical() {
        return PortTiers.combine(radioactiveChemicalInput(), radioactiveChemicalOutput());
    }

    public static PortTiers radioactiveChemical(IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? radioactiveChemicalInput() : radioactiveChemicalOutput();
    }

    public static PortTiers radioactiveChemicalInput() { return PortTiers.builder().anyRadioactiveChemicalInput().build(); }

    public static PortTiers radioactiveChemicalOutput() { return PortTiers.builder().anyRadioactiveChemicalOutput().build(); }

    public static PortTiers heat() { return PortTiers.combine(heatInput(), heatOutput()); }

    public static PortTiers heat(IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? heatInput() : heatOutput();
    }

    public static PortTiers heatInput() { return PortTiers.builder().anyHeatInput().build(); }

    public static PortTiers heatOutput() { return PortTiers.builder().anyHeatOutput().build(); }

    public static PortTiers stress() { return PortTiers.combine(stressInput(), stressOutput()); }

    public static PortTiers stress(IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? stressInput() : stressOutput();
    }

    public static PortTiers stressInput() { return PortTiers.builder().anyStressInput().build(); }

    public static PortTiers stressOutput() { return PortTiers.builder().anyStressOutput().build(); }

    public static PortTiers air() { return PortTiers.combine(airInput(), airOutput()); }

    public static PortTiers air(IOType ioType) {
        Objects.requireNonNull(ioType, "ioType");
        return ioType == IOType.INPUT ? airInput() : airOutput();
    }

    public static PortTiers airInput() { return PortTiers.builder().anyAirInput().build(); }

    public static PortTiers airOutput() { return PortTiers.builder().anyAirOutput().build(); }

    public static PortTiers sourceInput() { return PortTiers.builder().anySourceInput().build(); }

    public static PortTiers sourceInput(String id) { requireNormal(id); return sourceInput(); }

    public static PortTiers sourceOutput() { return PortTiers.builder().anySourceOutput().build(); }

    public static PortTiers sourceOutput(String id) { requireNormal(id); return sourceOutput(); }

    public static PortTiers manaInput() { return PortTiers.builder().anyManaInput().build(); }

    public static PortTiers manaInput(String id) { requireNormal(id); return manaInput(); }

    public static PortTiers manaOutput() { return PortTiers.builder().anyManaOutput().build(); }

    public static PortTiers manaOutput(String id) { requireNormal(id); return manaOutput(); }

    public static PortTiers combine(PortTiers... declarations) {
        return PortTiers.combine(declarations);
    }

    private static <T extends Enum<T>> T find(String id, T[] values) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("tier id blank");
        for (T value : values) if (value.name().equalsIgnoreCase(id)) return value;
        throw new IllegalArgumentException("Unknown tier id: " + id);
    }

    private static void requireNormal(String id) {
        if (!"normal".equalsIgnoreCase(id)) throw new IllegalArgumentException("Unknown tier id: " + id);
    }
}
