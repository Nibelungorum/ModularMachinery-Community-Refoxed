package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.data.registries.VanillaRegistries;

import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.LinkedAppearanceBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.stream.Stream;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeCraftingContextTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void doesNotCacheControllerLevelBecauseRestoredContextsMayBeCreatedBeforeLevelBinding() {
        boolean cachesLevel = Arrays.stream(RecipeCraftingContext.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("level"));

        assertThat(cachesLevel).isFalse();
    }

    @Test
    void simulateInputsAggregatesMatchingItemsAcrossMultipleInputBuses() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity first = itemInputBus(new BlockPos(1, 0, 0));
        ItemInputBusBlockEntity second = itemInputBus(new BlockPos(2, 0, 0));
        first.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(3));
        second.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(first, second);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "multi_bus_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5)),
                List.of()
        );

        assertThat(new RecipeCraftingContext(controller).simulateInputs(recipe)).isTrue();
    }

    @Test
    void commitInputsFailsWhenDuplicateIngredientsExceedCombinedInput() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(5));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "duplicate_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 5)
                ),
                List.of()
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isFalse();
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(5);
    }

    @Test
    void simulateOutputsReturnsFalseWhenOutputBusHasNoRoom() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "blocked_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(Items.IRON_INGOT.getDefaultInstance())
        );

        assertThat(new RecipeCraftingContext(controller).simulateOutputs(recipe)).isFalse();
    }

    @Test
    void explicitItemInputRequirementRunsWhenLegacyInputsAreEmpty() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = explicitItemRecipe(
                "explicit_input",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void sharedInputStartsOnlyTheParallelismThatCanBeCommitted() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(10));
        MachineControllerBlockEntity first = controllerWithComponents(input);
        MachineControllerBlockEntity second = controllerWithComponents(input);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "shared_start"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);

        int firstParallel = new RecipeCraftingContext(first).commitStart(recipe, 8);
        int secondParallel = new RecipeCraftingContext(second).commitStart(recipe, 8);

        assertThat(firstParallel + secondParallel).isEqualTo(10);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void mixedShapeItemInputRuntimeUsesExplicitRequirements() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "mixed_item_input_runtime"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 2)),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void missingItemInputRecordsStructuredRequirementFailure() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = explicitItemRecipe(
                "structured_input_failure",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 3, ItemStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isFalse();
        assertThat(context.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
            assertThat(failure.requirementIndex()).isZero();
            assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.MISSING_INPUT);
            assertThat(failure.required()).isEqualTo(3);
            assertThat(failure.available()).isEqualTo(1);
            assertThat(failure.shortAmount()).isEqualTo(2);
        });
    }

    @Test
    void simulateOutputsFailsWhenDuplicateOutputsExceedCombinedRoom() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "duplicate_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(
                        Items.IRON_INGOT.getDefaultInstance().copyWithCount(64),
                        Items.IRON_INGOT.getDefaultInstance()
                )
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isFalse();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void commitOutputsMergesMatchingStacksBeforeUsingEmptySlots() {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(10));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "merge_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(Items.IRON_INGOT.getDefaultInstance().copyWithCount(5))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(15);
        assertThat(output.getItemStackHandler(null).getStackInSlot(1).isEmpty()).isTrue();
    }

    @Test
    void commitOutputsPlansDifferentItemsAcrossSharedOutputBusSlots() {
        bindItemComponents(Items.IRON_NUGGET);
        bindItemComponents(Items.GOLD_NUGGET);
        bindItemComponents(Items.COPPER_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "multi_distinct_item_outputs"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(
                        Items.IRON_NUGGET.getDefaultInstance(),
                        Items.GOLD_NUGGET.getDefaultInstance(),
                        Items.COPPER_NUGGET.getDefaultInstance()
                )
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getItem()).isEqualTo(Items.IRON_NUGGET);
        assertThat(output.getItemStackHandler(null).getStackInSlot(1).getItem()).isEqualTo(Items.GOLD_NUGGET);
        assertThat(output.getItemStackHandler(null).getStackInSlot(2).getItem()).isEqualTo(Items.COPPER_NUGGET);
    }

    @Test
    void commitOutputsNormalizesLegacyVanillaStacksBeforeCapacityChecks() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemStack legacyOutput = new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1);
        assertThat(legacyOutput.getMaxStackSize()).isEqualTo(1);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, legacyOutput.copy());
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "legacy_vanilla_output_stack"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(legacyOutput.copy())
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(output.getItemStackHandler(null).getStackInSlot(1).isEmpty()).isTrue();
    }

    @Test
    void commitOutputsMergesLegacyEnchantedStacksUsingItemDefaultCapacity() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemStack legacyOutput = new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment("minecraft:sharpness"), 2);
        legacyOutput.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        assertThat(legacyOutput.getMaxStackSize()).isEqualTo(1);

        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, legacyOutput.copy());
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "legacy_enchanted_output_stack"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(legacyOutput.copy())
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(output.getItemStackHandler(null).getStackInSlot(1).isEmpty()).isTrue();
    }

    private static Holder<Enchantment> enchantment(String id) {
        return VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)));
    }

    @Test
    void explicitItemOutputRequirementRunsWhenLegacyOutputsAreEmpty() {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitItemRecipe(
                "explicit_output",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2)))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void zero_chance_item_output_does_not_insert_at_finish() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitItemRecipe(
                "zero_chance_item_output",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance().copyWithCount(3), 0F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void zero_chance_item_output_does_not_require_output_capacity() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitItemRecipe(
                "zero_chance_item_output_full_bus",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance().copyWithCount(3), 0F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getItem()).isEqualTo(Items.COBBLESTONE);
    }

    @Test
    void hundred_percent_item_output_inserts_at_finish() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitItemRecipe(
                "full_chance_item_output",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance().copyWithCount(3), 1F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(3);
    }

    @Test
    void zero_chance_fluid_output_does_not_insert_at_finish() {
        bindFluidComponents(Fluids.WATER);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "zero_chance_fluid_output",
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000), 0F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getMutableFluidStorage().getAmountAsLong()).isZero();
    }

    @Test
    void zero_chance_fluid_output_does_not_require_output_capacity() {
        bindFluidComponents(Fluids.WATER);
        bindFluidComponents(Fluids.LAVA);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        output.getMutableFluidStorage().setFluid(new FluidStack(Fluids.LAVA, (int) output.getMutableFluidStorage().getCapacityAsLong()));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "zero_chance_fluid_output_full_hatch",
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000), 0F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getMutableFluidStorage().getFluidStack().getFluid()).isEqualTo(Fluids.LAVA);
    }

    @Test
    void hundred_percent_fluid_output_inserts_at_finish() {
        bindFluidComponents(Fluids.WATER);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "full_chance_fluid_output",
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000), 1F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getMutableFluidStorage().getFluidStack()).satisfies(stack -> {
            assertThat(stack.getFluid()).isEqualTo(Fluids.WATER);
            assertThat(stack.getAmount()).isEqualTo(1000);
        });
    }

    @Test
    void partialItemOutputsFillAvailableRoomAndVoidRemainderInOrder() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(44));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = partialRequirementRecipe("partial_item_order", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(64)),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.GOLD_INGOT.getDefaultInstance())
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(output));

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(total(output, Items.IRON_INGOT)).isEqualTo(64);
        assertThat(total(output, Items.GOLD_INGOT)).isZero();
    }

    @Test
    void partialItemOutputContinuesAfterBlockedFirstRequirement() throws Exception {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        ItemOutputBusBlockEntity ironOnly = itemOutputBus(new BlockPos(1, 0, 0));
        ironOnly.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(64));
        for (int slot = 1; slot < ironOnly.getItemStackHandler(null).getSlots(); slot++) {
            ironOnly.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        ItemOutputBusBlockEntity goldRoom = itemOutputBus(new BlockPos(2, 0, 0));
        MachineRecipe recipe = partialRequirementRecipe("partial_item_continue", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance(), 1F, List.of("iron")),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.GOLD_INGOT.getDefaultInstance(), 1F, List.of("gold"))
        ));
        MachineControllerBlockEntity controller = controllerWithComponents(ironOnly, goldRoom);
        replaceComponents(controller, List.of(
                new ProcessingComponent(new MachineComponent(PortKinds.ITEM_OUTPUT, IOType.OUTPUT),
                        ironOnly, ironOnly.getBlockPos(), BlockPos.ZERO, List.of("iron")),
                new ProcessingComponent(new MachineComponent(PortKinds.ITEM_OUTPUT, IOType.OUTPUT),
                        goldRoom, goldRoom.getBlockPos(), BlockPos.ZERO, List.of("gold"))
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(total(ironOnly, Items.IRON_INGOT) + total(goldRoom, Items.IRON_INGOT)).isEqualTo(64);
        assertThat(total(ironOnly, Items.GOLD_INGOT) + total(goldRoom, Items.GOLD_INGOT)).isEqualTo(1);
    }

    @Test
    void unmarkedItemOutputRecipeRemainsStrictAndAtomic() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(44));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = explicitRequirementRecipe("strict_partial_item_blocked", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(64)),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.GOLD_INGOT.getDefaultInstance())
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(output));

        assertThat(context.simulateOutputs(recipe)).isFalse();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isFalse();
        assertThat(total(output, Items.IRON_INGOT)).isEqualTo(44);
        assertThat(total(output, Items.GOLD_INGOT)).isZero();
    }

    @Test
    void partialFluidAndItemOutputsFollowRequirementOrderAndVoidRemainders() throws Exception {
        bindFluidComponents(Fluids.WATER);
        bindFluidComponents(Fluids.LAVA);
        bindItemComponents(Items.IRON_INGOT);
        FluidOutputHatchBlockEntity water = fluidOutputHatch(new BlockPos(1, 0, 0));
        water.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 7400));
        ItemOutputBusBlockEntity item = itemOutputBus(new BlockPos(2, 0, 0));
        FluidOutputHatchBlockEntity lava = fluidOutputHatch(new BlockPos(3, 0, 0));
        MachineRecipe recipe = partialRequirementRecipe("partial_fluid_item_fluid", List.of(
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000), 1F, List.of("water")),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.LAVA, 1000), 1F, List.of("lava"))
        ));
        MachineControllerBlockEntity controller = controllerWithComponents(water, item, lava);
        replaceComponents(controller, List.of(
                new ProcessingComponent(new MachineComponent(PortKinds.FLUID_OUTPUT, IOType.OUTPUT),
                        water, water.getBlockPos(), BlockPos.ZERO, List.of("water")),
                new ProcessingComponent(new MachineComponent(PortKinds.ITEM_OUTPUT, IOType.OUTPUT),
                        item, item.getBlockPos(), BlockPos.ZERO, (String) null),
                new ProcessingComponent(new MachineComponent(PortKinds.FLUID_OUTPUT, IOType.OUTPUT),
                        lava, lava.getBlockPos(), BlockPos.ZERO, List.of("lava"))
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(water.getMutableFluidStorage().getAmountAsLong()).isEqualTo(8000);
        assertThat(item.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(lava.getMutableFluidStorage().getFluidStack()).satisfies(stack -> {
            assertThat(stack.getFluid()).isEqualTo(Fluids.LAVA);
            assertThat(stack.getAmount()).isEqualTo(1000);
        });
    }

    @Test
    void partialMixedOutputsPreserveFluidCompetitionOrderAcrossSharedHatches() {
        bindFluidComponents(Fluids.WATER);
        bindFluidComponents(Fluids.LAVA);
        bindItemComponents(Items.IRON_INGOT);
        FluidOutputHatchBlockEntity first = fluidOutputHatch(new BlockPos(1, 0, 0));
        FluidOutputHatchBlockEntity second = fluidOutputHatch(new BlockPos(2, 0, 0));
        ItemOutputBusBlockEntity item = itemOutputBus(new BlockPos(3, 0, 0));
        MachineRecipe recipe = partialRequirementRecipe("partial_mixed_shared_fluid_order", List.of(
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 8000)),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.LAVA, 8000))
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(first, second, item));

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(first.getMutableFluidStorage().getFluidStack().getFluid()).isEqualTo(Fluids.WATER);
        assertThat(second.getMutableFluidStorage().getFluidStack().getFluid()).isEqualTo(Fluids.LAVA);
        assertThat(item.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void partialOutputsRequireAtLeastOnePositiveItemOrFluidCapacity() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity full = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < full.getItemStackHandler(null).getSlots(); slot++) {
            full.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = partialRequirementRecipe("partial_no_positive_capacity", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance())
        ));

        assertThat(new RecipeCraftingContext(controllerWithComponents(full)).simulateOutputs(recipe)).isFalse();
        assertThat(new RecipeCraftingContext(controllerWithComponents()).simulateOutputs(partialRequirementRecipe(
                "partial_strict_only", List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 1))))).isFalse();
    }

    @Test
    void partialOutputSimulationDoesNotRollChanceAndDeterministicMissCommitIsValid() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = partialRequirementRecipe("partial_chance_miss", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance(), 0.1F, List.of())
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(
                controllerWithComponents(List.of(output), RandomSource.create(0L)));

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(total(output, Items.IRON_INGOT)).isZero();
    }

    @Test
    void publicCommitOutputsRoutesMarkedRecipesThroughPartialPlanner() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(44));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = partialRequirementRecipe("public_commit_partial_item", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        Items.IRON_INGOT.getDefaultInstance().copyWithCount(64))
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(output));

        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(total(output, Items.IRON_INGOT)).isEqualTo(64);
    }

    @Test
    void partialParallelOutputsScaleBeforeCapacityTruncationWithoutLoweringParallelism() {
        bindItemComponents(Items.COBBLESTONE);
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        output.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(44));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = partialRequirementRecipe("partial_parallel_item", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(64))
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(output));

        assertThat(context.simulateOutputs(recipe, 4)).isTrue();
        assertThat(context.startCrafting(recipe, 4)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 4)).isTrue();
        assertThat(total(output, Items.IRON_INGOT)).isEqualTo(64);
    }

    @Test
    void partialItemAndFluidOutputsDoNotLeakWhenStrictOutputFails() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        SmartInterfaceBlockEntity smartInterface = smartInterface(new BlockPos(2, 0, 0));
        setBlockEntityLevel(smartInterface, controllerWithComponents(output).getLevel());
        MachineControllerBlockEntity controller = controllerWithComponents(output, smartInterface);
        MachineRecipe recipe = partialRequirementRecipe("partial_strict_output_atomic", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                SmartInterfaceRequirement.output("mode", 9F)
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isFalse();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isFalse();
        assertThat(total(output, Items.IRON_INGOT)).isZero();
    }

    @Test
    void partialStrictOutputsDoNotLeakEarlierStrictMutationsWhenLaterStrictOutputFails() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        SmartInterfaceBlockEntity first = smartInterface(new BlockPos(2, 0, 0));
        SmartInterfaceBlockEntity second = failingSmartInterface(new BlockPos(3, 0, 0), "second");
        first.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "first", new SmartInterfaceType("first", 1F, 0)), true);
        second.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "second", new SmartInterfaceType("second", 2F, 0)), true);
        MachineControllerBlockEntity controller = controllerWithComponents(output, first, second);
        MachineRecipe recipe = partialRequirementRecipe("partial_strict_outputs_atomic_order", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                SmartInterfaceRequirement.output("first", 9F),
                SmartInterfaceRequirement.output("second", 7F)
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isFalse();
        assertThat(first.value("first")).contains(1F);
        assertThat(second.value("second")).contains(2F);
        assertThat(total(output, Items.IRON_INGOT)).isZero();
    }

    @Test
    void strictOutputsDoNotLeakEarlierSmartInterfaceMutationWhenLaterOutputFails() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        SmartInterfaceBlockEntity first = smartInterface(new BlockPos(2, 0, 0));
        SmartInterfaceBlockEntity second = failingSmartInterface(new BlockPos(3, 0, 0), "second");
        first.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "first", new SmartInterfaceType("first", 1F, 0)), true);
        second.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "second", new SmartInterfaceType("second", 2F, 0)), true);
        MachineControllerBlockEntity controller = controllerWithComponents(output, first, second);
        MachineRecipe recipe = explicitRequirementRecipe("strict_outputs_atomic_order", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                SmartInterfaceRequirement.output("first", 9F),
                SmartInterfaceRequirement.output("second", 7F)
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isFalse();
        assertThat(first.value("first")).contains(1F);
        assertThat(second.value("second")).contains(2F);
        assertThat(total(output, Items.IRON_INGOT)).isZero();
    }

    @Test
    void partialRecipeDefersSmartInterfaceOutputUntilCompletion() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        SmartInterfaceBlockEntity smart = smartInterface(new BlockPos(2, 0, 0));
        smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "mode", new SmartInterfaceType("mode", 1F, 0)), true);
        MachineControllerBlockEntity controller = controllerWithComponents(output, smart);
        MachineRecipe recipe = partialRequirementRecipe("partial_defers_smart_output_until_finish", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                SmartInterfaceRequirement.output("mode", 9F)
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(smart.value("mode")).contains(1F);
        assertThat(total(output, Items.IRON_INGOT)).isZero();

        assertThat(context.finishCrafting(recipe)).isTrue();
        assertThat(smart.value("mode")).contains(9F);
        assertThat(total(output, Items.IRON_INGOT)).isEqualTo(1);
    }

    @Test
    void partialRecipeDoesNotLeakSmartInterfaceOutputWhenCompletionFails() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        SmartInterfaceBlockEntity smart = failingSmartInterface(new BlockPos(2, 0, 0), "mode");
        smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "mode", new SmartInterfaceType("mode", 1F, 0)), true);
        MachineControllerBlockEntity controller = controllerWithComponents(output, smart);
        MachineRecipe recipe = partialRequirementRecipe("partial_completion_failure_keeps_smart_output", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                SmartInterfaceRequirement.output("mode", 9F)
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(smart.value("mode")).contains(1F);

        assertThat(context.finishCrafting(recipe)).isFalse();
        assertThat(smart.value("mode")).contains(1F);
        assertThat(total(output, Items.IRON_INGOT)).isZero();
    }

    @Test
    void strictRecipeAlsoDefersSmartInterfaceOutputUntilCompletion() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        SmartInterfaceBlockEntity smart = smartInterface(new BlockPos(2, 0, 0));
        smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "mode", new SmartInterfaceType("mode", 1F, 0)), true);
        MachineControllerBlockEntity controller = controllerWithComponents(output, smart);
        MachineRecipe recipe = explicitRequirementRecipe("strict_defers_smart_output_until_finish", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()),
                SmartInterfaceRequirement.output("mode", 9F)
        ));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.startCrafting(recipe)).isTrue();
        assertThat(smart.value("mode")).contains(1F);
        assertThat(total(output, Items.IRON_INGOT)).isZero();

        assertThat(context.finishCrafting(recipe)).isTrue();
        assertThat(smart.value("mode")).contains(9F);
        assertThat(total(output, Items.IRON_INGOT)).isEqualTo(1);
    }

    @Test
    void item_input_modifier_changes_runtime_consumption_without_mutating_recipe() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "modified_item_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(recipe.inputs().getFirst()).isEqualTo(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2));
    }

    @Test
    void structure_modifiers_are_added_after_recipe_modifiers_and_cleared_on_reset() {
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents());
        RecipeModifier recipeModifier = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.MULTIPLY, false);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "context_effective_modifiers"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20, List.of(), List.of(), List.of(recipeModifier), 0, 1);
        RecipeModifier structure = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.ADD, false);

        context.setStructureModifiers(List.of(structure));

        assertThat(context.structureModifiers()).containsExactly(structure);
        assertThat(context.effectiveModifiers(recipe)).containsExactly(recipeModifier, structure);

        context.resetTransientState();

        assertThat(context.structureModifiers()).isEmpty();
    }

    @Test
    void item_output_modifier_changes_runtime_output_without_mutating_recipe() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "modified_item_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(2)),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 3F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitSynchronousOutputs(recipe, 1)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(6);
        assertThat(recipe.outputs().getFirst().getCount()).isEqualTo(2);
    }

    @Test
    void commitInputsValidatesAllItemRoutesBeforeMutating() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity first = itemInputBus(new BlockPos(1, 0, 0));
        ItemInputBusBlockEntity second = itemInputBus(new BlockPos(2, 0, 0));
        first.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        second.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(first, second);
        MachineRecipe recipe = explicitItemRecipe(
                "route_invalidation",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 4, ItemStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        second.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        assertThat(context.commitInputs(recipe)).isFalse();
        assertThat(first.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void legacyFindAndCheckHelpersStayRemoved() {
        assertThat(Arrays.stream(RecipeCraftingContext.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("findAndCheck"))
                ).isEmpty();
    }

    @Test
    void explicitFluidInputRequirementRunsWhenLegacyInputsAreEmpty() {
        bindFluidComponents(Fluids.WATER);
        FluidInputHatchBlockEntity input = fluidInputHatch(new BlockPos(1, 0, 0));
        input.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 1000));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = explicitRequirementRecipe(
                "explicit_fluid_input",
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(input.getMutableFluidStorage().getAmountAsLong()).isZero();
    }

    @Test
    void explicitFluidInputRequirementAggregatesAcrossHatches() {
        bindFluidComponents(Fluids.WATER);
        FluidInputHatchBlockEntity first = fluidInputHatch(new BlockPos(1, 0, 0));
        FluidInputHatchBlockEntity second = fluidInputHatch(new BlockPos(2, 0, 0));
        first.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 400));
        second.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 600));
        MachineControllerBlockEntity controller = controllerWithComponents(first, second);
        MachineRecipe recipe = explicitRequirementRecipe(
                "fluid_multi_hatch_input",
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(first.getMutableFluidStorage().getAmountAsLong()).isZero();
        assertThat(second.getMutableFluidStorage().getAmountAsLong()).isZero();
    }

    @Test
    void restoredFluidRoutesBindAfterComponentsBecomeAvailable() throws Exception {
        bindFluidComponents(Fluids.WATER);
        FluidInputHatchBlockEntity input = fluidInputHatch(new BlockPos(1, 0, 0));
        input.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 1000));
        MachineRecipe recipe = explicitRequirementRecipe(
                "restored_fluid_route",
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
        );
        RecipeCraftingContext source = new RecipeCraftingContext(controllerWithComponents(input));
        assertThat(source.simulateInputs(recipe)).isTrue();
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        source.serialize(saved);

        MachineControllerBlockEntity restoredController = controllerWithComponents();
        RecipeCraftingContext restored = RecipeCraftingContext.from(restoredController, TagValueInput.create(
                ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()), saved.buildResult()));
        replaceComponents(restoredController, List.of(new ProcessingComponent(
                new MachineComponent(PortKinds.FLUID_INPUT, IOType.INPUT), input,
                input.getBlockPos(), BlockPos.ZERO, (String) null)));

        assertThat(restored.commitInputs(recipe)).isTrue();
        assertThat(input.getMutableFluidStorage().getAmountAsLong()).isZero();
    }

    @Test
    void explicitFluidOutputRequirementFailsWhenOutputHatchHasNoRoom() {
        bindFluidComponents(Fluids.WATER);
        bindFluidComponents(Fluids.LAVA);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        output.getMutableFluidStorage().setFluid(new FluidStack(Fluids.LAVA, (int) output.getMutableFluidStorage().getCapacityAsLong()));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "blocked_fluid_output",
                List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000)))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isFalse();
        assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
            assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.MISSING_OUTPUT);
            assertThat(failure.required()).isEqualTo(1000);
            assertThat(failure.available()).isZero();
        });
    }

    @Test
    void fluidOutputFailureDoesNotConsumeItemInput() {
        bindItemComponents(Items.IRON_INGOT);
        bindFluidComponents(Fluids.WATER);
        bindFluidComponents(Fluids.LAVA);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(1));
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(2, 0, 0));
        output.getMutableFluidStorage().setFluid(new FluidStack(Fluids.LAVA, (int) output.getMutableFluidStorage().getCapacityAsLong()));
        MachineControllerBlockEntity controller = controllerWithComponents(input, output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "fluid_output_no_swallow",
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                        new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000))
                )
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.simulateOutputs(recipe)).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void explicitEnergyRequirementIsConsumedByIoTick() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage().forceInsert(100, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "explicit_energy_tick",
                List.of(new EnergyRequirement(40))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.ioTick(recipe)).isTrue();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(60);
    }

    @Test
    void commitIoTickConsumesEnergyOnlyAfterItsFullRequirementIsAvailable() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage().forceInsert(25, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "atomic_energy_tick",
                List.of(new EnergyRequirement(40))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.commitSynchronousIoTick(recipe, 1)).isFalse();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(25);
    }

    @Test
    void commitIoTickDoesNotPartiallyConsumeAcrossEnergyRequirements() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage().forceInsert(30, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "atomic_multiple_energy_tick",
                List.of(new EnergyRequirement(20), new EnergyRequirement(20))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);

        assertThat(context.commitSynchronousIoTick(recipe, active.getParallelism())).isFalse();
        assertThat(active.applyTickGrant(false, false, 0)).isEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
        assertThat(active.getTick()).isZero();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(30);
    }

    @Test
    void commitIoTickDoesNotPartiallyProduceAcrossEnergyRequirements() throws Exception {
        EnergyOutputHatchBlockEntity hatch = energyOutputHatch(new BlockPos(1, 0, 0));
        setField(EnergyHatchBlockEntity.class, hatch, "storage",
                new LongEnergyStorage(30, 30, () -> {}));
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "atomic_multiple_energy_outputs",
                List.of(
                        new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 20),
                        new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 20)
                )
        );

        assertThat(new RecipeCraftingContext(controller).commitSynchronousIoTick(recipe, 1)).isFalse();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isZero();
    }

    @Test
    void blockedOutputKeepsCompletedRecipeAtFinalTick() {
        MachineRecipe recipe = explicitRequirementRecipe(
                "blocked_finish_tick",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()))
        );
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        active.setTick(active.getTotalTick() - 1);

        assertThat(active.applyTickGrant(true, false, 0)).isEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
        assertThat(active.getTick()).isEqualTo(active.getTotalTick() - 1);
    }

    @Test
    void blockedLiveOutputRetriesAndCommitsOnlyOnce() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        MachineRecipe recipe = explicitRequirementRecipe(
                "blocked_live_output_retry",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(output));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        active.setTick(active.getTotalTick() - 1);

        assertThat(context.commitSynchronousOutputs(recipe, active.getParallelism())).isFalse();
        assertThat(active.applyTickGrant(true, false, 0)).isEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
        assertThat(active.getTick()).isEqualTo(active.getTotalTick() - 1);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getItem()).isEqualTo(Items.COBBLESTONE);

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);

        assertThat(context.commitSynchronousOutputs(recipe, active.getParallelism())).isTrue();
        assertThat(active.applyTickGrant(true, true, 10)).isEqualTo(ActiveMachineRecipe.TickStatus.FINISHED);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void mixedShapeEnergyRuntimeUsesExplicitRequirements() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage().forceInsert(100, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "mixed_energy_runtime"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.EnergyIngredient(90)),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new EnergyRequirement(40))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.ioTick(recipe)).isTrue();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(60);
    }

    @Test
    void missingEnergyRequirementRecordsStructuredFailure() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage().forceInsert(25, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "missing_energy_runtime",
                List.of(new EnergyRequirement(40))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.ioTick(recipe)).isFalse();
        assertThat(context.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_ENERGY);
        assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
            assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.MISSING_ENERGY);
            assertThat(failure.required()).isEqualTo(40);
            assertThat(failure.available()).isEqualTo(25);
            assertThat(failure.shortAmount()).isEqualTo(15);
        });
    }

    @Test
    void multipliedItemRuntimeIoConsumesInputsAndInsertsOutputs() {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(input, output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "multiplied_item_runtime_io",
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance())
                )
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe, 4)).isTrue();
        assertThat(context.simulateOutputs(recipe, 4)).isTrue();
        assertThat(context.startCrafting(recipe, 4)).isTrue();
        assertThat(context.finishCrafting(recipe, 4)).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(4);
    }

    @Test
    void multipliedOutputSpaceFailureDoesNotConsumeInputs() {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 0, 0));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }
        output.getItemStackHandler(null).setStackInSlot(0, Items.IRON_NUGGET.getDefaultInstance().copyWithCount(61));
        MachineControllerBlockEntity controller = controllerWithComponents(input, output);
        MachineRecipe recipe = explicitRequirementRecipe(
                "multiplied_blocked_item_runtime_output",
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance())
                )
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe, 4)).isTrue();
        assertThat(context.simulateOutputs(recipe, 4)).isFalse();
        assertThat(context.startCrafting(recipe, 4)).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(8);
    }

    @Test
    void missingSmartInterfaceOutputDoesNotConsumeInputsAtRecipeStart() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        SmartInterfaceBlockEntity smartInterface = (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                new BlockPos(2, 0, 0), ModBlocks.SMART_INTERFACE.get().defaultBlockState());
        setBlockEntityLevel(smartInterface, controller.getLevel());
        replaceComponents(controller, List.of(
                new ProcessingComponent(new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT), input,
                        input.getBlockPos(), BlockPos.ZERO, (String) null),
                new ProcessingComponent((MachineComponent) null, smartInterface, smartInterface.getBlockPos(), BlockPos.ZERO, (String) null)
        ));
        MachineRecipe recipe = explicitRequirementRecipe("missing_smart_output", List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                SmartInterfaceRequirement.output("mode", 9F)
        ));

        assertThat(new RecipeCraftingContext(controller).startCrafting(recipe)).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void multipliedEnergyRequirementDrainsPerParallelTick() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage().forceInsert(30, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "multiplied_energy_tick",
                List.of(new EnergyRequirement(10))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.ioTick(recipe, 4)).isFalse();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(30);
        assertThat(context.ioTick(recipe, 3)).isTrue();
        assertThat(hatch.getMutableEnergyStorage().getAmountAsLong()).isZero();
    }

    @Test
    void missingItemInputFailureRecordsSearchedComponents() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = explicitRequirementRecipe(
                "missing_item_trace",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isFalse();
        assertThat(context.getLastRequirementFailure().searchedComponents()).isNotEmpty();
        assertThat(context.getLastRequirementFailure().matchedComponents()).isNotNull();
    }

    @Test
    void taggedRequirementRoutesOnlyToTaggedComponent() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity untagged = itemInputBus(new BlockPos(1, 0, 0));
        untagged.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        ItemInputBusBlockEntity tagged = itemInputBus(new BlockPos(2, 0, 0));
        tagged.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        MachineControllerBlockEntity controller = controllerWithComponents(untagged, tagged);
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ProcessingComponent> list = (List<ProcessingComponent>) componentsField.get(controller);
        list.clear();
        MachineComponent port = new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT);
        list.add(new ProcessingComponent(port, untagged, untagged.getBlockPos(), BlockPos.ZERO, (String) null));
        list.add(new ProcessingComponent(port, tagged, tagged.getBlockPos(), BlockPos.ZERO, List.of("input_a")));

        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "tagged_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 4, ItemStack.EMPTY, List.of("input_a")))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(untagged.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(tagged.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
    }

    @Test
    void selector_tag_still_limits_component_search_when_structure_modifier_changes_amount() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity wrongTag = itemInputBus(new BlockPos(1, 0, 0));
        wrongTag.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        ItemInputBusBlockEntity selected = itemInputBus(new BlockPos(2, 0, 0));
        selected.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(2));
        MachineControllerBlockEntity controller = controllerWithComponents(wrongTag, selected);
        MachineComponent port = new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT);
        replaceComponents(controller, List.of(
                new ProcessingComponent(port, wrongTag, wrongTag.getBlockPos(), BlockPos.ZERO, List.of("input_b")),
                new ProcessingComponent(port, selected, selected.getBlockPos(), BlockPos.ZERO, List.of("input_a"))
        ));
        MachineRecipe recipe = explicitRequirementRecipe(
                "tagged_input_with_structure_modifier",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY, List.of("input_a")))
        );
        RecipeModifier structureModifier = new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT,
                2F, RecipeModifier.Operation.MULTIPLY, false);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        context.setStructureModifiers(List.of(structureModifier));

        assertThat(((ItemRequirement) recipe.runtimeRequirements(context.structureModifiers()).getFirst()).count()).isEqualTo(4);
        assertThat(context.simulateInputs(recipe)).isFalse();
        assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
            assertThat(failure.required()).isEqualTo(4);
            assertThat(failure.available()).isEqualTo(2);
            assertThat(failure.searchedComponents()).anySatisfy(trace -> assertThat(trace).contains(selected.getBlockPos().toShortString()));
            assertThat(failure.searchedComponents()).noneSatisfy(trace -> assertThat(trace).contains(wrongTag.getBlockPos().toShortString()));
            assertThat(failure.matchedComponents()).anySatisfy(trace -> assertThat(trace).contains(selected.getBlockPos().toShortString()));
            assertThat(failure.matchedComponents()).noneSatisfy(trace -> assertThat(trace).contains(wrongTag.getBlockPos().toShortString()));
        });
    }

    @Test
    void itemRequirementWrongTagRecordsTagMismatchFailure() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity tagged = itemInputBus(new BlockPos(1, 0, 0));
        tagged.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        MachineControllerBlockEntity controller = controllerWithComponents(tagged);
        replaceComponents(controller, List.of(new ProcessingComponent(
                new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT),
                tagged,
                tagged.getBlockPos(),
                BlockPos.ZERO,
                List.of("input_a")
        )));
        MachineRecipe recipe = explicitRequirementRecipe(
                "wrong_item_tag",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 4, ItemStack.EMPTY, List.of("input_b")))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isFalse();
        assertThat(context.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
            assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.TAG_MISMATCH);
            assertThat(failure.searchedComponents()).isNotEmpty();
            assertThat(failure.matchedComponents()).isEmpty();
        });
    }

    @Test
    void fluidRequirementWrongTagRecordsTagMismatchFailure() throws Exception {
        bindFluidComponents(Fluids.WATER);
        FluidInputHatchBlockEntity tagged = fluidInputHatch(new BlockPos(1, 0, 0));
        tagged.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 1000));
        MachineControllerBlockEntity controller = controllerWithComponents(tagged);
        replaceComponents(controller, List.of(new ProcessingComponent(
                new MachineComponent(PortKinds.FLUID_INPUT, IOType.INPUT),
                tagged,
                tagged.getBlockPos(),
                BlockPos.ZERO,
                List.of("input_a")
        )));
        MachineRecipe recipe = explicitRequirementRecipe(
                "wrong_fluid_tag",
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY, List.of("input_b")))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isFalse();
        assertThat(context.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
            assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.TAG_MISMATCH);
            assertThat(failure.searchedComponents()).isNotEmpty();
            assertThat(failure.matchedComponents()).isEmpty();
        });
    }

    @Test
    void blockArrayTagsFlowThroughControllerComponentsToRuntimeRoutes() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity tagged = itemInputBus(new BlockPos(1, 0, 0));
        tagged.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        ItemInputBusBlockEntity other = itemInputBus(new BlockPos(2, 0, 0));
        other.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        MachineControllerBlockEntity controller = controllerWithComponents(tagged, other);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        setField(BlockEntity.class, controller, "blockState", Blocks.CHEST.defaultBlockState());
        BlockArray pattern = new BlockArray(java.util.Map.of(
                tagged.getBlockPos(), new BlockPredicate.Any(),
                other.getBlockPos(), new BlockPredicate.Any()
        )).tagged(tagged.getBlockPos(), "input_a");
        setField(MachineControllerBlockEntity.class, controller, "foundMachine",
                new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "tagged_structure"), "Tagged Structure", pattern));
        setField(MachineControllerBlockEntity.class, controller, "foundPattern", pattern);
        var updateComponents = MachineControllerBlockEntity.class.getDeclaredMethod("updateComponents");
        updateComponents.setAccessible(true);

        updateComponents.invoke(controller);

        assertThat(controller.getComponents())
                .anySatisfy(component -> assertThat(component.tags()).containsExactly("input_a"));
        MachineRecipe recipe = explicitRequirementRecipe(
                "structure_tagged_input",
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 4, ItemStack.EMPTY, List.of("input_a")))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(tagged.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(other.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(4);
    }

    @Test
    void structure_modifiers_change_duration_output_count_and_clamped_chance_without_mutating_recipe() {
        bindItemComponents(Items.IRON_NUGGET);
        List<RecipeModifier> recipeModifiers = List.of(new RecipeModifier(
                IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 1F,
                RecipeModifier.Operation.ADD, false));
        List<RecipeModifier> structureModifiers = List.of(
                new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 2F,
                        RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2F,
                        RecipeModifier.Operation.ADD, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 0.5F,
                        RecipeModifier.Operation.MULTIPLY, true));
        MachineRecipe recipe = explicitRequirementRecipe(
                "structure_duration_output_chance",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        Items.IRON_NUGGET.getDefaultInstance().copyWithCount(2), 1F, List.of())));
        recipe = new MachineRecipe(recipe.id(), recipe.machineId(), 20, List.of(), List.of(),
                recipeModifiers, 0, 1, false, List.of(), recipe.requirements());
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents());
        context.setStructureModifiers(structureModifiers);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);

        active.refreshTotalTick(context);
        List<MachineOutput> outputs = recipe.runtimeMachineOutputs(structureModifiers);
        List<MachineRequirement> requirements = recipe.runtimeRequirements(structureModifiers);
        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow();
        var decoded = MachineRecipe.CODEC.codec().parse(jsonOps(), encoded).getOrThrow();

        assertThat(active.getTotalTick()).isEqualTo(40);
        assertThat(outputs.getFirst()).isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getCount()).isEqualTo(5);
            assertThat(output.chance()).isEqualTo(0.5F);
        });
        assertThat(requirements.getFirst()).isInstanceOfSatisfying(ItemRequirement.class, requirement -> {
            assertThat(requirement.stack().getCount()).isEqualTo(5);
            assertThat(requirement.chance()).isEqualTo(0.5F);
        });
        assertThat(recipe.modifiers()).containsExactlyElementsOf(recipeModifiers);
        assertThat(recipe.requirements().getFirst()).isInstanceOfSatisfying(ItemRequirement.class, requirement -> {
            assertThat(requirement.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(requirement.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(requirement.stack().getCount()).isEqualTo(2);
            assertThat(requirement.chance()).isEqualTo(1F);
        });
        assertThat(decoded.modifiers()).containsExactlyElementsOf(recipe.modifiers());
        assertThat(decoded.requirements()).hasSameSizeAs(recipe.requirements());
        assertThat(decoded.requirements().getFirst()).isInstanceOfSatisfying(ItemRequirement.class, requirement -> {
            assertThat(requirement.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(requirement.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(requirement.stack().getCount()).isEqualTo(2);
            assertThat(requirement.chance()).isEqualTo(1F);
        });
    }

    @Test
    void smart_interface_values_are_resolved_from_interface_owned_parameters() throws Exception {
        SmartInterfaceBlockEntity smart = smartInterface(new BlockPos(1, 0, 0));
        assertThat(smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), java.util.Map.of(
                "temperature", new SmartInterfaceType("temperature", 50F, 0)
        ), true)).isTrue();
        MachineControllerBlockEntity controller = controllerWithMachineAndComponents(MMCR.id("test_machine"), smart);

        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.smartInterfaceValue("temperature")).contains(50F);
        assertThat(context.setSmartInterfaceValue("temperature", 75F)).isTrue();
        assertThat(smart.value("temperature")).contains(75F);
    }

    @Test
    void smart_interface_modifier_changes_runtime_item_output_and_chance() throws Exception {
        registerMachineWithSmartModifier(MMCR.id("test_machine"), List.of(
                SmartInterfaceModifier.item("temperature", RecipeModifier.IOType.OUTPUT, false, 0F, 100F, 1F, 3F,
                        RecipeModifier.Operation.MULTIPLY),
                SmartInterfaceModifier.item("temperature", RecipeModifier.IOType.OUTPUT, true, 0F, 100F, 0.25F, 2F,
                        RecipeModifier.Operation.MULTIPLY)
        ));
        SmartInterfaceBlockEntity smart = smartInterface(new BlockPos(1, 0, 0));
        assertThat(smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), java.util.Map.of(
                "temperature", new SmartInterfaceType("temperature", 100F, 0)
        ), true)).isTrue();
        MachineRecipe recipe = explicitRequirementRecipe("smart_modifier_item_output", List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        Items.IRON_NUGGET.getDefaultInstance().copyWithCount(2), 0.75F, List.of())
        ));

        List<MachineRequirement> runtime = new RecipeCraftingContext(
                controllerWithMachineAndComponents(MMCR.id("test_machine"), smart)).runtimeRequirements(recipe);

        ItemRequirement item = (ItemRequirement) runtime.getFirst();
        assertThat(item.stack().getCount()).isEqualTo(6);
        assertThat(item.chance()).isEqualTo(1F);
    }

    @Test
    void smart_interface_duration_modifier_changes_level_modified_duration() throws Exception {
        registerMachineWithSmartModifier(MMCR.id("test_machine"), List.of(
                SmartInterfaceModifier.duration("temperature", 0F, 100F, 2F, 0.5F, RecipeModifier.Operation.MULTIPLY)
        ));
        SmartInterfaceBlockEntity smart = smartInterface(new BlockPos(1, 0, 0));
        assertThat(smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), java.util.Map.of(
                "temperature", new SmartInterfaceType("temperature", 100F, 0)
        ), true)).isTrue();
        MachineRecipe recipe = new MachineRecipe(MMCR.id("smart_duration"), MMCR.id("test_machine"), 40,
                List.of(), List.of());

        assertThat(new RecipeCraftingContext(controllerWithMachineAndComponents(MMCR.id("test_machine"), smart))
                .levelModifiedDuration(recipe)).isEqualTo(20);
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) {
        return allocateItemBus(ItemInputBusBlockEntity.class, pos);
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) {
        return allocateItemBus(ItemOutputBusBlockEntity.class, pos);
    }

    private static FluidInputHatchBlockEntity fluidInputHatch(BlockPos pos) {
        return allocateBlockEntity(FluidInputHatchBlockEntity.class, pos);
    }

    private static FluidOutputHatchBlockEntity fluidOutputHatch(BlockPos pos) {
        return allocateBlockEntity(FluidOutputHatchBlockEntity.class, pos);
    }

    private static EnergyInputHatchBlockEntity energyInputHatch(BlockPos pos) {
        return allocateBlockEntity(EnergyInputHatchBlockEntity.class, pos);
    }

    private static EnergyOutputHatchBlockEntity energyOutputHatch(BlockPos pos) {
        return allocateBlockEntity(EnergyOutputHatchBlockEntity.class, pos);
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    private static void bindFluidComponents(Fluid fluid) {
        fluid.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    private static SmartInterfaceBlockEntity smartInterface(BlockPos pos) {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                pos, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
    }

    private static SmartInterfaceBlockEntity failingSmartInterface(BlockPos pos, String rejectedType) {
        return new SmartInterfaceBlockEntity(pos, ModBlocks.SMART_INTERFACE.get().defaultBlockState()) {
            @Override
            public boolean setValue(String type, float value) {
                if (rejectedType.equals(type)) return false;
                return super.setValue(type, value);
            }
        };
    }

    private static void registerMachineWithSmartModifier(Identifier machineId, List<SmartInterfaceModifier> modifiers) {
        MachineDefinitions.clearForTesting();
        MachineRegistration.Builder builder = MachineRegistration.builder(machineId).localizedName("Test Machine");
        modifiers.forEach(builder::smartInterfaceModifier);
        MachineDefinitions.register(builder.build());
        MachineDefinitions.freezeRegistryPhase();
    }

    private static MachineRecipe explicitItemRecipe(String path, List<MachineRequirement> requirements) {
        return explicitRequirementRecipe(path, requirements);
    }

    private static MachineRecipe explicitRequirementRecipe(String path, List<MachineRequirement> requirements) {
        return new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", path),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                requirements
        );
    }

    private static MachineRecipe partialRequirementRecipe(String path, List<MachineRequirement> requirements) {
        return new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", path),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                requirements,
                false,
                List.of(),
                true
        );
    }

    private static int total(ItemOutputBusBlockEntity bus, Item item) {
        int total = 0;
        for (int slot = 0; slot < bus.getItemStackHandler(null).getSlots(); slot++) {
            ItemStack stack = bus.getItemStackHandler(null).getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static <T extends BlockEntity> T allocateItemBus(Class<T> type, BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            T bus = (T) unsafe.allocateInstance(type);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
            initializeLinkedAppearance((LinkedAppearanceBlockEntity) bus);
            setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item bus for crafting context test", e);
        }
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static <T extends BlockEntity> T allocateBlockEntity(Class<T> type, BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            T entity = (T) unsafe.allocateInstance(type);
            setField(BlockEntity.class, entity, "type", null);
            setField(BlockEntity.class, entity, "worldPosition", pos);
            setField(BlockEntity.class, entity, "blockState", Blocks.CHEST.defaultBlockState());
            if (entity instanceof FluidHatchBlockEntity) {
                setField(FluidHatchBlockEntity.class, entity, "storage",
                        new LongFluidStorage(8000, () -> {}));
            }
            if (entity instanceof EnergyHatchBlockEntity) {
                setField(EnergyHatchBlockEntity.class, entity, "storage",
                        new LongEnergyStorage(100000, 100000, () -> {}));
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate block entity for crafting context test", e);
        }
    }

    private static void initializeLinkedAppearance(LinkedAppearanceBlockEntity component)
            throws ReflectiveOperationException {
        setField(LinkedAppearanceBlockEntity.class, component,
                "appearanceBaseTexture", cn.howxu.mmcr.MMCR.id("block/basic_casing"));
        setField(LinkedAppearanceBlockEntity.class, component,
                "linkedControllers", new TreeMap<>(BlockPos::compareTo));
        setField(LinkedAppearanceBlockEntity.class, component,
                "controllerLinkCheckCounter", 0);
    }

    private static MachineControllerBlockEntity controllerWithComponents(BlockEntity... ports) {
        return controllerWithComponents(List.of(ports), null);
    }

    private static MachineControllerBlockEntity controllerWithComponents(List<BlockEntity> ports,
                                                                        RandomSource random) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
            var level = random == null ? LevelStub.createWithBlockEntities(ports) : LevelStub.createWithBlockEntities(ports, random);
            setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
            setBlockEntityLevel(controller, level);
            for (BlockEntity port : ports) {
                setBlockEntityLevel(port, level);
            }
            Field components = MachineControllerBlockEntity.class.getDeclaredField("components");
            components.setAccessible(true);
            components.set(controller, new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> list = (List<ProcessingComponent>) components.get(controller);
            list.clear();
            for (BlockEntity port : ports) {
                MachineComponent component = componentFor(port);
                list.add(new ProcessingComponent(component, port, port.getBlockPos(), BlockPos.ZERO, (String) null));
            }
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate MachineControllerBlockEntity for crafting context test", e);
        }
    }

    private static MachineControllerBlockEntity controllerWithMachineAndComponents(Identifier machineId,
            BlockEntity... ports) throws ReflectiveOperationException {
        MachineControllerBlockEntity controller = controllerWithComponents(ports);
        setField(MachineControllerBlockEntity.class, controller, "foundMachine",
                new DynamicMachine(machineId, "Test Machine", new BlockArray(java.util.Map.of())));
        return controller;
    }

    private static void replaceComponents(MachineControllerBlockEntity controller, List<ProcessingComponent> replacements) throws Exception {
        Field components = MachineControllerBlockEntity.class.getDeclaredField("components");
        components.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ProcessingComponent> list = (List<ProcessingComponent>) components.get(controller);
        list.clear();
        list.addAll(replacements);
    }

    private static MachineComponent componentFor(BlockEntity port) {
        if (port instanceof ItemInputBusBlockEntity) return new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT);
        if (port instanceof ItemOutputBusBlockEntity) return new MachineComponent(PortKinds.ITEM_OUTPUT, IOType.OUTPUT);
        if (port instanceof FluidInputHatchBlockEntity) return new MachineComponent(PortKinds.FLUID_INPUT, IOType.INPUT);
        if (port instanceof FluidOutputHatchBlockEntity) return new MachineComponent(PortKinds.FLUID_OUTPUT, IOType.OUTPUT);
        if (port instanceof EnergyInputHatchBlockEntity) return new MachineComponent(PortKinds.ENERGY_INPUT, IOType.INPUT);
        if (port instanceof EnergyOutputHatchBlockEntity) return new MachineComponent(PortKinds.ENERGY_OUTPUT, IOType.OUTPUT);
        if (port instanceof SmartInterfaceBlockEntity) return null;
        throw new IllegalArgumentException("Unknown port: " + port.getClass().getSimpleName());
    }

    private static void setBlockEntityLevel(BlockEntity blockEntity, Level level)
            throws ReflectiveOperationException {
        setField(BlockEntity.class, blockEntity, "level", level);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
