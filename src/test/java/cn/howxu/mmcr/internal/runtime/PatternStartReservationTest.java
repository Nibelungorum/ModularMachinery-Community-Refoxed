package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pattern_request_cannot_top_up_materials_from_another_input_group(boolean isolatedHost) {
        ItemInputBusBlockEntity host = RuntimeTestFixtures.itemInput(PATTERN_PORT);
        ItemInputBusBlockEntity other = RuntimeTestFixtures.itemInput(new BlockPos(4, 0, 0));
        host.setInputIsolated(isolatedHost);
        other.setInputIsolated(!isolatedHost);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host, other);
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        setItem(other.itemHandler(), 0, stack(Items.IRON_INGOT, 2));
        var requested = new LongItemStorage(1, 64L, () -> { });
        setItem(requested, 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("pattern_request_group_boundary", List.of(input(Items.IRON_INGOT, 2)));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.preparePatternStart(recipe, 1L,
                List.of(new ItemBusCapability(requested, IOType.INPUT)), PATTERN_PORT)).isNull();
        assertThat(requested.amount(0)).isEqualTo(1L);
        assertThat(other.itemHandler().amount(0)).isEqualTo(2L);

        setItem(requested, 0, stack(Items.IRON_INGOT, 2));
        var prepared = runtime.preparePatternStart(recipe, 1L,
                List.of(new ItemBusCapability(requested, IOType.INPUT)), PATTERN_PORT);
        assertThat(prepared).isNotNull();
        assertThat(prepared.plan().inputScope().kind()).isEqualTo(isolatedHost
                ? InputScope.Kind.ISOLATED : InputScope.Kind.ORDINARY);
        assertThat(prepared.plan().inputScope().sources()).containsExactly(PATTERN_PORT);
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.commitPatternStart(prepared)).isTrue();
        assertThat(requested.amount(0)).isZero();
        assertThat(other.itemHandler().amount(0)).isEqualTo(2L);
        assertThat(runtime.activeInputScope()).isEqualTo(prepared.plan().inputScope());
    }

    @Test
    void unpaid_pattern_plan_is_rejected_when_input_routing_changes() {
        ItemInputBusBlockEntity host = RuntimeTestFixtures.itemInput(PATTERN_PORT);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        var requested = new LongItemStorage(1, 64L, () -> { });
        setItem(requested, 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var prepared = runtime.preparePatternStart(recipe("pattern_request_stale_scope", List.of(input(Items.IRON_INGOT, 1))),
                1L, List.of(new ItemBusCapability(requested, IOType.INPUT)), PATTERN_PORT);
        assertThat(prepared).isNotNull();
        assertThat(runtime.reservePatternStart()).isTrue();
        controller.setFullyInputIsolated(true);
        assertThat(runtime.commitPatternStart(prepared)).isFalse();
        assertThat(runtime.active()).isFalse();
        assertThat(requested.amount(0)).isEqualTo(1L);
    }

    static Stream<Arguments> patternCallbackChanges() {
        return Stream.of(false, true).flatMap(factory -> Stream.of("full", "masked_local_roundtrip", "unlink",
                "linked_roundtrip", "capability", "catalog", "module_roundtrip", "lifecycle")
                .map(change -> Arguments.of(factory, change)));
    }

    @ParameterizedTest
    @MethodSource("patternCallbackChanges")
    void pattern_before_start_only_recaptures_pure_isolation_changes(boolean factory, String change) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicReference<PaidPatternFixture> owner = new AtomicReference<>();
            AtomicInteger callbacks = new AtomicInteger();
            RecipeBehavior behavior = RecipeBehavior.builder().beforeStart(context -> {
                callbacks.incrementAndGet();
                PaidPatternFixture fixture = owner.get();
                MachineControllerBlockEntity controller = fixture.controller;
                controller.setFullyInputIsolated(true);
                var components = controller.componentRuntime();
                switch (change) {
                    case "masked_local_roundtrip" -> {
                        ItemInputBusBlockEntity host = (ItemInputBusBlockEntity) components.components().stream()
                                .filter(component -> PATTERN_PORT.equals(component.getPos())).findFirst().orElseThrow().getContainer();
                        host.setInputIsolated(true);
                        controller.onInputIsolationChanged();
                        host.setInputIsolated(false);
                        controller.onInputIsolationChanged();
                    }
                    case "unlink" -> components.replaceLinkedPortPositions(Set.of());
                    case "linked_roundtrip" -> {
                        var linked = components.linkedPortPositions();
                        components.replaceLinkedPortPositions(Set.of());
                        components.replaceLinkedPortPositions(linked);
                    }
                    case "capability" -> {
                        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(PATTERN_PORT);
                        replacement.setLevel(controller.getLevel());
                        components.replaceComponents(components.components().stream().map(component ->
                                PATTERN_PORT.equals(component.getPos()) ? new ProcessingComponent(
                                        new MachineComponent(replacement.kind(), replacement.ioType()), replacement,
                                        PATTERN_PORT, PATTERN_PORT, (String) null) : component).toList());
                    }
                    case "catalog" -> RecipeRegistry.clearForTesting();
                    case "module_roundtrip" -> {
                        var status = components.moduleConnectionStatus();
                        int installed = components.installedModuleCount();
                        components.replaceModuleConnectionState(ModuleConnectionStatus.connected(MMCR.id("pattern_callback_host")), 1);
                        components.replaceModuleConnectionState(status, installed);
                    }
                    case "lifecycle" -> controller.setMachine(null);
                }
            }).build();
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_callback_fence", behavior);
            owner.set(fixture);
            if (change.equals("masked_local_roundtrip")) fixture.controller.setFullyInputIsolated(true);
            List<MachineCapability> request = List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT));
            PatternStartReservation admission;
            CraftingRuntime runtime = fixture.runtime;
            if (factory) {
                FactoryRuntime lanes = new FactoryRuntime();
                lanes.ensureBaseLane(fixture.controller);
                var lane = lanes.reservePatternStart(fixture.recipe, 1L, request, PATTERN_PORT);
                admission = lane == null ? PatternStartReservation.unavailable() : reservation(fixture.recipe, lanes, lane);
                if (lane != null) runtime = lane.runtime();
            } else {
                admission = fixture.controller.reservePatternStart(PATTERN_PORT, List.of(), request);
            }
            assertThat(callbacks).hasValue(1);
            if (change.equals("full") || change.equals("masked_local_roundtrip")) {
                assertThat(admission.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                assertThat(admission.commit()).isTrue();
                assertThat(runtime.activeInputScope()).isEqualTo(new InputScope(InputScope.Kind.ISOLATED, List.of(PATTERN_PORT)));
                assertThat(fixture.inputs.amount(0)).isEqualTo(1L);
                assertThat(fixture.network.amount()).isEqualTo(96L);
                assertThat(fixture.buffer.reserved()).isEqualTo(4L);
            } else {
                assertThat(admission.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
                assertThat(runtime.active()).isFalse();
                assertThat(fixture.inputs.amount(0)).isEqualTo(2L);
                assertThat(fixture.network.amount()).isEqualTo(100L);
                assertThat(fixture.buffer.amount()).isZero();
                assertThat(fixture.buffer.reserved()).isZero();
                assertThat(fixture.networkCommits).hasValue(0);
            }
        }
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
        formForPattern(controller, true);
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

    @Test
    void caller_owned_notification_throw_reports_payment_and_keeps_the_real_allocation() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_paid_throw");
            PatternStartReservation reservation = fixture.reserve();
            RecipeCommitLedger payment = fixture.runtime.ledger();
            fixture.notification.set(() -> { throw new IllegalStateException("root notification"); });

            assertThat(reservation.commit()).isTrue();
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(reservation.rolledBack()).isFalse();
            assertThat(fixture.afterCommit).hasValue(0);
            fixture.assertPausedPayment(payment);
            assertThat(reservation.commit()).isFalse();
            reservation.close();
            fixture.assertPausedPayment(payment);
            fixture.runtime.invalidate();
            assertThat(fixture.buffer.reserved()).isZero();
            assertThat(fixture.buffer.idleExcess()).isEqualTo(4L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void caller_owned_cancelled_payment_is_terminal_without_activation_or_after_commit(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_paid_cancel");
            PatternStartReservation reservation = fixture.reserve();
            RecipeCommitLedger payment = fixture.runtime.ledger();
            fixture.notification.set(() -> {
                fixture.runtime.invalidate();
                if (notifyThrow) throw new IllegalStateException("cancelled root notification");
            });

            assertThat(reservation.commit()).isTrue();
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(reservation.rolledBack()).isFalse();
            assertThat(payment.state().paidAttempts()).isZero();
            assertThat(fixture.runtime.active()).isFalse();
            assertThat(fixture.afterCommit).hasValue(0);
            reservation.close();
            assertThat(reservation.commit()).isFalse();
            fixture.assertCancelledPayment();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void old_paid_continuation_does_not_release_a_reentrant_new_pending_pattern(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_paid_reentrant");
            PatternStartReservation old = fixture.reserve();
            AtomicReference<PatternStartReservation> replacement = new AtomicReference<>();
            AtomicReference<RecipeCommitLedger> replacementPayment = new AtomicReference<>();
            fixture.notification.set(() -> {
                fixture.runtime.invalidate();
                replacement.set(fixture.reserve());
                replacementPayment.set(fixture.runtime.ledger());
                if (notifyThrow) throw new IllegalStateException("old root notification");
            });

            assertThat(old.commit()).isTrue();
            old.close();
            fixture.assertPendingReplacement(replacement.get(), replacementPayment.get());
            assertThat(fixture.afterCommit).hasValue(0);
            assertThat(replacement.get().commit()).isTrue();
            fixture.assertReplacementPaid();
        }
    }

    @Test
    void ownership_write_failure_keeps_native_payment_and_does_not_invite_provider_replay() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_unpaid_failure");
            PatternStartReservation reservation = fixture.reserve();
            RecipeCommitLedger payment = fixture.runtime.ledger();

            assertThat(reservation.commit(() -> { throw new IllegalStateException("native holder settlement"); }))
                    .isTrue();
            assertThat(reservation.rolledBack()).isFalse();
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            fixture.assertPausedPayment(payment, 1L);
            assertThat(fixture.afterCommit).hasValue(0);
            assertThat(reservation.commit()).isFalse();
        }
    }

    static Stream<Arguments> finalPatternWriteChanges() {
        return Stream.of(false, true).flatMap(direct -> Stream.of("full", "local", "lifecycle", "ledger")
                .map(change -> Arguments.of(direct, change)));
    }

    @ParameterizedTest
    @MethodSource("finalPatternWriteChanges")
    void native_ownership_write_after_payment_never_rolls_back_joint_inputs(boolean direct, String change) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_final_write", true);
            RecipeCommitLedger payment;
            if (direct) {
                var prepared = fixture.runtime.preparePatternStart(fixture.recipe, 1L,
                        List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT)), PATTERN_PORT);
                assertThat(prepared).isNotNull();
                assertThat(fixture.runtime.reservePatternStart()).isTrue();
                payment = fixture.runtime.ledger();
                assertThat(fixture.runtime.commitPatternStart(prepared, () -> {
                    fixture.assertProvisionalJointPayment(payment);
                    fixture.changeUnpaidFence(change);
                })).isTrue();
            } else {
                PatternStartReservation reservation = fixture.reserve();
                payment = fixture.runtime.ledger();
                assertThat(reservation.commit(() -> {
                    fixture.assertProvisionalJointPayment(payment);
                    fixture.changeUnpaidFence(change);
                })).isTrue();
                assertThat(reservation.rolledBack()).isFalse();
                assertThat(reservation.commit()).isFalse();
            }
            assertThat(payment.state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.physicalInput.itemHandler().amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(96L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void root_notification_isolation_change_keeps_paid_joint_scope(boolean direct) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_final_paid_scope", true);
            fixture.notification.set(() -> fixture.controller.setFullyInputIsolated(true));
            if (direct) {
                var prepared = fixture.runtime.preparePatternStart(fixture.recipe, 1L,
                        List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT)), PATTERN_PORT);
                assertThat(prepared).isNotNull();
                assertThat(fixture.runtime.reservePatternStart()).isTrue();
                assertThat(fixture.runtime.commitPatternStart(prepared)).isTrue();
            } else {
                assertThat(fixture.reserve().commit()).isTrue();
                assertThat(fixture.afterCommit).hasValue(1);
            }
            assertThat(fixture.controller.isFullyInputIsolated()).isTrue();
            assertThat(fixture.runtime.active()).isTrue();
            assertThat(fixture.runtime.activeInputScope()).isEqualTo(new InputScope(InputScope.Kind.ORDINARY,
                    List.of(fixture.physicalInput.getBlockPos(), PATTERN_PORT)));
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.physicalInput.itemHandler().amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(96L);
            assertThat(fixture.buffer.amount()).isEqualTo(4L);
            assertThat(fixture.buffer.reserved()).isEqualTo(4L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void factory_old_paid_release_keeps_reentrant_lane_reservation_accounting(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("factory_pattern_reentrant");
            FactoryRuntime factory = new FactoryRuntime();
            factory.ensureBaseLane(fixture.controller);
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT));
            FactoryRuntime.PatternLane oldLane = factory.reservePatternStart(fixture.recipe, 1L, capabilities, PATTERN_PORT);
            assertThat(oldLane).isNotNull();
            PatternStartReservation old = reservation(fixture.recipe, factory, oldLane)
                    .afterCommit(fixture.afterCommit::incrementAndGet);
            AtomicReference<FactoryRuntime.PatternLane> newLane = new AtomicReference<>();
            AtomicReference<PatternStartReservation> replacement = new AtomicReference<>();
            AtomicReference<RecipeCommitLedger> payment = new AtomicReference<>();
            fixture.notification.set(() -> {
                factory.cancelAsyncState();
                newLane.set(factory.reservePatternStart(fixture.recipe, 1L, capabilities, PATTERN_PORT));
                assertThat(newLane.get()).isNotNull();
                replacement.set(reservation(fixture.recipe, factory, newLane.get())
                        .afterCommit(fixture.afterCommit::incrementAndGet));
                payment.set(newLane.get().runtime().ledger());
                if (notifyThrow) throw new IllegalStateException("factory old root notification");
            });

            assertThat(old.commit()).isTrue();
            old.close();
            assertThat(oldLane.runtime()).isSameAs(newLane.get().runtime());
            assertThat(newLane.get().runtime().pendingPatternRecipeId()).isEqualTo(fixture.recipe.id());
            assertThat(newLane.get().runtime().ledger()).isSameAs(payment.get());
            assertThat(payment.get().state().paidAttempts()).isZero();
            assertThat(payment.get().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(factory.reservePatternStart(fixture.recipe, 1L, capabilities, PATTERN_PORT)).isNull();
            assertThat(fixture.afterCommit).hasValue(0);
            fixture.assertCancelledPayment();
            assertThat(replacement.get().commit()).isTrue();
            assertThat(newLane.get().runtime().active()).isTrue();
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(92L);
            assertThat(fixture.buffer.reserved()).isEqualTo(4L);
            // Only the old payment notification throws; the replacement successfully activates.
            assertThat(fixture.afterCommit).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void same_reservation_nested_commit_is_rejected_through_writes_and_root_notification(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_attempt_guard");
            PatternStartReservation reservation = fixture.reserve();
            AtomicInteger nestedWrites = new AtomicInteger();
            AtomicInteger notifications = new AtomicInteger();
            Runnable nestedCommit = () -> assertThat(reservation.commit(() -> nestedWrites.incrementAndGet()))
                    .isFalse();
            fixture.notification.set(() -> {
                notifications.incrementAndGet();
                nestedCommit.run();
                if (notifyThrow) throw new IllegalStateException("guarded root notification");
            });

            AtomicInteger ownershipWrites = new AtomicInteger();
            assertThat(reservation.commit(() -> {
                ownershipWrites.incrementAndGet();
                nestedCommit.run();
            })).isTrue();
            assertThat(ownershipWrites).hasValue(1);
            assertThat(notifications).hasValue(1);
            assertThat(nestedWrites).hasValue(0);
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(reservation.rolledBack()).isFalse();
            if (notifyThrow) fixture.assertPausedPayment(fixture.runtime.ledger());
            else fixture.assertSinglePayment();
            reservation.close();
            assertThat(reservation.commit()).isFalse();
            assertThat(fixture.networkCommits).hasValue(1);
            assertThat(ownershipWrites).hasValue(1);
            assertThat(fixture.afterCommit).hasValue(notifyThrow ? 0 : 1);

            fixture.runtime.invalidate();
            assertThat(fixture.reserve().commit()).isTrue();
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(92L);
            assertThat(fixture.networkCommits).hasValue(2);
            assertThat(fixture.buffer.reserved()).isEqualTo(4L);
            assertThat(fixture.afterCommit).hasValue(notifyThrow ? 1 : 2);
        }
    }

    /** Shared real caller-owned payment boundary for single and multi-reservation tests.
     * @author howxu <dev@howxu.cn>
     */
    static final class PaidPatternFixture {
        final AtomicReference<Runnable> notification = new AtomicReference<>();
        final AtomicReference<Runnable> prefetchWrite = new AtomicReference<>();
        final AtomicInteger afterCommit = new AtomicInteger();
        final AtomicInteger networkCommits = new AtomicInteger();
        final FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        final LongValueStorage network = new LongValueStorage(100L, 100L, null);
        final LongItemStorage inputs = new LongItemStorage(1, 64L, () -> {
            // Native start commits prefetch before inputs; observe the actual final item mutation.
            Runnable action = notification.getAndSet(null);
            if (action != null) action.run();
        });
        final MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        final ItemInputBusBlockEntity physicalInput = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        final CraftingRuntime runtime;
        final MachineRecipe recipe;

        PaidPatternFixture(String name) {
            this(name, null);
        }

        PaidPatternFixture(String name, RecipeBehavior behavior) {
            this(name, behavior, false);
        }

        PaidPatternFixture(String name, boolean jointInputs) {
            this(name, null, jointInputs);
        }

        private PaidPatternFixture(String name, RecipeBehavior behavior, boolean jointInputs) {
            network.setAmount(100L);
            int requestAmount = jointInputs ? 1 : 2;
            inputs.setContents(0, stack(Items.IRON_INGOT, requestAmount), requestAmount);
            var flux = new FluxEnergyInputCapability(buffer, "pattern", amount -> Optional.of(() -> {
                networkCommits.incrementAndGet();
                assertThat(network.extract(amount, false)).isEqualTo(amount);
                Runnable action = prefetchWrite.getAndSet(null);
                if (action != null) action.run();
                return CapabilityResult.successful();
            }));
            if (behavior != null) {
                Machine original = controller.currentStructureSnapshot().configuredMachine();
                controller.setMachine(new Machine() {
                    @Override public ResourceLocation registryName() { return original.registryName(); }
                    @Override public BlockArray pattern() { return original.pattern(); }
                    @Override public MachineControllerSpec controller() { return original.controller(); }
                    @Override public RecipeBehavior behavior() { return behavior; }
                });
            }
            var components = new ArrayList<>(controller.componentRuntime().components());
            if (jointInputs) {
                physicalInput.setLevel(controller.getLevel());
                setItem(physicalInput.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
                components.add(new ProcessingComponent(new MachineComponent(physicalInput.kind(), physicalInput.ioType()),
                        physicalInput, physicalInput.getBlockPos(), physicalInput.getBlockPos(), (String) null));
            }
            components.add(new ProcessingComponent(null, new PatternFluxHost(flux), BlockPos.ZERO, BlockPos.ZERO,
                    (String) null));
            controller.componentRuntime().replaceComponents(components);
            formForPattern(controller, true);
            runtime = RuntimeTestFixtures.craftingRuntime(controller);
            recipe = MachineRecipe.fromCanonical(MMCR.id(name), MMCR.id("test_cube"), 2,
                    List.of(input(Items.IRON_INGOT, jointInputs ? 2 : 1), new EnergyRequirement(2L)), List.of(),
                    List.of(), 0, 1, false, false, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
        }

        PatternStartReservation reserve() {
            PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT, List.of(),
                    List.of(new ItemBusCapability(inputs, IOType.INPUT)));
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            return reservation.afterCommit(afterCommit::incrementAndGet);
        }

        void changeUnpaidFence(String change) {
            switch (change) {
                case "full" -> controller.setFullyInputIsolated(true);
                case "local" -> {
                    physicalInput.setInputIsolated(true);
                    controller.onInputIsolationChanged();
                }
                case "lifecycle" -> controller.setMachine(null);
                case "ledger" -> runtime.invalidate();
                default -> throw new IllegalArgumentException(change);
            }
        }

        void assertProvisionalJointPayment(RecipeCommitLedger payment) {
            assertThat(payment.state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isZero();
            assertThat(physicalInput.itemHandler().amount(0)).isZero();
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isEqualTo(4L);
        }

        void assertPausedPayment(RecipeCommitLedger payment) {
            assertPausedPayment(payment, 0L);
        }

        void assertPausedPayment(RecipeCommitLedger payment, long confirmedAttempts) {
            assertThat(runtime.ledger()).isSameAs(payment);
            assertThat(payment.state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(payment.state().paidAttempts()).isEqualTo(confirmedAttempts);
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved() + buffer.idleExcess()).isEqualTo(4L);
            if (runtime.active()) assertAllocation();
            var state = payment.state();
            runtime.resume();
            runtime.tick();
            runtime.finish();
            assertThat(payment.state()).isEqualTo(state);
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.reserved() + buffer.idleExcess()).isEqualTo(4L);
        }

        void assertSinglePayment() {
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(networkCommits).hasValue(1);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isEqualTo(4L);
            assertThat(buffer.idleExcess()).isZero();
            assertThat(afterCommit).hasValue(1);
            assertAllocation();
        }

        void assertCancelledPayment() {
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isZero();
            assertThat(buffer.idleExcess()).isEqualTo(4L);
        }

        void assertPendingReplacement(PatternStartReservation replacement, RecipeCommitLedger payment) {
            assertThat(replacement.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            assertThat(replacement.rolledBack()).isFalse();
            assertThat(runtime.active()).isFalse();
            assertThat(runtime.pendingPatternRecipeId()).isEqualTo(recipe.id());
            assertThat(runtime.ledger()).isSameAs(payment);
            assertThat(payment.state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(payment.state().paidAttempts()).isZero();
            assertCancelledPayment();
        }

        void assertReplacementPaid() {
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isZero();
            assertThat(network.amount()).isEqualTo(92L);
            assertThat(buffer.amount()).isEqualTo(8L);
            assertThat(buffer.reserved()).isEqualTo(4L);
            assertThat(buffer.idleExcess()).isEqualTo(4L);
            assertThat(afterCommit).hasValue(1);
            assertAllocation();
        }

        void assertSinglePaymentAfterSecondStart(int expectedCallbacks) {
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isZero();
            assertThat(network.amount()).isEqualTo(92L);
            assertThat(networkCommits).hasValue(2);
            assertThat(buffer.amount()).isEqualTo(8L);
            assertThat(buffer.reserved()).isEqualTo(4L);
            assertThat(buffer.idleExcess()).isEqualTo(4L);
            assertThat(afterCommit).hasValue(expectedCallbacks);
        }

        private void assertAllocation() {
            CompoundTag saved = new CompoundTag();
            runtime.save(saved, HolderLookup.Provider.create(Stream.empty()));
            var allocations = saved.getCompound("recipe").getCompound("data")
                    .getList("prefetched_energy_allocations", 10);
            assertThat(allocations.size()).isEqualTo(1);
            assertThat(allocations.getCompound(0).getString("key")).isEqualTo("pattern");
            assertThat(allocations.getCompound(0).getLong("amount")).isEqualTo(4L);
        }
    }

    /** Exposes the real Flux facet through the controller's usual component boundary.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PatternFluxHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot capabilities;

        private PatternFluxHost(MachineCapability capability) {
            this(RuntimeTestFixtures.energyInput(BlockPos.ZERO), capability);
        }

        private PatternFluxHost(BlockEntity entity, MachineCapability capability) {
            super(entity.getType(), entity.getBlockPos(), entity.getBlockState());
            capabilities = new CapabilitySnapshot(List.of(capability));
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() { return capabilities; }
    }

    private static void formForPattern(MachineControllerBlockEntity controller, boolean linked) {
        if (linked && controller.componentRuntime().components().stream()
                .noneMatch(component -> PATTERN_PORT.equals(component.getPos()))) {
            ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(PATTERN_PORT);
            source.setLevel(controller.getLevel());
            List<ProcessingComponent> components = new ArrayList<>(controller.componentRuntime().components());
            components.add(new ProcessingComponent(new MachineComponent(source.kind(), source.ioType()), source,
                    PATTERN_PORT, PATTERN_PORT, (String) null));
            controller.componentRuntime().replaceComponents(components);
        }
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
