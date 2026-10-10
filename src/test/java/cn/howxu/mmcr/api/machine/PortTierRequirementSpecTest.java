package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
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
        assertThat(ordinaryCombinedKind(IOType.INPUT).bindings())
                .extracting(binding -> binding.type())
                .containsExactly(BuiltinCapabilityDefinitions.ITEM_TYPE, BuiltinCapabilityDefinitions.FLUID_TYPE);
        assertThat(ordinaryCombinedKind(IOType.OUTPUT).bindings())
                .extracting(binding -> binding.type())
                .containsExactly(BuiltinCapabilityDefinitions.ITEM_TYPE, BuiltinCapabilityDefinitions.FLUID_TYPE);
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
    void combined_kind_rejects_an_energy_capability_type() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT), fluidFamily(IOType.INPUT)),
                List.of(BuiltinCapabilityDefinitions.ITEM_TYPE, BuiltinCapabilityDefinitions.ENERGY_TYPE));
    }

    @Test
    void combined_kind_rejects_a_third_capability_type() {
        assertInvalidCombined(List.of(itemFamily(IOType.INPUT), fluidFamily(IOType.INPUT)),
                List.of(BuiltinCapabilityDefinitions.ITEM_TYPE, BuiltinCapabilityDefinitions.FLUID_TYPE,
                        new CapabilityType(MMCR.id("custom"))));
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
                MMCR.id("tier_default_machine"),
                "Tier Default",
                new BlockArray(Map.of()));

        assertThat(machine.portTierRequirements()).isSameAs(PortTierRequirementSpec.none());
        assertThat(((Machine) machine).portTierRequirements()).isSameAs(PortTierRequirementSpec.none());
    }

    @Test
    void chemical_minimums_compare_registered_tiers_in_both_directions() {
        for (var minimum : PortTiers.ChemicalTier.values()) {
            for (var actual : PortTiers.ChemicalTier.values()) {
                var input = PortTierRequirementSpec.builder().minChemicalInput(minimum).build();
                var output = PortTierRequirementSpec.builder().minChemicalOutput(minimum).build();
                boolean sufficient = actual.ordinal() >= minimum.ordinal();
                assertThat(input.validate(List.of(mekanismKind("chemical_input_hatch_" + actual.id()))).isEmpty())
                        .as("input %s >= %s", actual, minimum).isEqualTo(sufficient);
                assertThat(output.validate(List.of(mekanismKind("chemical_output_hatch_" + actual.id()))).isEmpty())
                        .as("output %s >= %s", actual, minimum).isEqualTo(sufficient);
            }
        }
    }

    @Test
    void chemical_requirements_keep_radioactivity_and_direction_separate() {
        var normal = PortTierRequirementSpec.builder().anyChemicalInput().build();
        var radioactive = PortTierRequirementSpec.builder().anyRadioactiveChemicalInput().build();
        assertThat(normal.validate(List.of(mekanismKind("radioactive_chemical_input_hatch")))).isPresent();
        assertThat(normal.validate(List.of(mekanismKind("chemical_output_hatch_ultimate")))).isPresent();
        assertThat(radioactive.validate(List.of(mekanismKind("chemical_input_hatch_ultimate")))).isPresent();
        assertThat(radioactive.validate(List.of(mekanismKind("radioactive_chemical_output_hatch")))).isPresent();
        assertThat(radioactive.validate(List.of(mekanismKind("radioactive_chemical_input_hatch")))).isEmpty();
    }

    @Test
    void single_tier_ports_report_presence_without_a_fabricated_grade() {
        var spec = PortTierRequirementSpec.builder().anyHeatInput().anyHeatOutput()
                .anyRadioactiveChemicalInput().anyRadioactiveChemicalOutput().build();
        assertThat(spec.validate(List.of())).hasValueSatisfying(failure ->
                assertThat(failure.requirement().id()).isEqualTo("heat_input_hatch"));
        assertThat(spec.validate(List.of(mekanismKind("heat_output_hatch")))).isPresent();
        assertThat(spec.validate(List.of(mekanismKind("heat_input_hatch"), mekanismKind("heat_output_hatch"),
                mekanismKind("radioactive_chemical_input_hatch"), mekanismKind("radioactive_chemical_output_hatch"))))
                .isEmpty();
    }

    @Test
    void new_requirements_reject_inconsistent_or_fabricated_tiers_in_both_layers() {
        for (var category : List.of(PortTiers.PortCategory.CHEMICAL, PortTiers.PortCategory.RADIOACTIVE_CHEMICAL,
                PortTiers.PortCategory.HEAT, PortTiers.PortCategory.STRESS, PortTiers.PortCategory.AIR)) {
            var runtimeCategory = PortTierRequirementSpec.PortCategory.valueOf(category.name());
            assertThatIllegalArgumentException().isThrownBy(() -> new PortTiers.Requirement(category, IOType.INPUT, 0, "ultimate"));
            assertThatIllegalArgumentException().isThrownBy(() -> new PortTierRequirementSpec.Requirement(runtimeCategory, IOType.INPUT, 0, "ultimate"));
            assertThatIllegalArgumentException().isThrownBy(() -> new PortTiers.Requirement(category, IOType.OUTPUT, 8, "any"));
            assertThatIllegalArgumentException().isThrownBy(() -> new PortTierRequirementSpec.Requirement(runtimeCategory, IOType.OUTPUT, 8, "any"));
        }
    }

    @Test
    void every_registered_extended_grade_satisfies_its_highest_builtin_minimum_only_in_its_direction() {
        for (var kind : PortKinds.all()) {
            PortTierRequirementSpec input;
            PortTierRequirementSpec output;
            if (kind instanceof PortKinds.ExtendedItemBusKind) {
                input = PortTierRequirementSpec.builder().minItemInput(ItemBusSize.LUDICROUS).build();
                output = PortTierRequirementSpec.builder().minItemOutput(ItemBusSize.LUDICROUS).build();
            } else if (kind instanceof PortKinds.ExtendedFluidHatchKind) {
                input = PortTierRequirementSpec.builder().minFluidInput(FluidHatchSize.VACUUM).build();
                output = PortTierRequirementSpec.builder().minFluidOutput(FluidHatchSize.VACUUM).build();
            } else if (kind instanceof PortKinds.ExtendedEnergyHatchKind) {
                input = PortTierRequirementSpec.builder().minEnergyInput(EnergyHatchSize.ULTIMATE).build();
                output = PortTierRequirementSpec.builder().minEnergyOutput(EnergyHatchSize.ULTIMATE).build();
            } else {
                continue;
            }
            assertThat(input.validate(List.of(kind)).isEmpty()).as("input minimum: %s", kind.id())
                    .isEqualTo(kind.ioType() == IOType.INPUT);
            assertThat(output.validate(List.of(kind)).isEmpty()).as("output minimum: %s", kind.id())
                    .isEqualTo(kind.ioType() == IOType.OUTPUT);
        }
    }

    private static IOPortKind mekanismKind(String id) {
        var declaration = MekanismBridgeBootstrap.selectForTesting(true).portDeclarations().stream()
                .filter(port -> port.id().equals(id)).findFirst().orElseThrow();
        return declaration.type() == MekanismBridge.PortType.CHEMICAL
                ? new PortKinds.ChemicalKind(id, declaration.ioType(), declaration.tier(),
                        declaration.capacity(), declaration.radioactive())
                : new PortKinds.HeatKind(id, declaration.ioType(), declaration.tier(), declaration.capacity());
    }

    private static IOPortKind kind(String id) {
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void source_input_requires_its_family_and_direction_and_reports_absence() {
        var spec = PortTierRequirementSpec.from(PortTiers.builder().anySourceInput().build());
        assertThat(spec.validate(List.of(sourceKind(IOType.INPUT)))).isEmpty();
        assertThat(spec.validate(List.of(sourceKind(IOType.OUTPUT), kind("energy_input_hatch_ultimate"))))
                .hasValueSatisfying(failure -> {
                    assertThat(failure.requirement().id()).isEqualTo("source_input_interface>=normal");
                    assertThat(failure.actualPortIds()).isEmpty();
                });
        assertThat(spec.validate(List.of())).hasValueSatisfying(failure ->
                assertThat(failure.requirement().category()).isEqualTo(PortTierRequirementSpec.PortCategory.SOURCE));
        var output = PortTierRequirementSpec.builder().anySourceOutput().build();
        assertThat(output.validate(List.of(sourceKind(IOType.OUTPUT)))).isEmpty();
        assertThat(output.validate(List.of(sourceKind(IOType.INPUT)))).isPresent();
    }

    @Test
    void source_tier_is_fixed_at_normal_in_both_declaration_layers() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PortTiers.Requirement(
                PortTiers.PortCategory.SOURCE, IOType.INPUT, 1, "normal"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortTiers.Requirement(
                PortTiers.PortCategory.SOURCE, IOType.INPUT, 0, "tiny"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortTierRequirementSpec.Requirement(
                PortTierRequirementSpec.PortCategory.SOURCE, IOType.OUTPUT, 1, "normal"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortTierRequirementSpec.Requirement(
                PortTierRequirementSpec.PortCategory.SOURCE, IOType.OUTPUT, 0, "tiny"));
    }

    private static IOPortKind sourceKind(IOType direction) { return new SourceKind(direction); }

    /** Neutral family declaration avoids loading the unfinished Ars port implementation.
     * @author howxu <dev@howxu.cn>
     */
    private record SourceKind(IOType ioType) implements IOPortKind {
        public String id() { return "source_" + ioType.getSerializedName() + "_interface"; }
        public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() {
            return PortKinds.ITEM_INPUT.entityFactory();
        }
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(ArsSourceIds.SOURCE, ioType, 0, List.of(id())));
        }
        public PortDefinition definition() {
            return PortDefinition.of(MMCR.id(id()), List.of(IOPortKind.binding(
                    new CapabilityType(ArsSourceIds.SOURCE), ioType, families())));
        }
    }

    private static IOPortKind combinedKind(List<PortFamilyDescriptor> families) {
        return combinedKind(IOType.INPUT, families);
    }

    private static IOPortKind ordinaryCombinedKind(IOType ioType) {
        return combinedKind(ioType, List.of(itemFamily(ioType), fluidFamily(ioType)));
    }

    private static IOPortKind combinedKind(IOType ioType, List<PortFamilyDescriptor> families) {
        return new PortKinds.CombinedKind("combined_" + ioType.getSerializedName() + "_test", ioType, families,
                ItemInputBusBlockEntity::new, PortDefinition.of(
                        MMCR.id("combined_" + ioType.getSerializedName() + "_test"),
                        families.stream().map(family -> IOPortKind.binding(
                                family.familyId().equals(PortFamilyIds.ITEM)
                                        ? BuiltinCapabilityDefinitions.ITEM_TYPE
                                        : BuiltinCapabilityDefinitions.FLUID_TYPE,
                                ioType, families)).toList()));
    }

    private static void assertInvalidCombined(List<PortFamilyDescriptor> families) {
        assertThatIllegalArgumentException().isThrownBy(() -> combinedKind(IOType.INPUT, families));
    }

    private static void assertInvalidCombined(List<PortFamilyDescriptor> families, List<CapabilityType> types) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PortKinds.CombinedKind("combined_invalid_test",
                IOType.INPUT, families, ItemInputBusBlockEntity::new, PortDefinition.of(
                        MMCR.id("combined_invalid_test"), types.stream()
                                .map(type -> IOPortKind.binding(type, IOType.INPUT, families)).toList())));
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
