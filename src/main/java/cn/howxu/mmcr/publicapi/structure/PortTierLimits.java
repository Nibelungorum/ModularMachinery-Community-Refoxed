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
    enum PortCategory { ITEM, FLUID, ENERGY, SOURCE, MANA }
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
    static PortTierLimits sourceInput() { return TierAdapters.sourceInput(); }
    static PortTierLimits sourceInput(String id) { return TierAdapters.sourceInput(id); }
    static PortTierLimits sourceOutput() { return TierAdapters.sourceOutput(); }
    static PortTierLimits sourceOutput(String id) { return TierAdapters.sourceOutput(id); }
    static PortTierLimits manaInput() { return TierAdapters.manaInput(); }
    static PortTierLimits manaInput(String id) { return TierAdapters.manaInput(id); }
    static PortTierLimits manaOutput() { return TierAdapters.manaOutput(); }
    static PortTierLimits manaOutput(String id) { return TierAdapters.manaOutput(id); }
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
        Builder anySourceInput();
        Builder anySourceOutput();
        Builder anyManaInput();
        Builder anyManaOutput();
        Builder minItemInput(ItemTier tier);
        Builder minItemOutput(ItemTier tier);
        Builder minFluidInput(FluidTier tier);
        Builder minFluidOutput(FluidTier tier);
        Builder minEnergyInput(EnergyTier tier);
        Builder minEnergyOutput(EnergyTier tier);
        PortTierLimits build();
    }
}
