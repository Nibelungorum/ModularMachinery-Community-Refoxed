package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
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

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        active.serialize(output);
        CompoundTag tag = output.buildResult();
        ActiveMachineRecipe fromValueInput = ActiveMachineRecipe.from(TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()), tag));

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

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        active.serialize(output);
        ActiveMachineRecipe fromValueInput = ActiveMachineRecipe.from(TagValueInput.create(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()),
                output.buildResult()));

        assertThat(fromValueInput.shouldRetryFinish(14)).isFalse();
        assertThat(fromValueInput.shouldRetryFinish(15)).isTrue();
    }

    @Test
<<<<<<< HEAD
=======
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

    @Test
>>>>>>> feat/shared-multiblock-io
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
    void startPromotesParallelismToHighestFeasibleCraftAmount() throws Exception {
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
<<<<<<< HEAD
    void completionWithBlockedOutputCancelsTheActiveRecipe() throws Exception {
=======
    void completionWithBlockedOutputMarksRecipeForRetry() throws Exception {
>>>>>>> feat/shared-multiblock-io
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

<<<<<<< HEAD
        assertThat(active.tick(context)).isEqualTo(ActiveMachineRecipe.TickStatus.CANCELLED);
        assertThat(active.getTick()).isZero();
=======
        assertThat(context.simulateOutputs(active.getRecipe(), active.getParallelism())).isFalse();
        assertThat(active.applyTickGrant(true, false, 100)).isEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
        assertThat(active.getTick()).isZero();
        assertThat(active.isFinishPending()).isTrue();
>>>>>>> feat/shared-multiblock-io
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
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, new LevelModifier(0.5D, 1D, 0.5D, 0, 0));
        setField(MachineControllerBlockEntity.class, controller, "foundLevels", Map.of(level.typeId(), level));
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);

        active.refreshTotalTick(context);

        assertThat(active.getTotalTick()).isEqualTo(20);
        assertThat(context.runtimeRequirements(recipe)).singleElement().satisfies(requirement ->
                assertThat(((ItemRequirement) requirement).stack().getCount()).isEqualTo(1));
    }

    private static MachineRecipe inputRecipe(String path, net.minecraft.resources.Identifier machineId, Item item, int count) {
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
    private static <T extends cn.howxu.mmcr.internal.tile.ItemBusBlockEntity> T itemBus(Class<T> type, BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        T bus = (T) unsafe.allocateInstance(type);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        setField(cn.howxu.mmcr.internal.tile.ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
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
                            port instanceof ItemOutputBusBlockEntity ? cn.howxu.mmcr.util.IOType.OUTPUT : cn.howxu.mmcr.util.IOType.INPUT),
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
