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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
    void combined_kind_accepts_item_and_fluid_for_both_directions() {
        assertThat(ordinaryCombinedKind(IOType.INPUT).families())
                .extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(ordinaryCombinedKind(IOType.OUTPUT).families())
                .extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
    }

    @Test
    void combined_kind_rejects_a_single_family() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT)));
    }

    @Test
    void combined_kind_rejects_an_energy_family() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT), energyFamily(IOType.INPUT)));
    }

    @Test
    void combined_kind_rejects_duplicate_families() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT), itemFamily(IOType.INPUT)));
    }

    @Test
    void combined_kind_rejects_a_third_family() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT), fluidFamily(IOType.INPUT),
                energyFamily(IOType.INPUT)));
    }

    @Test
    void combined_kind_rejects_mixed_directions() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT), fluidFamily(IOType.OUTPUT)));
    }

    @Test
    void extended_item_kind_matches_the_highest_item_requirement() {
        IOPortKind kind = combinedKind(List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, ItemBusSize.LUDICROUS.ordinal() + 1,
                        List.of("item_input_bus")),
                fluidFamily(IOType.INPUT)));
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
        return combinedKind(IOType.INPUT, families);
    }

    private static IOPortKind ordinaryCombinedKind(IOType ioType) {
        return combinedKind(ioType, List.of(itemFamily(ioType), fluidFamily(ioType)));
    }

    private static IOPortKind combinedKind(IOType ioType, List<PortFamilyDescriptor> families) {
        return new PortKinds.CombinedKind("combined_" + ioType.getSerializedName() + "_test", ioType, families,
                PortKinds.ITEM_INPUT.entityFactory(), List.of());
    }

    private static void assertInvalidCombined(List<PortFamilyDescriptor> families) {
        assertThatIllegalArgumentException().isThrownBy(() -> combinedKind(IOType.INPUT, families));
    }

    private static PortFamilyDescriptor itemFamily(IOType ioType) {
        return new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType, 2,
                List.of(ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus"));
    }

    private static PortFamilyDescriptor fluidFamily(IOType ioType) {
        return new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType, 2,
                List.of(ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch"));
    }

    private static PortFamilyDescriptor energyFamily(IOType ioType) {
        return new PortFamilyDescriptor(PortFamilyIds.ENERGY, ioType, 2,
                List.of(ioType == IOType.INPUT ? "energy_input_hatch" : "energy_output_hatch"));
    }
}
