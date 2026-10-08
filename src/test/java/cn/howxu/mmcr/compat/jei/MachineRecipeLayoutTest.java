package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.Set;

import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class MachineRecipeLayoutTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void layoutPlansFluidThenItemInputsAcrossThreeColumns() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_layout"),
                MMCR.id("blast_furnace"),
                100,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 1),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.LAVA), 250),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COPPER_INGOT), 1)
                ),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET), 1)),
                List.of(),
                0,
                1,
                true,
                List.of(new FluidStack(Fluids.LAVA.builtInRegistryHolder(), 125))
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots())
                .extracting(slot -> slot.entry().kind(), slot -> slot.entry().index(),
                        MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(
                        tuple(MachineRecipeLayout.Kind.FLUID, 0, 12, 8), tuple(MachineRecipeLayout.Kind.FLUID, 1, 30, 8),
                        tuple(MachineRecipeLayout.Kind.ITEM, 0, 48, 8), tuple(MachineRecipeLayout.Kind.ITEM, 1, 30, 26),
                        tuple(MachineRecipeLayout.Kind.ITEM, 2, 48, 26));
        assertThat(layout.width()).isEqualTo(150);
        assertThat(layout.height()).isEqualTo(150);
        assertThat(layout.durationTextX()).isEqualTo(8);
        assertThat(layout.durationTextY()).isEqualTo(48);
        assertThat(layout.outputs().slots())
                .extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(102, 17), tuple(120, 17));
        assertThat(MachineRecipeDisplay.from(recipe).entries()).extracting(JeiDisplayEntry::role)
                .containsOnly(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT);
    }

    @Test
    void everyEntryGetsARealSlotRegardlessOfGuiScale() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_full_output_layout"), MMCR.id("large_machine"), 200,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(), List.of(), 0, 1, true,
                IntStream.range(0, 64)
                        .mapToObj(index -> new FluidStack(Fluids.WATER.builtInRegistryHolder(), 125)).toList());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        for (int scale : List.of(1, 2, 3, 4)) {
            MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, scale);
            assertThat(layout.inputs().slots()).hasSize(1);
            assertThat(layout.outputs().slots()).hasSize(64);
            assertThat(layout.outputs().slots()).extracting(slot -> slot.entry().index())
                    .containsExactlyElementsOf(IntStream.range(0, 64).boxed().toList());
            assertThat(layout.outputs().slots()).allSatisfy(slot -> {
                assertThat(slot.entry().kind()).isEqualTo(MachineRecipeLayout.Kind.FLUID);
                assertThat(slot.entry().displayEntry().role()).isEqualTo(RecipeIngredientRole.OUTPUT);
            });
            assertThat(layout.outputs().slots().getLast().y()).isEqualTo(178);
            assertThat(layout.durationTextY()).isEqualTo(200);
        }
    }

    @Test
    void emptyInputAndOutputRegionsReserveTextSpaceWithoutSlots() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_layout_empty"), MMCR.id("blast_furnace"), 100,
                List.of(), List.of(), List.of(), 0, 1, true, List.of());

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots()).isEmpty();
        assertThat(layout.outputs().slots()).isEmpty();
        assertThat(layout.arrow()).isNull();
        assertThat(layout.durationTextY()).isEqualTo(30);
    }

    @Test
    void levelRequirementRowsFollowTheEnergyRows() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_level_layout"), MMCR.id("blast_furnace"), 100,
                List.of(new MachineIngredient.EnergyIngredient(40)), List.of(), List.of(), 0, 1,
                false, List.of());
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.levelRequirementY(MachineRecipeDisplay.from(recipe), 0))
                .isEqualTo(layout.durationTextY() + 20);
    }

    @Test
    void levelRequirementSlotStartsAfterThePrecedingMetadataTextRow() {
        TestBootstrap.beginRegistration();
        ResourceLocation coilType = MMCR.id("layout_coil_type");
        ResourceLocation casingType = MMCR.id("layout_casing_type");
        ResourceLocation coilLevel = MMCR.id("layout_coil_level");
        ResourceLocation casingLevel = MMCR.id("layout_casing_level");
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        TestBootstrap.registerType(new LevelType(casingType, Component.literal("Casing")));
        TestBootstrap.registerLevel(new MachineLevel(coilLevel, coilType, 0,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                new ItemStack(Holder.direct(Blocks.COPPER_BLOCK.asItem())), ModifierDefinition.EMPTY));
        TestBootstrap.registerLevel(new MachineLevel(casingLevel, casingType, 0,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new ItemStack(Holder.direct(Blocks.IRON_BLOCK.asItem())), ModifierDefinition.EMPTY));

        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_level_slot_layout"), MMCR.id("layout_test_machine"), 100,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(LevelRequirement.input(coilType, coilLevel), LevelRequirement.input(casingType, casingLevel)),
                false, List.of(), false, Set.of(MMCR.id("layout_host")));
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

        assertThat(layout.levelRequirementSlotY(display, 0)).isEqualTo(layout.hostRequirementTextY() + 10);
        assertThat(layout.levelRequirementSlotY(display, 1)).isEqualTo(layout.levelRequirementSlotY(display, 0) + 18);
        assertThat(layout.smartInterfaceTextY(display)).isEqualTo(layout.levelRequirementSlotY(display, 1) + 18);
        assertThat(layout.lastMetadataTextY(display)).isEqualTo(layout.levelRequirementSlotY(display, 1));
        assertThat(layout.informationTextY(display)).isEqualTo(layout.levelRequirementSlotY(display, 1) + 18);
    }

    @Test
    void stageRequirementTextFollowsLevelSlotAndPrecedesSmartInterfaceText() {
        TestBootstrap.beginRegistration();
        ResourceLocation levelType = MMCR.id("stage_layout_level_type");
        ResourceLocation levelId = MMCR.id("stage_layout_level");
        TestBootstrap.registerType(new LevelType(levelType, Component.literal("Coils")));
        TestBootstrap.registerLevel(new MachineLevel(levelId, levelType, 0,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                new ItemStack(Holder.direct(Blocks.COPPER_BLOCK.asItem())), ModifierDefinition.EMPTY));
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_stage_layout"), MMCR.id("stage_layout_machine"), 100,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(LevelRequirement.input(levelType, levelId), StageRequirement.input(2),
                        SmartInterfaceRequirement.input("mode", 1F)),
                false, List.of(), false, Set.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

        assertThat(layout.stageRequirementTextY(display))
                .isEqualTo(layout.levelRequirementSlotY(display, 0) + 18);
        assertThat(layout.smartInterfaceTextY(display)).isEqualTo(layout.stageRequirementTextY(display) + 10);
        assertThat(layout.lastMetadataTextY(display)).isEqualTo(layout.smartInterfaceTextY(display));
        assertThat(layout.informationTextY(display)).isEqualTo(layout.smartInterfaceTextY(display) + 10);
        assertThat(layout.informationLineCapacity(display, layout.informationTextY(display) + 9)).isZero();
        assertThat(layout.informationLineCapacity(display, layout.informationTextY(display) + 20)).isEqualTo(2);
    }

    @Test
    void metadataRowsReserveHostRequirementAfterEnergyRowsAndStayInsideRecipeHeight() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_host_layout"), MMCR.id("hosted_module"), 100,
                IntStream.range(0, 22)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1))
                        .toList(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET), 1)),
                List.of(), 0, 1, false, List.of(), List.of(), false, List.of(), Set.of(MMCR.id("host_a")));

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.hostRequirementTextY()).isEqualTo(120);
        assertThat(layout.durationTextY()).isEqualTo(110);
        assertThat(layout.lastMetadataTextY(MachineRecipeDisplay.from(recipe))).isLessThan(layout.height());
    }

    @Test
    void hostRequirementFollowsEnergyOutputRows() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_host_after_energy"), MMCR.id("hosted_module"), 100,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                 List.<MachineRequirement>of(
                         new EnergyRequirement(RecipeModifier.IOType.INPUT, 40),
                         new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 80),
                         new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 120)),
                 false, List.of(), false, Set.of(MMCR.id("host_a")));
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

        assertThat(layout.hostRequirementTextY())
                .isEqualTo(layout.durationTextY()
                        + 10 * (1 + display.energyInputs().size() + display.energyOutputs().size()));
        assertThat(layout.levelRequirementY(display, 0)).isEqualTo(layout.hostRequirementTextY() + 10);
        assertThat(layout.smartInterfaceTextY(display)).isEqualTo(layout.hostRequirementTextY() + 10);
        assertThat(layout.lastMetadataTextY(display)).isEqualTo(layout.hostRequirementTextY());
    }

}
