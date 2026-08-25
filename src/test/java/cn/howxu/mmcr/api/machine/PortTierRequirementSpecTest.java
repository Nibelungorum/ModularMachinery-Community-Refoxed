package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortTierRequirementSpecTest {

    @Test
    void none_accepts_empty_ports() {
        assertThat(PortTierRequirementSpec.none().validate(List.of())).isEmpty();
    }

    @Test
    void any_item_input_accepts_any_item_input_tier() {
        var spec = PortTierRequirementSpec.builder().anyItemInput().build();

        assertThat(spec.validate(List.of(kind("item_input_bus_tiny")))).isEmpty();
    }

    @Test
    void minimum_energy_input_rejects_lower_tier() {
        var spec = PortTierRequirementSpec.builder()
                .minEnergyInput(EnergyHatchSize.LUDICROUS)
                .build();

        var failure = spec.validate(List.of(kind("energy_input_hatch_big")));

        assertThat(failure).hasValueSatisfying(value -> {
            assertThat(value.requirement().id()).isEqualTo("energy_input_hatch>=ludicrous");
            assertThat(value.actualPortIds()).containsExactly("energy_input_hatch_big");
        });
    }

    @Test
    void minimum_energy_input_accepts_exact_and_higher_tiers() {
        var spec = PortTierRequirementSpec.builder()
                .minEnergyInput(EnergyHatchSize.LUDICROUS)
                .build();

        assertThat(spec.validate(List.of(kind("energy_input_hatch_ludicrous")))).isEmpty();
        assertThat(spec.validate(List.of(kind("energy_input_hatch_ultimate")))).isEmpty();
    }

    @Test
    void wrong_direction_or_category_does_not_satisfy_requirement() {
        var spec = PortTierRequirementSpec.builder()
                .minFluidOutput(FluidHatchSize.HUGE)
                .build();

        assertThat(spec.validate(List.of(kind("fluid_input_hatch_vacuum")))).isPresent();
        assertThat(spec.validate(List.of(kind("energy_output_hatch_ultimate")))).isPresent();
    }

    @Test
    void item_and_fluid_minimums_accept_exact_or_higher_tiers() {
        var spec = PortTierRequirementSpec.builder()
                .minItemInput(ItemBusSize.NORMAL)
                .minFluidOutput(FluidHatchSize.HUGE)
                .build();

        assertThat(spec.validate(List.of(
                kind("item_input_bus"),
                kind("fluid_output_hatch_ludicrous")))).isEmpty();
    }

    @Test
    void combined_input_kind_matches_item_and_fluid_requirements() {
        IOPortKind kind = combinedKind(List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, ItemBusSize.NORMAL.ordinal(),
                        List.of("item_input_bus")),
                new PortFamilyDescriptor(PortFamilyIds.FLUID, IOType.INPUT, FluidHatchSize.NORMAL.ordinal(),
                        List.of("fluid_input_hatch"))));
        var spec = PortTierRequirementSpec.builder()
                .minItemInput(ItemBusSize.NORMAL)
                .minFluidInput(FluidHatchSize.NORMAL)
                .build();

        assertThat(spec.validate(List.of(kind))).isEmpty();
    }

    @Test
    void extended_item_kind_matches_the_highest_item_requirement() {
        IOPortKind kind = combinedKind(List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, ItemBusSize.LUDICROUS.ordinal() + 1,
                        List.of("item_input_bus"))));
        var spec = PortTierRequirementSpec.builder()
                .minItemInput(ItemBusSize.LUDICROUS)
                .build();

        assertThat(spec.validate(List.of(kind))).isEmpty();
    }

    @Test
    void dynamic_machine_defaults_to_no_tier_requirements() {
        var machine = new DynamicMachine(
                cn.howxu.mmcr.MMCR.id("tier_default_machine"),
                "Tier Default",
                new BlockArray(Map.of()));

        assertThat(machine.portTierRequirements()).isSameAs(PortTierRequirementSpec.none());
        assertThat(((Machine) machine).portTierRequirements()).isSameAs(PortTierRequirementSpec.none());
    }

    private static IOPortKind kind(String id) {
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static IOPortKind combinedKind(List<PortFamilyDescriptor> families) {
        return new PortKinds.CombinedKind("combined_input_test", IOType.INPUT, families,
                PortKinds.ITEM_INPUT.entityFactory(), List.of());
    }
}
