package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.datagen.Translations;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
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

import static org.assertj.core.api.Assertions.assertThat;

class MachineRecipeDisplayTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void displayIncludesItemFluidEnergyAndDuration() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_display"),
                MMCR.id("blast_furnace"),
                120,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 8),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.EnergyIngredient(40)
                ),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 4)),
                List.of(),
                3,
                1,
                true,
                List.of(new FluidStack(Fluids.LAVA.builtInRegistryHolder(), 125))
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.recipeId()).isEqualTo(MMCR.id("jei_display"));
        assertThat(display.machineId()).isEqualTo(MMCR.id("blast_furnace"));
        assertThat(display.durationTicks()).isEqualTo(120);
        assertThat(display.itemInputs()).hasSize(1);
        assertThat(display.itemInputCounts()).containsExactly(8);
        assertThat(display.itemOutputs()).singleElement().satisfies(output -> {
            assertThat(output.is(Items.IRON_NUGGET)).isTrue();
            assertThat(output.getCount()).isEqualTo(4);
        });
        assertThat(display.fluidInputs()).hasSize(1);
        assertThat(display.fluidInputAmounts()).containsExactly(250);
        assertThat(display.fluidOutputs()).hasSize(1);
        assertThat(display.energyInputs()).containsExactly(new EnergyIngredient(40, true));
        assertThat(display.energyOutputs()).isEmpty();
    }

    @Test
    void displayUsesRuntimeOutputsAfterModifiers() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_modifier_display"),
                MMCR.id("blast_furnace"),
                80,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 2)),
                List.of(new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 3.0F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.outputs()).hasOnlyElementsOfType(MachineOutput.ItemOutput.class);
        assertThat(display.itemOutputs()).extracting(ItemStack::getCount).containsExactly(6);
    }

    @Test
    void displaysAllRecipesInDeterministicOrder() {
        RecipeRegistry.clearForTesting();
        MachineRecipe low = recipe("low", "blast_furnace", 0);
        MachineRecipe high = recipe("high", "blast_furnace", 10);
        MachineRecipe other = recipe("other", "other_machine", 5);
        RecipeRegistry.register(low);
        RecipeRegistry.register(other);
        RecipeRegistry.register(high);

        assertThat(MachineRecipeDisplays.all())
                .extracting(MachineRecipeDisplay::recipeId)
                .containsExactly(MMCR.id("high"), MMCR.id("low"), MMCR.id("other"));

        assertThat(MachineRecipeDisplays.byMachine())
                .containsOnlyKeys(MMCR.id("blast_furnace"), MMCR.id("other_machine"));
        assertThat(MachineRecipeDisplays.byMachine().get(MMCR.id("blast_furnace")))
                .extracting(MachineRecipeDisplay::recipeId)
                .containsExactly(MMCR.id("high"), MMCR.id("low"));
    }

    @Test
    void layoutPlacesFluidsBeforeItemsAndCapsOverflow() {
        MachineRecipeDisplay display = MachineRecipeDisplay.from(new MachineRecipe(
                MMCR.id("jei_layout"),
                MMCR.id("blast_furnace"),
                20,
                List.of(
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 100),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.LAVA), 200),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 3)
                ),
                List.of(
                        new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1),
                        new ItemStack(Holder.direct(Items.GOLD_NUGGET, DataComponentMap.EMPTY), 2)
                ),
                List.of(),
                0,
                1,
                true,
                List.of(new FluidStack(Fluids.WATER.builtInRegistryHolder(), 100))
        ));
        MachineRecipeDisplay overflow = MachineRecipeDisplay.from(new MachineRecipe(
                MMCR.id("jei_overflow"),
                MMCR.id("blast_furnace"),
                20,
                java.util.stream.IntStream.range(0, 33)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1))
                        .toList(),
                List.of(),
                List.of(),
                0,
                1
        ));

        assertThat(MachineRecipeLayout.forDisplay(display).inputs().slots()).hasSize(5);
        assertThat(MachineRecipeLayout.forDisplay(display).inputs().slots())
                .extracting(slot -> slot.entry().kind())
                .startsWith(MachineRecipeLayout.Kind.FLUID, MachineRecipeLayout.Kind.FLUID);
        MachineRecipeLayout overflowLayout = MachineRecipeLayout.forDisplay(overflow);
        assertThat(overflowLayout.inputs().slots()).hasSize(17);
        assertThat(overflowLayout.inputs().overflowSlot()).isNotNull();
        assertThat(overflowLayout.inputs().hiddenEntries()).hasSize(16);
    }

    @Test
    void translationsIncludeOverflowTooltips() {
        assertThat(Translations.ALL.get("en_us"))
                .containsKeys("jei.mmcr.machine_recipe.input_overflow", "jei.mmcr.machine_recipe.output_overflow",
                        "jei.mmcr.machine_recipe.overflow_entry");
        assertThat(Translations.ALL.get("zh_cn"))
                .containsEntry("jei.mmcr.machine_recipe.output_overflow", "其余产物：")
                .containsKeys("jei.mmcr.machine_recipe.input_overflow", "jei.mmcr.machine_recipe.overflow_entry");
    }

    @Test
    void outputOverflowNameFallsBackToItemDescriptionWhenHoverNameIsEmpty() {
        ItemStack stack = new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 3);

        assertThat(MachineRecipeCategory.outputStackName(stack).getString()).isNotEmpty();
    }

    @Test
    void levelRequirementCyclesEligibleLevelsEverySecond() {
        var typeId = MMCR.id("coil");
        var copperId = MMCR.id("copper");
        var ironId = MMCR.id("iron");
        var goldId = MMCR.id("gold");
        var diamondId = MMCR.id("diamond");
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(typeId, Component.literal("Coils")));
        registerLevel(copperId, typeId, 0, Blocks.COPPER_BLOCK);
        registerLevel(ironId, typeId, 1, Blocks.IRON_BLOCK);
        registerLevel(goldId, typeId, 2, Blocks.GOLD_BLOCK);
        registerLevel(diamondId, typeId, 3, Blocks.DIAMOND_BLOCK);
        MachineLevelRegistry.freezeRegistration();
        LevelRequirement requirement = new LevelRequirement(typeId, ironId);

        assertThat(MachineRecipeCategory.levelRequirement(requirement, 0).getString())
                .isEqualTo("Coils: Block of Ironjei.mmcr.machine_recipe.minimum_level");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 20).getString()).isEqualTo("Coils: Block of Gold");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 40).getString()).isEqualTo("Coils: Block of Diamond");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 60).getString()).isEqualTo("Coils: Block of Gold");
        assertThat(MachineRecipeCategory.levelRequirement(requirement, 80).getString())
                .isEqualTo("Coils: Block of Ironjei.mmcr.machine_recipe.minimum_level");
        assertThat(Translations.ALL.get("en_us").get("jei.mmcr.machine_recipe.minimum_level"))
                .isEqualTo(" (minimum level)");
        assertThat(Translations.ALL.get("zh_cn").get("jei.mmcr.machine_recipe.minimum_level"))
                .isEqualTo(" (最低等级)");
    }

    private static MachineRecipe recipe(String id, String machine, int priority) {
        return new MachineRecipe(
                MMCR.id(id),
                MMCR.id(machine),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                List.of(),
                priority,
                1
        );
    }

    private static void registerLevel(net.minecraft.resources.Identifier id, net.minecraft.resources.Identifier typeId,
                                      int priority, net.minecraft.world.level.block.Block block) {
        MachineLevelRegistry.registerLevel(new MachineLevel(id, typeId, priority,
                new BlockPredicate.OfBlockState(block.defaultBlockState()),
                new ItemStack(Holder.direct(block.asItem(), DataComponentMap.EMPTY)), LevelModifier.IDENTITY));
    }
}
