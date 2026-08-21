package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import static org.assertj.core.api.Assertions.assertThat;

class ActiveMachineRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void setParallelismClampsToActiveMaximumAndPersistsClampedValue() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("active_parallel_clamp"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of());
        RecipeRegistry.register(recipe);

        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);
        active.setParallelism(64);
        assertThat(active.getParallelism()).isEqualTo(16);
        active.setMaxParallelism(4);
        assertThat(active.getParallelism()).isEqualTo(4);
        active.setMaxParallelism(16);
        active.setParallelism(0);
        assertThat(active.getParallelism()).isEqualTo(1);

        active.setParallelism(64);
        ActiveMachineRecipe fromNbt = new ActiveMachineRecipe(active.serialize());
        assertThat(fromNbt.getMaxParallelism()).isEqualTo(16);
        assertThat(fromNbt.getParallelism()).isEqualTo(16);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()));
        active.serialize(output);
        CompoundTag tag = output.buildResult();
        ActiveMachineRecipe fromValueInput = ActiveMachineRecipe.from(TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()), tag));

        assertThat(fromValueInput.getMaxParallelism()).isEqualTo(16);
        assertThat(fromValueInput.getParallelism()).isEqualTo(16);
    }

    @Test
    void contextCheckPromotesParallelismBeforeStartCommitsInputs() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = inputRecipe("active_parallel_start", MMCR.id("blast_furnace"), Items.IRON_INGOT, 2);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);

        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        boolean canStart = active.canStartCrafting(context);
        boolean started = active.start(context);

        assertThat(canStart).isTrue();
        assertThat(started).isTrue();
        assertThat(active.getParallelism()).isEqualTo(4);
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
    }

    @Test
    void finishRetryCooldownPersistsAcrossNbtAndValueIo() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("active_retry_cooldown"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of());
        RecipeRegistry.register(recipe);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);

        assertThat(active.shouldRetryFinish(5)).isTrue();
        active.markFinishBlocked(5);
        assertThat(active.shouldRetryFinish(14)).isFalse();
        assertThat(active.shouldRetryFinish(15)).isTrue();

        ActiveMachineRecipe fromNbt = new ActiveMachineRecipe(active.serialize());
        assertThat(fromNbt.shouldRetryFinish(14)).isFalse();
        assertThat(fromNbt.shouldRetryFinish(15)).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()));
        active.serialize(output);
        ActiveMachineRecipe fromValueInput = ActiveMachineRecipe.from(TagValueInput.create(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),
                output.buildResult()));

        assertThat(fromValueInput.shouldRetryFinish(14)).isFalse();
        assertThat(fromValueInput.shouldRetryFinish(15)).isTrue();
    }

    @Test
    void coordinatorStartCommitConsumesPromotedInputs() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = inputRecipe("active_parallel_start", MMCR.id("blast_furnace"), Items.IRON_INGOT, 2);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);

        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        boolean canStart = active.canStartCrafting(context);
        int granted = context.commitStart(recipe, active.getMaxParallelism());
        active.setParallelism(granted);

        assertThat(canStart).isTrue();
        assertThat(granted).isEqualTo(4);
        assertThat(active.getParallelism()).isEqualTo(4);
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
    }

    void inputConsumptionPlanPersistsConsumedBatchCounts() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("active_consumption_plan"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of());
        RecipeRegistry.register(recipe);
        ActiveMachineRecipe.InputConsumptionPlan plan = new ActiveMachineRecipe.InputConsumptionPlan(List.of(2));
        CompoundTag serialized = new CompoundTag();
        serialized.putString("recipeName", recipe.id().toString());
        serialized.put("inputConsumptionPlan", plan.serialize());

        ActiveMachineRecipe restored = new ActiveMachineRecipe(serialized);

        assertThat(restored.inputConsumptionPlan().consumedBatches(0)).isEqualTo(2);
        assertThat(restored.inputConsumptionPlan().consumedBatches(1)).isZero();
    }

    @Test
    void persistedConsumptionPlanExtractsOnlyItsConsumedParallelBatches() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(3));
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("active_consumption_batches"),
                MMCR.id("blast_furnace"),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(), null, 0.5F)),
                true);
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents(bus));

        assertThat(context.startCrafting(recipe, 3, new ActiveMachineRecipe.InputConsumptionPlan(List.of(2)))).isTrue();
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void repeatedTagInputRecipeContinuesAfterFirstCompletion() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("repeated_tag_input"), MMCR.id("blast_furnace"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT,
                        Ingredient.of(HolderSet.direct(Items.WOODEN_SWORD.builtInRegistryHolder(),
                                Items.DIAMOND_SWORD.builtInRegistryHolder())), 1,
                        ItemStack.EMPTY)), true);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();

        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.WOODEN_SWORD));
        ActiveMachineRecipe first = new ActiveMachineRecipe(recipe);
        RecipeCraftingContext firstContext = pool.borrow(first, controller);
        assertThat(first.start(firstContext)).isTrue();
        for (int tick = 0; tick < first.getTotalTick(); tick++) {
            assertThat(firstContext.commitSynchronousIoTick(recipe, 1, first.inputConsumptionPlan())).isTrue();
            assertThat(first.applyTickGrant(true, true, tick)).isNotEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
        }
        pool.returnContext(firstContext);

        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD));
        ActiveMachineRecipe second = new ActiveMachineRecipe(recipe);
        RecipeCraftingContext secondContext = pool.borrow(second, controller);
        assertThat(second.start(secondContext)).isTrue();
        assertThat(secondContext.commitSynchronousIoTick(recipe, 1, second.inputConsumptionPlan())).isTrue();
        assertThat(second.applyTickGrant(true, false, 0)).isEqualTo(ActiveMachineRecipe.TickStatus.CONTINUE);
        assertThat(secondContext.commitSynchronousIoTick(recipe, 1, second.inputConsumptionPlan())).isTrue();
        assertThat(second.applyTickGrant(true, false, 1)).isEqualTo(ActiveMachineRecipe.TickStatus.CONTINUE);
    }

    @Test
    void nonConsumedInputBatchIsRetainedAcrossTicks() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("retained_input_tick"), MMCR.id("blast_furnace"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD), 1,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0.5F)), true);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD));

        ActiveMachineRecipe.InputConsumptionPlan plan = new ActiveMachineRecipe.InputConsumptionPlan(List.of(0));
        assertThat(context.startCrafting(recipe, 1, plan)).isTrue();
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isFalse();
        for (int tick = 0; tick < 2; tick++) {
            assertThat(context.commitSynchronousIoTick(recipe, 1, plan)).isTrue();
            assertThat(active.applyTickGrant(true, false, tick)).isEqualTo(ActiveMachineRecipe.TickStatus.CONTINUE);
        }
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isFalse();
    }

    @Test
    void completionWithBlockedOutputMarksRecipeForRetry() throws Exception {
        bindItemComponents(Items.COBBLESTONE);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("blocked_completion_output"), MMCR.id("blast_furnace"), 1,
                List.of(), List.of(Items.IRON_INGOT.getDefaultInstance()));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(active.start(context)).isTrue();
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, Items.COBBLESTONE.getDefaultInstance().copyWithCount(64));
        }

        assertThat(context.simulateOutputs(active.getRecipe(), active.getParallelism())).isFalse();
        assertThat(active.applyTickGrant(true, false, 100)).isEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
        assertThat(active.getTick()).isZero();
        assertThat(active.isFinishPending()).isTrue();
        assertThat(context.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_OUTPUT);
    }

    @Test
    void levelEffectsApplyBeforeOrdinaryModifiersAndKeepPositiveOutput() throws Exception {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("level_effects"), MMCR.id("blast_furnace"), 20,
                List.of(), List.of(),
                List.of(new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        Items.IRON_INGOT.getDefaultInstance(), 1F, List.of())), false);
        MachineControllerBlockEntity controller = controllerWithComponents();
        MachineLevel level = new MachineLevel(MMCR.id("level"), MMCR.id("coil"), 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, new LevelModifier(0.5D, 1D, 0.5D, 0, 0));
        setField(MachineControllerBlockEntity.class, controller, "foundLevels", Map.of(level.typeId(), level));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);

        active.refreshTotalTick(context);

        assertThat(active.getTotalTick()).isEqualTo(20);
        assertThat(context.runtimeRequirements(recipe)).singleElement().satisfies(requirement ->
                assertThat(((ItemRequirement) requirement).stack().getCount()).isEqualTo(1));
    }

    @Test
    void activeRecipeRejectsMismatchedConnectedHostRequirement() throws Exception {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("active_mismatched_host"), MMCR.id("blast_furnace"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(), false, List.of(),
                Set.of(MMCR.id("required_host")));
        MachineControllerBlockEntity controller = controllerWithComponents();
        setField(MachineControllerBlockEntity.class, controller, "foundMachine",
                new DynamicMachine(MMCR.id("active_module"), "active_module", new BlockArray(Map.of()))
                        .withRole(MachineRole.MODULE, Set.of()));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(recipe.canRunOnConnectedHost(MMCR.id("other_host"))).isFalse();
        assertThat(new ActiveMachineRecipe(recipe).canStartCrafting(context)).isFalse();
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, Item item, int count) {
        return new MachineRecipe(
                MMCR.id(path),
                machineId,
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY)),
                true);
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception {
        return itemBus(ItemInputBusBlockEntity.class, pos);
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) throws Exception {
        return itemBus(ItemOutputBusBlockEntity.class, pos);
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static <T extends ItemBusBlockEntity> T itemBus(Class<T> type, BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        T bus = (T) unsafe.allocateInstance(type);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
        setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
        return bus;
    }

    private static MachineControllerBlockEntity controllerWithComponents(BlockEntity... ports) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        var level = LevelStub.createWithBlockEntities(List.of(ports));
        setField(BlockEntity.class, controller, "level", level);
        setField(MachineControllerBlockEntity.class, controller, "components", new ArrayList<ProcessingComponent>());
        for (BlockEntity port : ports) {
            setField(BlockEntity.class, port, "level", level);
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> components = (List<ProcessingComponent>) fieldValue(MachineControllerBlockEntity.class, controller, "components");
            components.add(new ProcessingComponent(
                    new MachineComponent(port instanceof ItemOutputBusBlockEntity ? PortKinds.ITEM_OUTPUT : PortKinds.ITEM_INPUT,
                            port instanceof ItemOutputBusBlockEntity ? IOType.OUTPUT : IOType.INPUT),
                    port,
                    port.getBlockPos(),
                    BlockPos.ZERO,
                    (String) null));
        }
        return controller;
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object fieldValue(Class<?> declaringClass, Object target, String name) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
