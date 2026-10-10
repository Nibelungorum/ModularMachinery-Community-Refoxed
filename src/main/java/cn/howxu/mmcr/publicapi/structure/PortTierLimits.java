package cn.howxu.mmcr.publicapi.structure;

import cn.howxu.mmcr.internal.api.facade.structure.TierAdapters;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;

/** Factory-owned per-family tier constraints; not a consumer SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface PortTierLimits {
    /** Independently tiered port families.
     * @author howxu <dev@howxu.cn>
     */
    enum PortCategory { ITEM, FLUID, ENERGY, CHEMICAL, RADIOACTIVE_CHEMICAL, HEAT }
    /** Item bus tiers.
     * @author howxu <dev@howxu.cn>
     */
    enum ItemTier {
        TINY, SMALL, NORMAL, REINFORCED, BIG, HUGE, LUDICROUS;
        public String id() { return TierAdapters.id(this); }
    }
    /** Fluid hatch tiers.
     * @author howxu <dev@howxu.cn>
     */
    enum FluidTier {
        TINY, SMALL, NORMAL, REINFORCED, BIG, HUGE, LUDICROUS, VACUUM;
        public String id() { return TierAdapters.id(this); }
    }
    /** Energy hatch tiers.
     * @author howxu <dev@howxu.cn>
     */
    enum EnergyTier {
        TINY, SMALL, NORMAL, REINFORCED, BIG, HUGE, LUDICROUS, ULTIMATE;
        public String id() { return TierAdapters.id(this); }
    }
    /** Non-radioactive chemical hatch tiers.
     * @author howxu <dev@howxu.cn>
     */
    enum ChemicalTier {
        BASIC, ADVANCED, ELITE, ULTIMATE;
        public String id() { return TierAdapters.id(this); }
    }

    static PortTierLimits none() { return TierAdapters.none(); }
    static Builder builder() { return TierAdapters.builder(); }
    static PortTierLimits combine(PortTierLimits... declarations) { return TierAdapters.combine(declarations); }
    static PortTierLimits item(String id) { return TierAdapters.item(id); }
    static PortTierLimits item(ItemTier tier) { return TierAdapters.item(tier); }
    static PortTierLimits item(ItemTier tier, IoDirection io) { return TierAdapters.item(tier, io); }
    static PortTierLimits fluid(String id) { return TierAdapters.fluid(id); }
    static PortTierLimits fluid(FluidTier tier) { return TierAdapters.fluid(tier); }
    static PortTierLimits fluid(FluidTier tier, IoDirection io) { return TierAdapters.fluid(tier, io); }
    static PortTierLimits energy(String id) { return TierAdapters.energy(id); }
    static PortTierLimits energy(EnergyTier tier) { return TierAdapters.energy(tier); }
    static PortTierLimits energy(EnergyTier tier, IoDirection io) { return TierAdapters.energy(tier, io); }
    static PortTierLimits itemInput(String id) { return TierAdapters.itemInput(id); }
    static PortTierLimits itemInput(ItemTier tier) { return TierAdapters.itemInput(tier); }
    static PortTierLimits itemOutput(String id) { return TierAdapters.itemOutput(id); }
    static PortTierLimits itemOutput(ItemTier tier) { return TierAdapters.itemOutput(tier); }
    static PortTierLimits fluidInput(String id) { return TierAdapters.fluidInput(id); }
    static PortTierLimits fluidInput(FluidTier tier) { return TierAdapters.fluidInput(tier); }
    static PortTierLimits fluidOutput(String id) { return TierAdapters.fluidOutput(id); }
    static PortTierLimits fluidOutput(FluidTier tier) { return TierAdapters.fluidOutput(tier); }
    static PortTierLimits energyInput(String id) { return TierAdapters.energyInput(id); }
    static PortTierLimits energyInput(EnergyTier tier) { return TierAdapters.energyInput(tier); }
    static PortTierLimits energyOutput(String id) { return TierAdapters.energyOutput(id); }
    static PortTierLimits energyOutput(EnergyTier tier) { return TierAdapters.energyOutput(tier); }
    static PortTierLimits chemical(String id) { return TierAdapters.chemical(id); }
    static PortTierLimits chemical(ChemicalTier tier) { return TierAdapters.chemical(tier); }
    static PortTierLimits chemical(ChemicalTier tier, IoDirection io) { return TierAdapters.chemical(tier, io); }
    static PortTierLimits chemicalInput(String id) { return TierAdapters.chemicalInput(id); }
    static PortTierLimits chemicalInput(ChemicalTier tier) { return TierAdapters.chemicalInput(tier); }
    static PortTierLimits chemicalOutput(String id) { return TierAdapters.chemicalOutput(id); }
    static PortTierLimits chemicalOutput(ChemicalTier tier) { return TierAdapters.chemicalOutput(tier); }
    /** Requires the single-tier radioactive chemical ports in both directions. */
    static PortTierLimits radioactiveChemical() { return TierAdapters.radioactiveChemical(); }
    static PortTierLimits radioactiveChemical(IoDirection io) { return TierAdapters.radioactiveChemical(io); }
    static PortTierLimits radioactiveChemicalInput() { return TierAdapters.radioactiveChemicalInput(); }
    static PortTierLimits radioactiveChemicalOutput() { return TierAdapters.radioactiveChemicalOutput(); }
    /** Requires the single-tier heat ports in both directions. */
    static PortTierLimits heat() { return TierAdapters.heat(); }
    static PortTierLimits heat(IoDirection io) { return TierAdapters.heat(io); }
    static PortTierLimits heatInput() { return TierAdapters.heatInput(); }
    static PortTierLimits heatOutput() { return TierAdapters.heatOutput(); }
    List<RequirementView> requirements();

    /** Factory-owned tier requirement view.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface RequirementView {
        PortCategory category();
        IoDirection ioType();
        int minTier();
        String minTierId();
    }

    /** Factory-owned configuration handle.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface Builder {
        Builder anyItemInput();
        Builder anyItemOutput();
        Builder anyFluidInput();
        Builder anyFluidOutput();
        Builder anyEnergyInput();
        Builder anyEnergyOutput();
        Builder anyChemicalInput();
        Builder anyChemicalOutput();
        Builder anyRadioactiveChemicalInput();
        Builder anyRadioactiveChemicalOutput();
        Builder anyHeatInput();
        Builder anyHeatOutput();
        Builder minItemInput(ItemTier tier);
        Builder minItemOutput(ItemTier tier);
        Builder minFluidInput(FluidTier tier);
        Builder minFluidOutput(FluidTier tier);
        Builder minEnergyInput(EnergyTier tier);
        Builder minEnergyOutput(EnergyTier tier);
        Builder minChemicalInput(ChemicalTier tier);
        Builder minChemicalOutput(ChemicalTier tier);
        PortTierLimits build();
    }
}
