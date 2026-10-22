package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.RecipeCommitLedger;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.LevelStub;
import net.minecraft.core.BlockPos;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE;
import static cn.howxu.mmcr.internal.recipe.RecipeThread.StartResult.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies generic failure matcher delegation for factory recipe threads.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryRecipeThreadTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private ServerThreadTestScope serverThreadScope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void installServerThread() throws Exception {
        serverThreadScope = ServerThreadTestScope.open();
    }

    @AfterEach
    void restoreServerThread() throws Exception {
        if (serverThreadScope != null) serverThreadScope.close();
    }

    @Test
    void delegates_custom_requirement_wakeups_without_concrete_requirement_dispatch() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "factory_wakeup");
            RequirementHandler<TestRequirement> handler = new RequirementHandler<>() {
                @Override
                public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                            PlanningContext context) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }

                @Override
                public List<ResourceWakeup> resourceWakeups(TestRequirement requirement) {
                    return List.of(new ResourceWakeup(Set.of(BuiltinFailureReasons.MISSING_INPUT.id()), WakeupReason.INPUT_AVAILABLE,
                            resource -> resource.equals("virtual-resource")));
                }
            };
            RequirementType<TestRequirement> type = new RequirementType.Definition<>(id,
                    MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), handler);
            RequirementHandlerRegistry.register(type);

            EnumMap<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers =
                    new EnumMap<>(ResourceAvailabilityNotifier.Reason.class);
            FactoryRecipeThread.addRequirementMatchers(matchers,
                    new TestRequirement(type, RecipeModifier.IOType.INPUT), BuiltinFailureReasons.MISSING_INPUT.id());

            assertThat(matchers).containsKey(INPUT_AVAILABLE);
            assertThat(matchers.get(INPUT_AVAILABLE).getFirst().test("virtual-resource")).isTrue();
            assertThat(matchers.get(INPUT_AVAILABLE).getFirst().test("other-resource")).isFalse();
        }
    }

    @Test
    void load_migrates_legacy_search_failure_reason_into_a_typed_id() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("legacy_search_failure"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(
                        RecipeModifier.IOType.INPUT, null, 1, new ItemStack(Items.IRON_INGOT, 1))));

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isEqualTo(NO_MATCH);
        CompoundTag legacy = new CompoundTag();
        thread.save(legacy, EMPTY_LOOKUP);
        legacy.remove("search_failure_reason_id");
        legacy.putString("search_failure_reason", "insufficient_resource");

        FactoryRecipeThread restored = FactoryRecipeThread.load(legacy, controller, EMPTY_LOOKUP, List.of(recipe));

        assertThat(restored.searchFailureReason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT.id());
    }

    @Test
    void factory_lane_save_load_preserves_final_payment_and_does_not_repay_continuous_energy() {
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.energyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        var lane = FactoryRecipeThread.simple(controller, "factory-paid");
        var recipe = RecipeTestSupport.create(MMCR.id("factory_paid_save"), MMCR.id("test_cube"), 1,
                List.of(new EnergyRequirement(7L)), List.of());
        RecipeRegistry.registerStatic(recipe);
        try {
            lane.runtime().start(recipe, 1L);
            long afterStart = energy.energyStorage().getAmountAsLong();
            lane.runtime().tick();
            var output = new CompoundTag();
            lane.save(output, EMPTY_LOOKUP);
            var restored = FactoryRecipeThread.load(output, controller, EMPTY_LOOKUP, List.of(recipe));
            assertThat(restored.asyncLaneId()).isEqualTo("factory-paid");
            assertThat(restored.runtime().ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.WAITING_FINISH);
            assertThat(restored.runtime().ledger().state().finalTickPaid()).isTrue();
            LevelStub.setGameTime(controller.getLevel(), 1L);
            restored.runtime().tick();
            restored.runtime().finish();
            assertThat(restored.runtime().active()).isFalse();
            assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
            var terminal = new CompoundTag();
            restored.save(terminal, EMPTY_LOOKUP);
            var completed = FactoryRecipeThread.load(terminal, controller, EMPTY_LOOKUP, List.of(recipe));
            assertThat(completed.runtime().active()).isFalse();
        } finally {
            RecipeRegistry.clearForTesting();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void async_finish_restart_defers_single_recipe_admission_to_the_actual_start_commit(boolean supplied) throws Exception {
        TestBootstrap.bootstrapCapabilities();
        MachineDefinitions.clearForTesting();
        RuntimeTestFixtures.registerRecipePool(MMCR.id("test_cube"));
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            AtomicInteger planningCalls = new AtomicInteger();
            AtomicReference<RequirementType<TestRequirement>> registeredType = new AtomicReference<>();
            RequirementType<TestRequirement> type = new RequirementType.Definition<>(MMCR.id("factory_restart_admission"),
                    MapCodec.unit(() -> new TestRequirement(registeredType.get(), IOType.INPUT)),
                    new RequirementHandler<>() {
                        @Override
                        public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                                    PlanningContext context) {
                            planningCalls.incrementAndGet();
                            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                        }
                    });
            registeredType.set(type);
            RequirementHandlerRegistry.register(type);
            var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
            energy.energyStorage().setAmount(100L);
            var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
            RuntimeTestFixtures.formStructureWithComponents(controller, controller.structureSnapshot().configuredMachine(), energy);
            controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                    new MachineComponent(energy.kind(), energy.ioType()), energy,
                    energy.getBlockPos(), energy.getBlockPos(), (String) null)));
            RuntimeTestFixtures.republish(controller);
            ServerLevel level = (ServerLevel) controller.getLevel();
            assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
            var lane = FactoryRecipeThread.simple(controller, "factory-restart-admission");
            var recipe = RecipeTestSupport.create(MMCR.id("factory_restart_single_admission"), MMCR.id("test_cube"), 1,
                    List.of(new EnergyRequirement(7L), new TestRequirement(type, IOType.INPUT)), List.of());
            RecipeRegistry.registerStatic(recipe);
            try {
                assertThat(lane.runtime().start(recipe, 1L).isCrafting())
                        .as("start failure: %s", lane.runtime().failure()).isTrue();
                lane.onStarted();
                lane.runtime().ensureInstalledProgram();
                lane.runtime().tick();
                lane.runtime().finish();
                lane.onRecipeFinished();
                assertThat(lane.runtime().active()).isFalse();
                assertThat(lane.runtime().restartProgramReady(recipe)).isTrue();
                if (!supplied) energy.energyStorage().setAmount(0L);
                planningCalls.set(0);
                long beforeRestart = energy.energyStorage().getAmountAsLong();
                var snapshot = controller.currentRuntimeSnapshot();
                var context = new FactorySearchContext(snapshot, List.of(recipe), controller.componentRuntime().capabilities(),
                        controller.componentRuntime().modifierList(), RecipeRegistry.catalogForPool(recipe.recipePoolId()).version(),
                        controller.resourceAvailabilityEpoch(), 1L, level.getGameTime());

                assertThat(lane.prepareAsyncFinishRestart(context, List.of(recipe), 1L, snapshot.structure().version(),
                        snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion())).isEqualTo(PENDING);
                assertThat(lane.isStartPending()).isTrue();
                assertThat(planningCalls.get()).isZero();
                assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(beforeRestart);

                SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
                sharedIo.beginLevelTick(level.getGameTime());
                sharedIo.resolve(level);

                assertThat(lane.isStartPending()).isFalse();
                assertThat(lane.runtime().active()).isEqualTo(supplied);
                if (supplied) {
                    assertThat(planningCalls.get()).isEqualTo(1);
                    assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(beforeRestart);
                    assertThat(lane.runtime().tickCount()).isZero();
                    assertThat(lane.runtime().recipe()).isSameAs(recipe);
                } else {
                    assertThat(lane.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
                    assertThat(lane.hasFullSearchPending()).isTrue();
                    assertThat(lane.searchFailureKey()).isNull();
                    assertThat(energy.energyStorage().getAmountAsLong()).isZero();
                }
            } finally {
                MachineAsyncCoordinator.discard(level);
                SharedIoCoordinator.discard(level);
                StructureClaimRegistry.discard(level);
                RecipeRegistry.clearForTesting();
            }
        }
    }

    @Test
    void load_clears_retry_state_when_legacy_search_failure_reason_is_unknown() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("unknown_search_failure"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(
                        RecipeModifier.IOType.INPUT, null, 1, new ItemStack(Items.IRON_INGOT, 1))));

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isEqualTo(NO_MATCH);
        CompoundTag legacy = new CompoundTag();
        thread.save(legacy, EMPTY_LOOKUP);
        legacy.remove("search_failure_reason_id");
        legacy.putString("search_failure_reason", "legacy:removed_reason");

        FactoryRecipeThread restored = FactoryRecipeThread.load(legacy, controller, EMPTY_LOOKUP);

        assertThat(restored.searchFailureReason()).isNull();
        assertThat(restored.searchFailureKey()).isNull();
    }

    @Test
    void load_clears_retry_state_when_legacy_search_failure_reason_is_malformed() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("malformed_search_failure"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(
                        RecipeModifier.IOType.INPUT, null, 1, new ItemStack(Items.IRON_INGOT, 1))));

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isEqualTo(NO_MATCH);
        CompoundTag legacy = new CompoundTag();
        thread.save(legacy, EMPTY_LOOKUP);
        legacy.remove("search_failure_reason_id");
        legacy.putString("search_failure_reason", "not a valid identifier");

        FactoryRecipeThread restored = FactoryRecipeThread.load(legacy, controller, EMPTY_LOOKUP);

        assertThat(restored.searchFailureReason()).isNull();
        assertThat(restored.searchFailureKey()).isNull();
    }

    @Test
    void factory_search_uses_the_controller_selected_recipe_pool() {
        ResourceLocation machineId = MMCR.id("factory_selected_pool_machine");
        ResourceLocation firstPool = MMCR.id("factory_selected_pool_first");
        ResourceLocation secondPool = MMCR.id("factory_selected_pool_second");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(new DynamicMachine(machineId, "factory multi pool", new BlockArray(Map.of())));
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("factory_selected_pool_first_recipe"), firstPool,
                20, List.of(), List.of());
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("factory_selected_pool_second_recipe"), secondPool,
                20, List.of(), List.of());
        Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.dynamicSnapshot();
        try {
            RecipeRegistry.replaceDynamic(Map.of(first.id(), first, second.id(), second), ignored -> true);
            FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

            assertThat(thread.candidatesFor(null)).containsExactly(second);
            assertThat(thread.searchAndStartRecipe(null, 1,
                    controller.runtimeSnapshot().structure().version()).ownsStart()).isTrue();
            assertThat(thread.runtime().recipe()).isEqualTo(second);
        } finally {
            RecipeRegistry.replaceDynamic(previous, ignored -> true);
        }
    }

    @Test
    void valid_last_source_survives_component_state_change_and_replans_its_execution() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        RuntimeTestFixtures.formStructure(controller, controller.structureSnapshot().configuredMachine());
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_source_new_state"), MMCR.id("test_cube"),
                1, List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        try {
            assertThat(lane.searchAndStartRecipe(List.of(recipe), 1L,
                    controller.currentRuntimeSnapshot().structure().version())).isEqualTo(STARTED);
            lane.runtime().ensureInstalledProgram();
            lane.runtime().tick();
            lane.runtime().finish();
            lane.onRecipeFinished();
            controller.componentRuntime().replaceLinkedPortPositions(Set.of(new BlockPos(8, 0, 0)));
            RuntimeTestFixtures.republish(controller);
            assertThat(lane.runtime().restartProgramReady(recipe)).isFalse();
            var current = controller.currentRuntimeSnapshot();

            assertThat(lane.tryRestartLastRecipe(List.of(recipe), 1L, current.structure().version(),
                    current.capabilityVersion(), current.modifierVersion(), current.stateVersion())).isEqualTo(STARTED);
            assertThat(lane.runtime().recipe()).isSameAs(recipe);
            assertThat(lane.runtime().active()).isTrue();
            assertThat(lane.hasFullSearchPending()).isFalse();
        } finally {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void catalog_reload_rebinds_only_a_compatible_last_recipe_definition(boolean compatible) {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        RuntimeTestFixtures.formStructure(controller, controller.structureSnapshot().configuredMachine());
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller);
        MachineRecipe original = RecipeTestSupport.create(MMCR.id("factory_source_reload"), MMCR.id("test_cube"),
                1, List.of(), List.of());
        MachineRecipe replacement = RecipeTestSupport.create(original.id(), original.recipePoolId(),
                compatible ? 1 : 2, List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(original.id(), original));
        try {
            assertThat(lane.searchAndStartRecipe(List.of(original), 1L,
                    controller.currentRuntimeSnapshot().structure().version())).isEqualTo(STARTED);
            lane.runtime().tick();
            lane.runtime().finish();
            lane.onRecipeFinished();
            RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));
            var current = controller.currentRuntimeSnapshot();

            RecipeThread.StartResult result = lane.tryRestartLastRecipe(List.of(replacement), 1L,
                    current.structure().version(), current.capabilityVersion(), current.modifierVersion(), current.stateVersion());

            assertThat(result).isEqualTo(compatible ? STARTED : NO_MATCH);
            assertThat(lane.runtime().active()).isEqualTo(compatible);
            assertThat(lane.hasFullSearchPending()).isEqualTo(!compatible);
            if (compatible) assertThat(lane.runtime().recipe()).isSameAs(
                    RecipeRegistry.catalogForPool(replacement.recipePoolId()).recipes().getFirst());
            else assertThat(lane.lastRecipeId()).isNull();
        } finally {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failed_catalog_hint_cannot_reenter_full_search_with_its_old_source(boolean changedDefinition) {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        var input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        var components = controller.componentRuntime().components();
        RuntimeTestFixtures.formStructureWithComponents(controller, controller.structureSnapshot().configuredMachine(), input);
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(controller);
        MachineRecipe original = RecipeTestSupport.create(MMCR.id("factory_hint_invalid_source"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                        new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        MachineRecipe changed = RecipeTestSupport.create(original.id(), original.recipePoolId(), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                        new ItemRequirement(IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY)));
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller);
        RecipeRegistry.replaceDynamic(Map.of(original.id(), original));
        try {
            input.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 2L);
            assertThat(lane.searchAndStartRecipe(List.of(original), 1L,
                    controller.currentRuntimeSnapshot().structure().version())).isEqualTo(STARTED);
            lane.runtime().tick();
            lane.runtime().finish();
            lane.onRecipeFinished();
            RecipeRegistry.replaceDynamic(changedDefinition ? Map.of(changed.id(), changed) : Map.of());
            var snapshot = controller.currentRuntimeSnapshot();

            assertThat(lane.tryRestartLastRecipe(List.of(original), 1L, snapshot.structure().version(),
                    snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion())).isEqualTo(NO_MATCH);
            assertThat(lane.hasFullSearchPending()).isTrue();
            assertThat(lane.searchAndStartRecipe(List.of(original), 1L, snapshot.structure().version())).isEqualTo(NO_MATCH);
            assertThat(lane.runtime().active()).isFalse();
            assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
            assertThat(lane.hasFullSearchPending()).isFalse();
        } finally {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        }
    }

    @Test
    void unregistered_supplied_source_is_valid_only_until_its_catalog_changes() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        var input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        MachineRecipe supplied = RecipeTestSupport.create(MMCR.id("factory_unregistered_source"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                        new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller);
        try {
            input.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 2L);
            assertThat(lane.searchAndStartRecipe(List.of(supplied), 1L, 0L)).isEqualTo(STARTED);
            lane.runtime().tick();
            lane.runtime().finish();
            lane.onRecipeFinished();
            MachineRecipe other = RecipeTestSupport.create(MMCR.id("factory_unrelated_catalog_change"), supplied.recipePoolId(),
                    20, List.of(), List.of());
            RecipeRegistry.registerStatic(other);

            assertThat(lane.searchAndStartRecipe(List.of(supplied), 1L, 0L)).isEqualTo(NO_MATCH);
            assertThat(lane.runtime().active()).isFalse();
            assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
        } finally {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        }
    }

    @Test
    void replacing_core_candidates_ends_the_old_restart_opportunity_without_starting_outside_the_lane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe oldRecipe = RecipeTestSupport.create(MMCR.id("factory_core_old_source"), MMCR.id("test_cube"),
                1, List.of(), List.of());
        MachineRecipe allowed = RecipeTestSupport.create(MMCR.id("factory_core_new_source"), MMCR.id("test_cube"),
                20, List.of(), List.of());
        FactoryRecipeThread lane = FactoryRecipeThread.core(controller, "source", Set.of(oldRecipe));
        assertThat(lane.searchAndStartRecipe(List.of(oldRecipe, allowed), 1L, 0L)).isEqualTo(STARTED);
        lane.runtime().tick();
        lane.runtime().finish();
        lane.onRecipeFinished();
        lane.replaceRecipeSet(Set.of(allowed));
        var current = controller.currentRuntimeSnapshot();

        assertThat(lane.tryRestartLastRecipe(List.of(oldRecipe, allowed), 1L, current.structure().version(),
                current.capabilityVersion(), current.modifierVersion(), current.stateVersion())).isEqualTo(NO_MATCH);
        assertThat(lane.runtime().active()).isFalse();
        assertThat(lane.hasFullSearchPending()).isTrue();
        assertThat(lane.searchFailureKey()).isNull();
        assertThat(lane.searchAndStartRecipe(List.of(oldRecipe, allowed), 1L, current.structure().version())).isEqualTo(STARTED);
        assertThat(lane.runtime().recipe()).isSameAs(allowed);
    }

    @Test
    void core_set_change_before_pending_commit_preserves_the_unpaid_inputs() throws Exception {
        TestBootstrap.bootstrapCapabilities();
        var input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        RuntimeTestFixtures.formStructureWithComponents(controller, controller.structureSnapshot().configuredMachine(), input);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                new MachineComponent(input.kind(), input.ioType()), input, input.getBlockPos(), input.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_core_pending_scope"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                        new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        installInlineCoordinator(level);
        try {
            input.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
            FactoryRecipeThread lane = FactoryRecipeThread.core(controller, "pending", Set.of(recipe));
            assertThat(lane.searchAndStartRecipe(List.of(recipe), 1L,
                    controller.currentRuntimeSnapshot().structure().version())).isEqualTo(PENDING);
            assertThat(lane.isStartPending()).isTrue();
            lane.replaceRecipeSet(Set.of());
            SharedIoCoordinator shared = SharedIoCoordinator.get(level);
            shared.beginLevelTick(level.getGameTime());
            MachineAsyncCoordinator.get(level).completeUntilIdleForTesting(() -> shared.resolve(level));

            assertThat(lane.isStartPending()).isFalse();
            assertThat(lane.runtime().active()).isFalse();
            assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
            assertThat(lane.searchFailureKey()).isNull();
        } finally {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            MachineAsyncCoordinator.discard(level);
            SharedIoCoordinator.discard(level);
            StructureClaimRegistry.discard(level);
            RecipeRegistry.clearForTesting();
        }
    }

    @Test
    void failed_cached_eligibility_requires_a_full_search_before_retrying_the_same_source() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("factory_full_search_deferred"), MMCR.id("test_cube"),
                1, List.of(), List.of());
        assertThat(lane.searchAndStartRecipe(List.of(recipe), 1L, 0L)).isEqualTo(STARTED);
        lane.runtime().tick();
        lane.runtime().finish();
        lane.onRecipeFinished();
        lane.setStartEligibility(candidate -> false);
        var current = controller.currentRuntimeSnapshot();
        assertThat(lane.tryRestartLastRecipe(List.of(recipe), 1L, current.structure().version(),
                current.capabilityVersion(), current.modifierVersion(), current.stateVersion())).isEqualTo(NO_MATCH);
        assertThat(lane.hasFullSearchPending()).isTrue();
        lane.onFullSearchResult(DEFERRED);
        assertThat(lane.hasFullSearchPending()).isTrue();
        lane.setStartEligibility(candidate -> true);

        assertThat(lane.tryRestartLastRecipe(List.of(recipe), 1L, current.structure().version(),
                current.capabilityVersion(), current.modifierVersion(), current.stateVersion())).isEqualTo(NO_MATCH);
        assertThat(lane.runtime().active()).isFalse();
        assertThat(lane.searchAndStartRecipe(List.of(recipe), 1L, current.structure().version())).isEqualTo(STARTED);
        assertThat(lane.hasFullSearchPending()).isFalse();
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }

    @SuppressWarnings("unchecked")
    private static void installInlineCoordinator(ServerLevel level) throws ReflectiveOperationException {
        var field = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        field.setAccessible(true);
        ((Map<ServerLevel, MachineAsyncCoordinator>) field.get(null)).put(level,
                MachineAsyncCoordinator.forTesting(Runnable::run));
    }
}
