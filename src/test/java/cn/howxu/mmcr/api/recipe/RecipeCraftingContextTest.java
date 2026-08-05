package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    void simulateInputsFailsWhenDuplicateIngredientsExceedCombinedInput() {
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

        assertThat(new RecipeCraftingContext(controller).simulateInputs(recipe)).isFalse();
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

        assertThat(new RecipeCraftingContext(controller).simulateOutputs(recipe)).isFalse();
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
        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(15);
        assertThat(output.getItemStackHandler(null).getStackInSlot(1).isEmpty()).isTrue();
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
        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(output.getItemStackHandler(null).getStackInSlot(1).isEmpty()).isTrue();
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
        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
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
        input.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 1000));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = explicitRequirementRecipe(
                "explicit_fluid_input",
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(input.getFluidTank(null).getFluidAmount()).isZero();
    }

    @Test
    void explicitFluidInputRequirementAggregatesAcrossHatches() {
        bindFluidComponents(Fluids.WATER);
        FluidInputHatchBlockEntity first = fluidInputHatch(new BlockPos(1, 0, 0));
        FluidInputHatchBlockEntity second = fluidInputHatch(new BlockPos(2, 0, 0));
        first.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 400));
        second.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 600));
        MachineControllerBlockEntity controller = controllerWithComponents(first, second);
        MachineRecipe recipe = explicitRequirementRecipe(
                "fluid_multi_hatch_input",
                List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(first.getFluidTank(null).getFluidAmount()).isZero();
        assertThat(second.getFluidTank(null).getFluidAmount()).isZero();
    }

    @Test
    void explicitFluidOutputRequirementFailsWhenOutputHatchHasNoRoom() {
        bindFluidComponents(Fluids.WATER);
        bindFluidComponents(Fluids.LAVA);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        output.getFluidTank(null).setFluid(new FluidStack(Fluids.LAVA, output.getFluidTank(null).getCapacity()));
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
        output.getFluidTank(null).setFluid(new FluidStack(Fluids.LAVA, output.getFluidTank(null).getCapacity()));
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
        hatch.getMutableEnergyStorage(null).receiveEnergy(100, false);
        MachineControllerBlockEntity controller = controllerWithComponents(hatch);
        MachineRecipe recipe = explicitRequirementRecipe(
                "explicit_energy_tick",
                List.of(new EnergyRequirement(40))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.ioTick(recipe)).isTrue();
        assertThat(hatch.getMutableEnergyStorage(null).getEnergyStored()).isEqualTo(60);
    }

    @Test
    void mixedShapeEnergyRuntimeUsesExplicitRequirements() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage(null).receiveEnergy(100, false);
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
        assertThat(hatch.getMutableEnergyStorage(null).getEnergyStored()).isEqualTo(60);
    }

    @Test
    void missingEnergyRequirementRecordsStructuredFailure() {
        EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
        hatch.getMutableEnergyStorage(null).receiveEnergy(25, false);
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

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    private static void bindFluidComponents(net.minecraft.world.level.material.Fluid fluid) {
        fluid.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    private static MachineRecipe explicitItemRecipe(String path, List<cn.howxu.mmcr.api.recipe.requirement.MachineRequirement> requirements) {
        return explicitRequirementRecipe(path, requirements);
    }

    private static MachineRecipe explicitRequirementRecipe(String path, List<cn.howxu.mmcr.api.recipe.requirement.MachineRequirement> requirements) {
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

    @SuppressWarnings({"removal", "unchecked"})
    private static <T extends BlockEntity> T allocateItemBus(Class<T> type, BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            T bus = (T) unsafe.allocateInstance(type);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(cn.howxu.mmcr.internal.tile.ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
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
            setField(BlockEntity.class, entity, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            if (entity instanceof cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity) {
                setField(cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity.class, entity, "tank",
                        new net.neoforged.neoforge.fluids.capability.templates.FluidTank(8000) {
                            @Override
                            protected void onContentsChanged() {
                            }
                        });
            }
            if (entity instanceof cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity) {
                setField(cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity.class, entity, "storage",
                        new net.neoforged.neoforge.energy.EnergyStorage(100000, 100000, 100000));
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate block entity for crafting context test", e);
        }
    }

    private static MachineControllerBlockEntity controllerWithComponents(net.minecraft.world.level.block.entity.BlockEntity... ports) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
            var level = LevelStub.createWithBlockEntities(List.of(ports));
            setBlockEntityLevel(controller, level);
            for (net.minecraft.world.level.block.entity.BlockEntity port : ports) {
                setBlockEntityLevel(port, level);
            }
            Field components = MachineControllerBlockEntity.class.getDeclaredField("components");
            components.setAccessible(true);
            components.set(controller, new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> list = (List<ProcessingComponent>) components.get(controller);
            list.clear();
            for (net.minecraft.world.level.block.entity.BlockEntity port : ports) {
                MachineComponent component = componentFor(port);
                list.add(new ProcessingComponent(component, port, port.getBlockPos(), BlockPos.ZERO, null));
            }
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate MachineControllerBlockEntity for crafting context test", e);
        }
    }

    private static MachineComponent componentFor(BlockEntity port) {
        if (port instanceof ItemInputBusBlockEntity) return new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        if (port instanceof ItemOutputBusBlockEntity) return new MachineComponent(PortKinds.ITEM_OUTPUT, cn.howxu.mmcr.util.IOType.OUTPUT);
        if (port instanceof FluidInputHatchBlockEntity) return new MachineComponent(PortKinds.FLUID_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        if (port instanceof FluidOutputHatchBlockEntity) return new MachineComponent(PortKinds.FLUID_OUTPUT, cn.howxu.mmcr.util.IOType.OUTPUT);
        if (port instanceof EnergyInputHatchBlockEntity) return new MachineComponent(PortKinds.ENERGY_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        throw new IllegalArgumentException("Unknown port: " + port.getClass().getSimpleName());
    }

    private static void setBlockEntityLevel(net.minecraft.world.level.block.entity.BlockEntity blockEntity, net.minecraft.world.level.Level level)
            throws ReflectiveOperationException {
        setField(net.minecraft.world.level.block.entity.BlockEntity.class, blockEntity, "level", level);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
