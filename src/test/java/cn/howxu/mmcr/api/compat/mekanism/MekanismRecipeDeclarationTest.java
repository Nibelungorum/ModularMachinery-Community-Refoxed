package cn.howxu.mmcr.api.compat.mekanism;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Mekanism-neutral recipe declaration values.
 *
 * @author howxu <dev@howxu.cn>
 */
class MekanismRecipeDeclarationTest {
    @BeforeEach
    void clear_registry_before_test() {
        FailureReasonRegistry.clearForTesting();
    }

    @AfterEach
    void clear_registry_after_test() {
        FailureReasonRegistry.clearForTesting();
    }

    @Test
    void chemical_declarations_distinguish_exact_id_and_tag() {
        assertEquals(ChemicalIngredient.Kind.CHEMICAL,
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000).kind());
        assertEquals(ChemicalIngredient.Kind.TAG,
                ChemicalIngredient.tag(ResourceLocation.parse("mekanism:fuels"), 1_000).kind());
        assertThrows(IllegalArgumentException.class,
                () -> ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 0));
    }

    @Test
    void chemical_output_and_heat_declarations_validate_values() {
        ChemicalOutput output = ChemicalOutput.of(ResourceLocation.parse("mekanism:oxygen"), 1_000, 0.5F);
        assertEquals(ResourceLocation.parse("mekanism:oxygen"), output.id());
        assertEquals(1_000, output.amount());
        assertEquals(0.5F, output.chance());
        assertEquals(HeatRequirement.Kind.MINIMUM_TEMPERATURE, HeatRequirement.minimumTemperature(300D).kind());
        assertEquals(300D, HeatRequirement.minimumTemperature(300D).value());
        assertEquals(HeatRequirement.Kind.OUTPUT_HEAT, HeatRequirement.outputHeat(4D).kind());
        assertEquals(4D, HeatRequirement.outputHeat(4D).value());
        assertThrows(IllegalArgumentException.class,
                () -> ChemicalOutput.of(ResourceLocation.parse("mekanism:oxygen"), 1_000, 1.1F));
        assertThrows(IllegalArgumentException.class, () -> HeatRequirement.minimumTemperature(-1D));
    }

    @Test
    void port_family_ids_are_stable() {
        assertEquals(ResourceLocation.parse("mekanism:chemical"), MekanismPortFamilies.CHEMICAL);
        assertEquals(ResourceLocation.parse("mekanism:temperature"), MekanismPortFamilies.HEAT_TEMPERATURE);
        assertEquals(ResourceLocation.parse("mekanism:heat"), MekanismPortFamilies.HEAT);
    }

    @Test
    void production_initialization_registers_all_failure_reasons() {
        BuiltinFailureReasons.register();
        MekanismBridgeBootstrap.bootstrap();

        assertRegistered(MekanismFailureReasons.MEKANISM_UNAVAILABLE,
                "gui.mmcr.failure.mekanism_unavailable");
        assertRegistered(MekanismFailureReasons.CHEMICAL_INPUT_MISSING,
                "gui.mmcr.failure.chemical_input_missing");
        assertRegistered(MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED,
                "gui.mmcr.failure.chemical_output_blocked");
        assertRegistered(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH,
                "gui.mmcr.failure.chemical_type_mismatch");
        assertRegistered(MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED,
                "gui.mmcr.failure.chemical_radioactivity_rejected");
        assertRegistered(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT,
                "gui.mmcr.failure.heat_temperature_insufficient");
        assertRegistered(MekanismFailureReasons.HEAT_INPUT_MISSING,
                "gui.mmcr.failure.heat_input_missing");
        assertRegistered(MekanismFailureReasons.HEAT_OUTPUT_BLOCKED,
                "gui.mmcr.failure.heat_output_blocked");

        assertEquals(BuiltinFailureReasons.MISSING_INPUT.priority(),
                MekanismFailureReasons.CHEMICAL_INPUT_MISSING.priority());
        assertEquals(BuiltinFailureReasons.MISSING_OUTPUT.priority(),
                MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED.priority());
        assertTrue(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT.priority()
                > MekanismFailureReasons.CHEMICAL_INPUT_MISSING.priority());

        FailureReason reason = MekanismFailureReasons.CHEMICAL_INPUT_MISSING;
        ExecutionStatus status = ExecutionStatus.blocked(reason.id(), reason.id(),
                FailureOccurrence.at(reason, reason.id(), FailurePhase.REQUIREMENT_PLAN, null, null, Map.of()));
        assertEquals(reason, status.reason());
        assertThrows(IllegalArgumentException.class, MekanismFailureReasons::register);
    }

    @Test
    void frozen_registry_rejects_a_new_extension_reason() {
        BuiltinFailureReasons.register();
        MekanismFailureReasons.register();
        FailureReasonRegistry.freeze();

        assertThrows(IllegalStateException.class, () -> FailureReasonRegistry.register(
                new FailureReason(ResourceLocation.parse("mmcr_test:extension_reason"),
                        "gui.mmcr.failure.extension_reason", 50)));
    }

    private static void assertRegistered(FailureReason reason, String translationKey) {
        assertEquals(translationKey, reason.translationKey());
        assertEquals(reason, FailureReasonRegistry.find(reason.id()));
    }
}
