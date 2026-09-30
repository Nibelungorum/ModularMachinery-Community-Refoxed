package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Mekanism recipe values consume smart-interface recipe modifiers.
 *
 * @author howxu <dev@howxu.cn>
 */
class MekanismSmartInterfaceModifierTest {
    @AfterEach
    void resetHandlers() {
        LoadedChemicalRequirement.installUnavailableHandler();
        LoadedHeatRequirement.installUnavailableHandler();
    }

    @Test
    void chemical_requirements_apply_amount_and_chance_modifiers() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        LoadedChemicalRequirement input = LoadedChemicalRequirement.input(
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L));
        LoadedChemicalRequirement output = LoadedChemicalRequirement.output(
                ResourceLocation.parse("mekanism:hydrogen"), 200L, 1F);
        List<RecipeModifier> modifiers = List.of(
                new RecipeModifier("chemical", RecipeModifier.IOType.INPUT, 0.5F,
                        RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("chemical", RecipeModifier.IOType.INPUT, 0.25F,
                        RecipeModifier.Operation.MULTIPLY, true),
                new RecipeModifier("", RecipeModifier.IOType.OUTPUT, 2F,
                        RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("", RecipeModifier.IOType.OUTPUT, 0.5F,
                        RecipeModifier.Operation.MULTIPLY, true));

        LoadedChemicalRequirement modifiedInput = LoadedChemicalRequirement.TYPE.applyModifiers(input, modifiers);
        LoadedChemicalRequirement modifiedOutput = LoadedChemicalRequirement.TYPE.applyModifiers(output, modifiers);
        LoadedChemicalOutput modifiedMachineOutput = new LoadedChemicalOutput(
                ResourceLocation.parse("mekanism:hydrogen"), 200L, 1F).applyModifiers(modifiers);

        assertThat(modifiedInput.ingredient().amount()).isEqualTo(500L);
        assertThat(modifiedInput.consumeChance()).isEqualTo(0.25F);
        assertThat(modifiedOutput.ingredient().amount()).isEqualTo(400L);
        assertThat(modifiedOutput.chance()).isEqualTo(0.5F);
        assertThat(modifiedMachineOutput.amount()).isEqualTo(400L);
        assertThat(modifiedMachineOutput.chance()).isEqualTo(0.5F);
    }

    @Test
    void heat_requirements_and_outputs_apply_value_modifiers() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        List<RecipeModifier> modifiers = List.of(
                new RecipeModifier("heat", RecipeModifier.IOType.INPUT, 2F,
                        RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("", RecipeModifier.IOType.OUTPUT, 3F,
                        RecipeModifier.Operation.MULTIPLY, false));

        LoadedHeatRequirement input = LoadedHeatRequirement.TEMPERATURE_TYPE.applyModifiers(
                LoadedHeatRequirement.minimumTemperature(350D), modifiers);
        LoadedHeatRequirement output = LoadedHeatRequirement.HEAT_TYPE.applyModifiers(
                LoadedHeatRequirement.outputHeat(5D), modifiers);
        LoadedHeatOutput machineOutput = new LoadedHeatOutput(5D).applyModifiers(modifiers);

        assertThat(input.heat().value()).isEqualTo(700D);
        assertThat(output.heat().value()).isEqualTo(15D);
        assertThat(machineOutput.heat()).isEqualTo(15D);
    }

    @Test
    void chemical_amount_modifiers_clamp_non_positive_and_overflow_values() {
        List<RecipeModifier> negative = List.of(new RecipeModifier("chemical", RecipeModifier.IOType.INPUT,
                -Float.MAX_VALUE, RecipeModifier.Operation.MULTIPLY, false));
        List<RecipeModifier> overflow = List.of(new RecipeModifier("chemical", RecipeModifier.IOType.INPUT,
                Float.MAX_VALUE, RecipeModifier.Operation.MULTIPLY, false));

        assertThat(IntegrationTypeHelper.applyChemical(
                negative, Integer.MAX_VALUE, RecipeModifier.IOType.INPUT)).isEqualTo(1L);
        assertThat(IntegrationTypeHelper.applyChemical(
                overflow, Integer.MAX_VALUE, RecipeModifier.IOType.INPUT)).isEqualTo(Integer.MAX_VALUE);
    }
}
