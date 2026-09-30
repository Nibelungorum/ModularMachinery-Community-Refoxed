package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller admission and reservation boundary tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class PatternStartReservationTest {
    private static final BlockPos PATTERN_PORT = new BlockPos(3, 0, 0);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void inactive_or_unlinked_controllers_reject_admission() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("reservation_inactive", List.of());
        RecipeRegistry.registerStatic(recipe);

        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        formForPattern(controller, false);
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
    }

    @Test
    void normal_controller_reserves_one_slot_and_releases_it_on_rollback() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        MachineRecipe recipe = recipe("reservation_normal", List.of());
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation first = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(first.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        first.rollback();
        assertThat(first.rolledBack()).isTrue();
        PatternStartReservation retried = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(retried.commit()).isTrue();
        assertThat(retried.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
    }

    @Test
    void successful_linked_controller_admissions_rotate_without_reordering_the_provider_group() {
        MachineControllerBlockEntity first = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerBlockEntity second = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(first, true);
        formForPattern(second, true);
        MachineRecipe recipe = recipe("reservation_rotation", List.of());
        RecipeRegistry.registerStatic(recipe);
        AtomicInteger cursor = new AtomicInteger();

        PatternStartReservation firstStart = MachineControllerBlockEntity.reserveNextPatternStart(
                List.of(first, second), cursor, PATTERN_PORT, List.of(), List.of());
        assertThat(firstStart.commit()).isTrue();
        assertThat(cursor).hasValue(1);
        PatternStartReservation secondStart = MachineControllerBlockEntity.reserveNextPatternStart(
                List.of(first, second), cursor, PATTERN_PORT, List.of(), List.of());

        assertThat(firstStart.recipe()).isEqualTo(recipe);
        assertThat(secondStart.commit()).isTrue();
        assertThat(first.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(second.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
    }

    @Test
    void linked_controller_batch_reserves_every_controller_before_advancing_rotation() {
        MachineControllerBlockEntity first = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerBlockEntity second = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(first, true);
        formForPattern(second, true);
        MachineRecipe recipe = recipe("reservation_batch_rotation", List.of());
        RecipeRegistry.registerStatic(recipe);
        AtomicInteger cursor = new AtomicInteger();

        PatternStartBatchReservation batch = MachineControllerBlockEntity.reserveNextPatternStarts(
                List.of(first, second), cursor, PATTERN_PORT, List.of(), 2L, ignored -> List.of());

        assertThat(cursor).hasValue(0);
        assertThat(batch.parallelism()).isEqualTo(2L);
        assertThat(batch.commit()).isTrue();
        assertThat(cursor).hasValue(0);
        assertThat(first.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(second.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
    }

    @Test
    void linked_rotation_skips_an_unavailable_candidate_before_committing_the_next_controller() {
        MachineControllerBlockEntity unavailable = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerBlockEntity available = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(available, true);
        MachineRecipe recipe = recipe("reservation_skip_unavailable", List.of());
        RecipeRegistry.registerStatic(recipe);
        AtomicInteger cursor = new AtomicInteger();

        PatternStartReservation reservation = MachineControllerBlockEntity.reserveNextPatternStart(
                List.of(unavailable, available), cursor, PATTERN_PORT, List.of(), List.of());

        assertThat(reservation.commit()).isTrue();
        assertThat(available.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(cursor).hasValue(0);
    }

    @Test
    void output_matching_selects_only_the_compatible_recipe() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        MachineRecipe incompatible = recipe("reservation_output_incompatible", List.of(output(Items.GOLD_NUGGET, 1)));
        MachineRecipe compatible = recipe("reservation_output_compatible", List.of(output(Items.IRON_NUGGET, 1)));
        RecipeRegistry.registerStatic(incompatible);
        RecipeRegistry.registerStatic(compatible);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 1), 1F)), List.of());

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(reservation.recipe()).isEqualTo(compatible);
        reservation.rollback();
    }

    @Test
    void output_matching_rejects_differences_in_amount_components_and_fluid() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        ItemStack componentOutput = stack(Items.IRON_NUGGET, 1);
        componentOutput.set(DataComponents.CUSTOM_NAME, Component.literal("recipe"));
        MachineRecipe exact = recipeWithOutputs("reservation_output_strict", List.of(
                new MachineOutput.ItemOutput(componentOutput, 1F),
                new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1_000), 1F)));
        RecipeRegistry.registerStatic(exact);

        assertThat(controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 2), 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1_000), 1F)), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 1), 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1_000), 1F)), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(componentOutput, 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 500), 1F)), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
    }

    @Test
    void output_matching_accepts_a_probabilistic_recipe_output() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        MachineRecipe recipe = recipeWithOutputs("reservation_output_probability", List.of(
                new MachineOutput.ItemOutput(stack(Items.GOLD_NUGGET, 1), 0.5F)));
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.GOLD_NUGGET, 1), 1F)), List.of());

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        reservation.rollback();
    }

    @Test
    void output_matching_uses_the_runtime_modifier_context() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        controller.componentRuntime().replaceModifiers(Map.of("runtime", List.of(
                MachineModifier.numeric("output", "output", 2D, "multiply", false))));
        MachineRecipe recipe = recipeWithOutputs("reservation_output_context",
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 1), 1F)));
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 2), 1F)), List.of());

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        reservation.rollback();
    }

    @Test
    void redstone_paused_controller_rejects_admission() {
        MachineControllerBlockEntity controller = factoryController();
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        RuntimeTestFixtures.republish(controller);
        RecipeRegistry.registerStatic(recipe("reservation_redstone_paused", List.of()));
        RuntimeTestFixtures.setDirectSignal(controller.getLevel(), controller.getBlockPos(), 15);
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
    }

    @Test
    void ordinary_start_cannot_preempt_a_live_reservation() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("reservation_ordinary_preemption", List.of());
        CraftingRuntime.PreparedStart prepared = runtime.preparePatternStart(recipe, 1L, List.of());

        assertThat(prepared).isNotNull();
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.start(recipe, 1).isCrafting()).isFalse();
        assertThat(runtime.commitPatternStart(prepared)).isTrue();
    }

    @Test
    void request_and_linked_input_ports_are_planned_and_committed_together() {
        ItemInputBusBlockEntity ordinaryInput = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), ordinaryInput);
        formForPattern(controller, true);
        setItem(ordinaryInput.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        LongItemStorage requestStorage = new LongItemStorage(1, 64, null);
        requestStorage.setContents(0, stack(Items.IRON_INGOT, 1), 1);
        MachineCapability request = new ItemBusCapability(requestStorage, IOType.INPUT);
        MachineRecipe recipe = recipe("reservation_atomic_inputs", List.of(input(Items.IRON_INGOT, 2)));
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of(request));

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(reservation.commit()).isTrue();
        assertThat(ordinaryInput.itemHandler().amount(0)).isZero();
        assertThat(requestStorage.amount(0)).isZero();
    }

    @Test
    void failed_request_plan_leaves_both_resources_and_the_normal_slot_available() {
        ItemInputBusBlockEntity ordinaryInput = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), ordinaryInput);
        formForPattern(controller, true);
        setItem(ordinaryInput.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        LongItemStorage requestStorage = new LongItemStorage(1, 64, null);
        requestStorage.setContents(0, stack(Items.IRON_INGOT, 1), 1);
        MachineCapability request = new ItemBusCapability(requestStorage, IOType.INPUT);
        RecipeRegistry.registerStatic(recipe("reservation_insufficient_request", List.of(input(Items.IRON_INGOT, 3))));

        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of(request)).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(ordinaryInput.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(requestStorage.amount(0)).isEqualTo(1L);

        MachineRecipe viable = recipe("reservation_request_retry", List.of(input(Items.IRON_INGOT, 2)));
        RecipeRegistry.registerStatic(viable);
        PatternStartReservation retried = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of(request));
        assertThat(retried.recipe()).isEqualTo(viable);
        retried.rollback();
    }

    @Test
    void factory_reserves_a_concrete_idle_lane_until_commit_or_rollback() {
        MachineControllerBlockEntity controller = factoryController();
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        RuntimeTestFixtures.republish(controller);
        assertThat(controller.hasFactoryController()).isTrue();
        assertThat(controller.runtimeSnapshot().linkedPortPositions()).contains(PATTERN_PORT);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("reservation_factory"), MMCR.id("reservation_factory"),
                20, List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation first = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(first.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(first.laneId()).isEqualTo("base");
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        first.rollback();
        PatternStartReservation retried = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(retried.commit()).isTrue();
        assertThat(retried.laneId()).isEqualTo("base");
    }

    @Test
    void factory_reserves_all_idle_lanes_for_one_pattern_batch() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = recipe("reservation_factory_batch", List.of());

        List<FactoryRuntime.PatternLane> lanes = factory.reservePatternStarts(recipe, 2L, List.of());

        assertThat(lanes).extracting(FactoryRuntime.PatternLane::laneId)
                .containsExactly("base", "factory-0");
    }

    @Test
    void batch_commit_starts_every_reserved_lane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = recipe("reservation_factory_batch_commit", List.of());
        List<PatternStartReservation> reservations = factory.reservePatternStarts(recipe, 2L, List.of()).stream()
                .map(lane -> reservation(recipe, factory, lane)).toList();

        PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(reservations);

        assertThat(batch.commit()).isTrue();
        assertThat(factory.activeRuntimes()).hasSize(2);
    }

    @Test
    void factory_scheduler_cannot_preempt_a_live_reservation() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("reservation_factory_preemption", List.of());
        FactoryRuntime.PatternLane lane = factory.reservePatternStart(recipe, 1L, List.of());
        assertThat(lane).isNotNull();
        PatternStartReservation reservation = reservation(recipe, factory, lane);
        factory.tick(List.of(recipe), 1, 0L);

        assertThat(factory.activeRuntimes()).isEmpty();
        assertThat(reservation.commit()).isTrue();
    }

    @Test
    void cancelling_factory_async_state_releases_pattern_reservations_and_prevents_commit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("reservation_factory_lifecycle_cancel", List.of());
        FactoryRuntime.PatternLane lane = factory.reservePatternStart(recipe, 1L, List.of());
        assertThat(lane).isNotNull();
        PatternStartReservation reservation = reservation(recipe, factory, lane);

        factory.cancelAsyncState();

        assertThat(reservation.commit()).isFalse();
        assertThat(factory.reservePatternStart(recipe, 1L, List.of())).isNotNull();
    }

    @Test
    void lowering_factory_limit_keeps_existing_live_reservations() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = recipe("reservation_factory_removal", List.of());
        FactoryRuntime.PatternLane base = factory.reservePatternStart(recipe, 1L, List.of());
        FactoryRuntime.PatternLane removable = factory.reservePatternStart(recipe, 1L, List.of());
        assertThat(base).isNotNull();
        assertThat(removable).isNotNull();
        PatternStartReservation removedReservation = reservation(recipe, factory, removable);
        PatternStartReservation baseReservation = reservation(recipe, factory, base);
        factory.setLaneLimit(1);
        assertThat(removedReservation.commit()).isTrue();

        factory.clear();
        assertThat(baseReservation.commit()).isFalse();
    }

    private static void formForPattern(MachineControllerBlockEntity controller, boolean linked) {
        controller.setFormed(true);
        controller.componentRuntime().replaceLinkedPortPositions(linked ? Set.of(PATTERN_PORT) : Set.of());
        RuntimeTestFixtures.republish(controller);
    }

    private static MachineRecipe recipe(String path, List<ItemRequirement> requirements) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 4, false, List.of(), requirements);
    }

    private static MachineRecipe recipeWithOutputs(String path, List<MachineOutput> outputs) {
        List<MachineRequirement> requirements = outputs.stream()
                .map(output -> OutputRegistry.tryToRequirement(output, List.of()))
                .toList();
        return MachineRecipe.fromCanonical(MMCR.id(path), MMCR.id("test_cube"), 20, requirements, outputs,
                List.of(), 0, 1, false, false, false, Set.of());
    }

    private static PatternStartReservation reservation(MachineRecipe recipe, FactoryRuntime factory,
                                                        FactoryRuntime.PatternLane lane) {
        return PatternStartReservation.reserved(recipe, lane.laneId(), lane.runtime(), lane.preparedStart(),
                () -> factory.releasePatternStart(lane));
    }

    private static ItemRequirement input(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement output(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack(item, count),
                1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static void setItem(LongItemStorage storage, int slot, ItemStack stack) {
        storage.setContents(slot, stack, stack.getCount());
    }

    private static MachineControllerBlockEntity factoryController() {
        ResourceLocation machineId = MMCR.id("reservation_factory");
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "reservation factory", pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

}
