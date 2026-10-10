package cn.howxu.mmcr.internal.api.facade.structure;

import cn.howxu.mmcr.api.machine.definition.InterfaceTiers;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.util.IOType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Internal explicit enum conversion and delegation to canonical tier factories.
 * @author howxu <dev@howxu.cn>
 */
public final class TierAdapters {
    private TierAdapters() {}
    public static PortTierLimits wrap(PortTiers value) { return new TierView(Objects.requireNonNull(value)); }
    public static PortTiers unwrap(PortTierLimits value) { return ((TierView) value).value; }
    public static PortTierLimits none() { return wrap(PortTiers.none()); }
    public static PortTierLimits.Builder builder() { return builder(PortTiers.builder()); }
    public static PortTierLimits.Builder builder(PortTiers.Builder value) { return new TierOptions(value); }
    public static PortTierLimits combine(PortTierLimits... declarations) {
        return wrap(PortTiers.combine(Arrays.stream(declarations)
                .map(value -> value == null ? null : unwrap(value)).toArray(PortTiers[]::new)));
    }
    public static String id(PortTierLimits.ItemTier tier) { return toCore(tier).id(); }
    public static String id(PortTierLimits.FluidTier tier) { return toCore(tier).id(); }
    public static String id(PortTierLimits.EnergyTier tier) { return toCore(tier).id(); }
    public static String id(PortTierLimits.ChemicalTier tier) { return toCore(tier).id(); }
    public static PortTierLimits item(String id) { return wrap(InterfaceTiers.item(id)); }
    public static PortTierLimits item(PortTierLimits.ItemTier tier) { return wrap(InterfaceTiers.item(toCore(tier))); }
    public static PortTierLimits item(PortTierLimits.ItemTier tier, IoDirection io) {
        return wrap(InterfaceTiers.item(toCore(tier), toCore(io)));
    }
    public static PortTierLimits fluid(String id) { return wrap(InterfaceTiers.fluid(id)); }
    public static PortTierLimits fluid(PortTierLimits.FluidTier tier) { return wrap(InterfaceTiers.fluid(toCore(tier))); }
    public static PortTierLimits fluid(PortTierLimits.FluidTier tier, IoDirection io) {
        return wrap(InterfaceTiers.fluid(toCore(tier), toCore(io)));
    }
    public static PortTierLimits energy(String id) { return wrap(InterfaceTiers.energy(id)); }
    public static PortTierLimits energy(PortTierLimits.EnergyTier tier) { return wrap(InterfaceTiers.energy(toCore(tier))); }
    public static PortTierLimits energy(PortTierLimits.EnergyTier tier, IoDirection io) {
        return wrap(InterfaceTiers.energy(toCore(tier), toCore(io)));
    }
    public static PortTierLimits itemInput(String id) { return wrap(InterfaceTiers.itemInput(id)); }
    public static PortTierLimits itemInput(PortTierLimits.ItemTier tier) { return wrap(InterfaceTiers.itemInput(toCore(tier))); }
    public static PortTierLimits itemOutput(String id) { return wrap(InterfaceTiers.itemOutput(id)); }
    public static PortTierLimits itemOutput(PortTierLimits.ItemTier tier) { return wrap(InterfaceTiers.itemOutput(toCore(tier))); }
    public static PortTierLimits fluidInput(String id) { return wrap(InterfaceTiers.fluidInput(id)); }
    public static PortTierLimits fluidInput(PortTierLimits.FluidTier tier) { return wrap(InterfaceTiers.fluidInput(toCore(tier))); }
    public static PortTierLimits fluidOutput(String id) { return wrap(InterfaceTiers.fluidOutput(id)); }
    public static PortTierLimits fluidOutput(PortTierLimits.FluidTier tier) { return wrap(InterfaceTiers.fluidOutput(toCore(tier))); }
    public static PortTierLimits energyInput(String id) { return wrap(InterfaceTiers.energyInput(id)); }
    public static PortTierLimits energyInput(PortTierLimits.EnergyTier tier) { return wrap(InterfaceTiers.energyInput(toCore(tier))); }
    public static PortTierLimits energyOutput(String id) { return wrap(InterfaceTiers.energyOutput(id)); }
    public static PortTierLimits energyOutput(PortTierLimits.EnergyTier tier) { return wrap(InterfaceTiers.energyOutput(toCore(tier))); }
    public static PortTierLimits sourceInput() { return wrap(InterfaceTiers.sourceInput()); }
    public static PortTierLimits sourceInput(String id) { return wrap(InterfaceTiers.sourceInput(id)); }
    public static PortTierLimits sourceOutput() { return wrap(InterfaceTiers.sourceOutput()); }
    public static PortTierLimits sourceOutput(String id) { return wrap(InterfaceTiers.sourceOutput(id)); }
    public static PortTierLimits manaInput() { return wrap(InterfaceTiers.manaInput()); }
    public static PortTierLimits manaInput(String id) { return wrap(InterfaceTiers.manaInput(id)); }
    public static PortTierLimits manaOutput() { return wrap(InterfaceTiers.manaOutput()); }
    public static PortTierLimits manaOutput(String id) { return wrap(InterfaceTiers.manaOutput(id)); }
    public static PortTierLimits chemical(String id) { return wrap(InterfaceTiers.chemical(id)); }
    public static PortTierLimits chemical(PortTierLimits.ChemicalTier tier) { return wrap(InterfaceTiers.chemical(toCore(tier))); }
    public static PortTierLimits chemical(PortTierLimits.ChemicalTier tier, IoDirection io) {
        return wrap(InterfaceTiers.chemical(toCore(tier), toCore(io)));
    }
    public static PortTierLimits chemicalInput(String id) { return wrap(InterfaceTiers.chemicalInput(id)); }
    public static PortTierLimits chemicalInput(PortTierLimits.ChemicalTier tier) { return wrap(InterfaceTiers.chemicalInput(toCore(tier))); }
    public static PortTierLimits chemicalOutput(String id) { return wrap(InterfaceTiers.chemicalOutput(id)); }
    public static PortTierLimits chemicalOutput(PortTierLimits.ChemicalTier tier) { return wrap(InterfaceTiers.chemicalOutput(toCore(tier))); }
    public static PortTierLimits radioactiveChemical() { return wrap(InterfaceTiers.radioactiveChemical()); }
    public static PortTierLimits radioactiveChemical(IoDirection io) { return wrap(InterfaceTiers.radioactiveChemical(toCore(io))); }
    public static PortTierLimits radioactiveChemicalInput() { return wrap(InterfaceTiers.radioactiveChemicalInput()); }
    public static PortTierLimits radioactiveChemicalOutput() { return wrap(InterfaceTiers.radioactiveChemicalOutput()); }
    public static PortTierLimits heat() { return wrap(InterfaceTiers.heat()); }
    public static PortTierLimits heat(IoDirection io) { return wrap(InterfaceTiers.heat(toCore(io))); }
    public static PortTierLimits heatInput() { return wrap(InterfaceTiers.heatInput()); }
    public static PortTierLimits heatOutput() { return wrap(InterfaceTiers.heatOutput()); }
    public static PortTierLimits stress() { return wrap(InterfaceTiers.stress()); }
    public static PortTierLimits stress(IoDirection io) { return wrap(InterfaceTiers.stress(toCore(io))); }
    public static PortTierLimits stressInput() { return wrap(InterfaceTiers.stressInput()); }
    public static PortTierLimits stressOutput() { return wrap(InterfaceTiers.stressOutput()); }
    public static PortTierLimits air() { return wrap(InterfaceTiers.air()); }
    public static PortTierLimits air(IoDirection io) { return wrap(InterfaceTiers.air(toCore(io))); }
    public static PortTierLimits airInput() { return wrap(InterfaceTiers.airInput()); }
    public static PortTierLimits airOutput() { return wrap(InterfaceTiers.airOutput()); }

    private static IOType toCore(IoDirection io) {
        return switch (io) {
            case INPUT -> IOType.INPUT;
            case OUTPUT -> IOType.OUTPUT;
        };
    }
    private static IoDirection toPublic(IOType io) {
        return switch (io) {
            case INPUT -> IoDirection.INPUT;
            case OUTPUT -> IoDirection.OUTPUT;
        };
    }
    private static PortTiers.ItemTier toCore(PortTierLimits.ItemTier tier) {
        return switch (tier) {
            case TINY -> PortTiers.ItemTier.TINY;
            case SMALL -> PortTiers.ItemTier.SMALL;
            case NORMAL -> PortTiers.ItemTier.NORMAL;
            case REINFORCED -> PortTiers.ItemTier.REINFORCED;
            case BIG -> PortTiers.ItemTier.BIG;
            case HUGE -> PortTiers.ItemTier.HUGE;
            case LUDICROUS -> PortTiers.ItemTier.LUDICROUS;
        };
    }
    private static PortTiers.FluidTier toCore(PortTierLimits.FluidTier tier) {
        return switch (tier) {
            case TINY -> PortTiers.FluidTier.TINY;
            case SMALL -> PortTiers.FluidTier.SMALL;
            case NORMAL -> PortTiers.FluidTier.NORMAL;
            case REINFORCED -> PortTiers.FluidTier.REINFORCED;
            case BIG -> PortTiers.FluidTier.BIG;
            case HUGE -> PortTiers.FluidTier.HUGE;
            case LUDICROUS -> PortTiers.FluidTier.LUDICROUS;
            case VACUUM -> PortTiers.FluidTier.VACUUM;
        };
    }
    private static PortTiers.EnergyTier toCore(PortTierLimits.EnergyTier tier) {
        return switch (tier) {
            case TINY -> PortTiers.EnergyTier.TINY;
            case SMALL -> PortTiers.EnergyTier.SMALL;
            case NORMAL -> PortTiers.EnergyTier.NORMAL;
            case REINFORCED -> PortTiers.EnergyTier.REINFORCED;
            case BIG -> PortTiers.EnergyTier.BIG;
            case HUGE -> PortTiers.EnergyTier.HUGE;
            case LUDICROUS -> PortTiers.EnergyTier.LUDICROUS;
            case ULTIMATE -> PortTiers.EnergyTier.ULTIMATE;
        };
    }

    private static PortTiers.ChemicalTier toCore(PortTierLimits.ChemicalTier tier) {
        return switch (tier) {
            case BASIC -> PortTiers.ChemicalTier.BASIC;
            case ADVANCED -> PortTiers.ChemicalTier.ADVANCED;
            case ELITE -> PortTiers.ChemicalTier.ELITE;
            case ULTIMATE -> PortTiers.ChemicalTier.ULTIMATE;
        };
    }

    /** Core-backed tier builder.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TierOptions implements PortTierLimits.Builder {
        private final PortTiers.Builder value;
        private TierOptions(PortTiers.Builder value) { this.value = value; }
        public PortTierLimits.Builder anyItemInput() { value.anyItemInput(); return this; }
        public PortTierLimits.Builder anyItemOutput() { value.anyItemOutput(); return this; }
        public PortTierLimits.Builder anyFluidInput() { value.anyFluidInput(); return this; }
        public PortTierLimits.Builder anyFluidOutput() { value.anyFluidOutput(); return this; }
        public PortTierLimits.Builder anyEnergyInput() { value.anyEnergyInput(); return this; }
        public PortTierLimits.Builder anyEnergyOutput() { value.anyEnergyOutput(); return this; }
        public PortTierLimits.Builder anySourceInput() { value.anySourceInput(); return this; }
        public PortTierLimits.Builder anySourceOutput() { value.anySourceOutput(); return this; }
        public PortTierLimits.Builder anyManaInput() { value.anyManaInput(); return this; }
        public PortTierLimits.Builder anyManaOutput() { value.anyManaOutput(); return this; }
        public PortTierLimits.Builder anyChemicalInput() { value.anyChemicalInput(); return this; }
        public PortTierLimits.Builder anyChemicalOutput() { value.anyChemicalOutput(); return this; }
        public PortTierLimits.Builder anyRadioactiveChemicalInput() { value.anyRadioactiveChemicalInput(); return this; }
        public PortTierLimits.Builder anyRadioactiveChemicalOutput() { value.anyRadioactiveChemicalOutput(); return this; }
        public PortTierLimits.Builder anyHeatInput() { value.anyHeatInput(); return this; }
        public PortTierLimits.Builder anyHeatOutput() { value.anyHeatOutput(); return this; }
        public PortTierLimits.Builder anyStressInput() { value.anyStressInput(); return this; }
        public PortTierLimits.Builder anyStressOutput() { value.anyStressOutput(); return this; }
        public PortTierLimits.Builder anyAirInput() { value.anyAirInput(); return this; }
        public PortTierLimits.Builder anyAirOutput() { value.anyAirOutput(); return this; }
        public PortTierLimits.Builder minItemInput(PortTierLimits.ItemTier tier) { value.minItemInput(toCore(tier)); return this; }
        public PortTierLimits.Builder minItemOutput(PortTierLimits.ItemTier tier) { value.minItemOutput(toCore(tier)); return this; }
        public PortTierLimits.Builder minFluidInput(PortTierLimits.FluidTier tier) { value.minFluidInput(toCore(tier)); return this; }
        public PortTierLimits.Builder minFluidOutput(PortTierLimits.FluidTier tier) { value.minFluidOutput(toCore(tier)); return this; }
        public PortTierLimits.Builder minEnergyInput(PortTierLimits.EnergyTier tier) { value.minEnergyInput(toCore(tier)); return this; }
        public PortTierLimits.Builder minEnergyOutput(PortTierLimits.EnergyTier tier) { value.minEnergyOutput(toCore(tier)); return this; }
        public PortTierLimits.Builder minChemicalInput(PortTierLimits.ChemicalTier tier) { value.minChemicalInput(toCore(tier)); return this; }
        public PortTierLimits.Builder minChemicalOutput(PortTierLimits.ChemicalTier tier) { value.minChemicalOutput(toCore(tier)); return this; }
        public PortTierLimits build() { return wrap(value.build()); }
    }

    /** Tier declaration view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TierView implements PortTierLimits {
        private final PortTiers value;
        private TierView(PortTiers value) { this.value = value; }
        public List<RequirementView> requirements() {
            return value.requirements().stream().<RequirementView>map(RequirementViewAdapter::new).toList();
        }
    }

    /** Tier requirement view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RequirementViewAdapter implements PortTierLimits.RequirementView {
        private final PortTiers.Requirement value;
        private RequirementViewAdapter(PortTiers.Requirement value) { this.value = value; }
        public PortTierLimits.PortCategory category() {
            return switch (value.category()) {
                case ITEM -> PortTierLimits.PortCategory.ITEM;
                case FLUID -> PortTierLimits.PortCategory.FLUID;
                case ENERGY -> PortTierLimits.PortCategory.ENERGY;
                case SOURCE -> PortTierLimits.PortCategory.SOURCE;
                case MANA -> PortTierLimits.PortCategory.MANA;
                case CHEMICAL -> PortTierLimits.PortCategory.CHEMICAL;
                case RADIOACTIVE_CHEMICAL -> PortTierLimits.PortCategory.RADIOACTIVE_CHEMICAL;
                case HEAT -> PortTierLimits.PortCategory.HEAT;
                case STRESS -> PortTierLimits.PortCategory.STRESS;
                case AIR -> PortTierLimits.PortCategory.AIR;
            };
        }
        public IoDirection ioType() { return toPublic(value.ioType()); }
        public int minTier() { return value.minTier(); }
        public String minTierId() { return value.minTierId(); }
    }
}
