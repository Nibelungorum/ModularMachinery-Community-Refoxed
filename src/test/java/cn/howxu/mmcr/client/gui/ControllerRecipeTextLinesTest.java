package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies controller recipe output text construction.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerRecipeTextLinesTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void outputOverflowUsesTheLastOfSixtyFourLinesForTheMoreMarker() {
        List<MachineOutputAmount> outputs = IntStream.range(0, 65)
                .mapToObj(index -> new MachineOutputAmount(
                        new MachineOutput.ItemOutput(new ItemStack(Items.STONE, index + 1), 1F), index + 1L))
                .toList();

        List<ControllerTextLine> lines = ControllerRecipeTextLines.outputs(outputs);

        assertThat(lines).hasSize(64);
        assertThat(lines.getLast().text())
                .isEqualTo(Component.translatable("gui.mmcr.controller.recipe_output.more"));
    }

    @Test
    void exactlySixtyFourOutputsDoesNotShowTheMoreMarker() {
        List<MachineOutputAmount> outputs = IntStream.range(0, 64)
                .mapToObj(index -> new MachineOutputAmount(
                        new MachineOutput.ItemOutput(new ItemStack(Items.STONE, index + 1), 1F), index + 1L))
                .toList();

        List<ControllerTextLine> lines = ControllerRecipeTextLines.outputs(outputs);

        assertThat(lines).hasSize(64);
        assertThat(lines.getLast().text())
                .isNotEqualTo(Component.translatable("gui.mmcr.controller.recipe_output.more"));
    }

    @Test
    void itemOutputUsesItsStackAsTheNativeIcon() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);

        ControllerTextLine line = ControllerRecipeTextLines.outputs(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(stack, 1F), 3L))).getFirst();

        assertThat(line.icon()).isInstanceOfSatisfying(ControllerTextLine.ItemIcon.class,
                icon -> assertThat(icon.stack().is(Items.DIAMOND)).isTrue());
    }

    @Test
    void itemOutputTextAndTooltipPreserveVanillaRarityStyle() {
        ItemStack stack = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Styled apple").withStyle(ChatFormatting.AQUA));
        Component styledName = Component.empty().append(stack.getHoverName())
                .withStyle(stack.getRarity().getStyleModifier()).withStyle(ChatFormatting.ITALIC);

        ControllerTextLine line = ControllerRecipeTextLines.outputs(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(stack, 1F), 1L))).getFirst();

        assertThat(line.text()).isEqualTo(Component.translatable(
                "gui.mmcr.controller.recipe_output.item", "", styledName));
        assertThat(line.tooltip().getFirst()).isEqualTo(styledName);
    }

    @Test
    void plainTextDoesNotReserveSpaceForAnIcon() {
        assertThat(new ControllerTextLine(Component.literal("external"), 0xFFFFFFFF).textXOffset()).isZero();
    }

    @Test
    void itemOutputAppliesDeclaredRarityToNameTooltipAndIcon() {
        ItemStack stack = new ItemStack(Items.STONE);
        var components = new DataComponentPredicateSet(Map.of(DataComponents.RARITY,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive("rare")))));
        var output = new MachineOutput.ItemOutput(stack, 1F, components);

        ControllerTextLine line = ControllerRecipeTextLines.outputs(List.of(
                new MachineOutputAmount(output, 1L))).getFirst();

        Component name = Component.empty().append(stack.getHoverName()).withStyle(ChatFormatting.AQUA);
        assertThat(name.getStyle().getColor().getValue()).isEqualTo(ChatFormatting.AQUA.getColor());
        assertThat(line.text()).isEqualTo(Component.translatable(
                "gui.mmcr.controller.recipe_output.item", "", name));
        assertThat(line.tooltip().getFirst()).isEqualTo(name);
        assertThat(line.icon()).isInstanceOfSatisfying(ControllerTextLine.ItemIcon.class,
                icon -> assertThat(icon.stack().getRarity()).isEqualTo(Rarity.RARE));
        assertThat(stack.getRarity()).isEqualTo(Rarity.COMMON);
    }

    @Test
    void recipeOutputsAreScaledByTheSelectedRuntimeParallelism() {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_recipe_text"), MMCR.id("test_cube"), 1,
                List.of(), List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 2), 1F)),
                List.of(), 0, 1, false, false, false, Set.of());

        assertThat(ControllerRecipeTextLines.forRecipe(recipe, 3L))
                .extracting(ControllerTextLine::text)
                .contains(Component.translatable("gui.mmcr.controller.recipe_output.item", "6 ",
                        Component.empty().append(new ItemStack(Items.DIAMOND).getHoverName())
                                .withStyle(Rarity.COMMON.getStyleModifier())));
    }

    @Test
    void presentationCreatesSummaryTooltipsBeforeResourceOutputs() {
        ControllerRecipePresentation presentation = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 1), 1F), 3L)),
                400L, 200L, 10D);

        List<ControllerTextLine> lines = ControllerRecipeTextLines.create(presentation);

        assertThat(lines).hasSize(5);
        assertThat(lines.get(0).text()).isEqualTo(Component.translatable("gui.mmcr.controller.recipe.energy_input", "400"));
        assertThat(lines.get(0).tooltip()).containsExactly(
                Component.translatable("gui.mmcr.controller.recipe.energy_input_exact", "400"));
        assertThat(lines.get(3).text())
                .isEqualTo(Component.translatable("gui.mmcr.controller.recipe_output.title"));
        assertThat(lines.get(4).icon()).isInstanceOf(ControllerTextLine.ItemIcon.class);
    }

    @Test
    void presentationAddsAnIndentedResourceOutputSectionAfterSummaries() {
        ControllerRecipePresentation presentation = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F), 1L)),
                400L, 0L, 0D);

        List<ControllerTextLine> lines = ControllerRecipeTextLines.create(presentation);

        assertThat(lines).extracting(ControllerTextLine::text).containsExactly(
                Component.translatable("gui.mmcr.controller.recipe.energy_input", "400"),
                Component.translatable("gui.mmcr.controller.recipe_output.title"),
                Component.translatable("gui.mmcr.controller.recipe_output.item", "",
                         Component.empty().append(new ItemStack(Items.DIAMOND).getHoverName())
                                 .withStyle(Rarity.COMMON.getStyleModifier())));
        assertThat(lines.get(0).leftIndent()).isZero();
        assertThat(lines.get(1).leftIndent()).isZero();
        assertThat(lines.get(2).leftIndent()).isEqualTo(4);
    }

    @Test
    void fluidAndChemicalOutputsUseNativeIconDescriptors() {
        MekanismBridgeBootstrap.installForTesting(new MekanismBridge() {
            @Override public boolean available() { return true; }
            @Override public boolean supportsPortFamily(net.minecraft.resources.ResourceLocation familyId) { return false; }
            @Override public net.minecraft.resources.ResourceLocation unavailableReason() { return null; }
            @Override public void registerRecipeTypes(net.minecraft.resources.ResourceLocation chemical,
                                                      net.minecraft.resources.ResourceLocation heatTemperature,
                                                      net.minecraft.resources.ResourceLocation heat) {}
            @Override public ChemicalRenderData chemicalRenderData(net.minecraft.resources.ResourceLocation chemicalId) {
                return new ChemicalRenderData(chemicalId, 0xFFFFFFFF, Component.literal("Test chemical"));
            }
        });
        try {
            ControllerRecipePresentation presentation = new ControllerRecipePresentation(List.of(
                    new MachineOutputAmount(new MachineOutput.FluidOutput(
                            new FluidStack(Fluids.WATER, 1_000), 1F), 1_000L),
                    new MachineOutputAmount(new LoadedChemicalOutput(MMCR.id("test_chemical"), 1L, 1F), 1L)),
                    0L, 0L, 0D);

            List<ControllerTextLine> lines = ControllerRecipeTextLines.create(presentation);

            assertThat(lines).extracting(ControllerTextLine::icon)
                    .anySatisfy(icon -> assertThat(icon).isInstanceOf(ControllerTextLine.FluidIcon.class))
                    .anySatisfy(icon -> assertThat(icon).isInstanceOf(ControllerTextLine.ChemicalIcon.class));
        } finally {
            MekanismBridgeBootstrap.resetForTesting();
        }
    }
}
