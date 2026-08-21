package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.InterfaceTiers;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceHelpersTest {
    @Test
    void interface_predicates_keep_registered_port_alternatives() {
        assertThat(InterfacePredicates.anyOfItemInput().alternatives()).hasSize(7);
        assertThat(InterfacePredicates.anyOfFluidOutput().alternatives()).hasSize(8);
        assertThat(InterfacePredicates.anyOfEnergyInput().alternatives()).hasSize(8);
        assertThat(InterfacePredicates.anyOfPort("item_input_bus").alternatives()).hasSize(1);
        assertThat(InterfacePredicates.smartInterface().blockSupplier()).isPresent();
    }

    @Test
    void any_of_port_rejects_empty_alternatives() {
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interface_tiers_keep_item_fluid_and_energy_enums_separate() {
        PortTiers tiers = InterfaceTiers.combine(
                InterfaceTiers.itemInput(PortTiers.ItemTier.NORMAL),
                InterfaceTiers.fluidOutput(PortTiers.FluidTier.VACUUM),
                InterfaceTiers.energyInput(PortTiers.EnergyTier.ULTIMATE));

        assertThat(tiers.requirements()).extracting(PortTiers.Requirement::category)
                .containsExactly(PortTiers.PortCategory.ITEM, PortTiers.PortCategory.FLUID,
                        PortTiers.PortCategory.ENERGY);
        assertThat(tiers.requirements()).extracting(PortTiers.Requirement::minTierId)
                .containsExactly("normal", "vacuum", "ultimate");
        assertThatThrownBy(() -> new PortTiers.Requirement(PortTiers.PortCategory.ITEM,
                cn.howxu.mmcr.util.IOType.INPUT, PortTiers.ItemTier.NORMAL.ordinal(), "big"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
