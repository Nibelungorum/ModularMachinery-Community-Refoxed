package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryRecipeSchedulerTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void starts_at_most_thread_limit_lanes_and_keeps_pending_lanes_until_capacity_opens() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2);
        FakeLane first = new FakeLane(3);
        FakeLane second = new FakeLane(3);
        FakeLane third = new FakeLane(3);

        assertThat(scheduler.laneCapacity()).isEqualTo(2);
        assertThat(scheduler.hasCapacity()).isTrue();
        assertThat(scheduler.startLane(first)).isTrue();
        assertThat(scheduler.laneCapacity()).isEqualTo(1);
        assertThat(scheduler.startLane(second)).isTrue();

        assertThat(scheduler.hasCapacity()).isFalse();
        assertThat(scheduler.startLane(third)).isFalse();

        assertThat(first.starts).isEqualTo(1);
        assertThat(second.starts).isEqualTo(1);
        assertThat(third.starts).isZero();
        assertThat(scheduler.activeLaneCount()).isEqualTo(2);
    }

    @Test
    void ticks_every_active_lane_once_and_removes_finished_lanes() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(3);
        FakeLane first = new FakeLane(1);
        FakeLane second = new FakeLane(2);
        scheduler.startLane(first);
        scheduler.startLane(second);

        scheduler.tick();

        assertThat(first.ticks).isEqualTo(1);
        assertThat(second.ticks).isEqualTo(1);
        assertThat(scheduler.activeLaneCount()).isEqualTo(1);
        assertThat(scheduler.hasCapacity()).isTrue();
        assertThat(scheduler.laneCapacity()).isEqualTo(2);

        scheduler.tick();

        assertThat(second.ticks).isEqualTo(2);
        assertThat(scheduler.activeLaneCount()).isZero();
    }

    @Test
    void stop_all_stops_and_clears_all_active_lanes() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2);
        FakeLane first = new FakeLane(3);
        FakeLane second = new FakeLane(3);
        scheduler.startLane(first);
        scheduler.startLane(second);

        scheduler.stopAll();

        assertThat(first.stops).isEqualTo(1);
        assertThat(second.stops).isEqualTo(1);
        assertThat(scheduler.activeLaneCount()).isZero();
        assertThat(scheduler.hasCapacity()).isTrue();
        assertThat(scheduler.laneCapacity()).isEqualTo(2);
        scheduler.stopAll();
        assertThat(first.stops).isEqualTo(1);
        assertThat(second.stops).isEqualTo(1);
    }

    @Test
    void snapshot_contains_only_recipe_id_parallelism_structure_version_and_component_descriptors() throws Exception {
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, null, BlockPos.ZERO, new BlockPos(1, 2, 3), List.of("input", "hot"), null));

        FactoryRecipeScheduler.RecipeSnapshot snapshot = FactoryRecipeScheduler.captureSnapshot(
                MMCR.id("factory_recipe"),
                4,
                42L,
                components);

        assertThat(snapshot.recipeId()).isEqualTo(MMCR.id("factory_recipe"));
        assertThat(snapshot.parallelism()).isEqualTo(4);
        assertThat(snapshot.structureVersion()).isEqualTo(42L);
        assertThat(snapshot.components()).containsExactly(new FactoryRecipeScheduler.ComponentSnapshot(
                "unknown",
                new BlockPos(1, 2, 3),
                List.of("input", "hot")));
        assertThat(snapshot.components().getFirst().tags()).isUnmodifiable();
        assertThat(snapshot.components()).isUnmodifiable();

        for (Field field : snapshot.getClass().getDeclaredFields()) {
            assertThat(net.minecraft.world.level.Level.class.isAssignableFrom(field.getType())).isFalse();
            assertThat(BlockEntity.class.isAssignableFrom(field.getType())).isFalse();
        }
        for (Field field : snapshot.components().getFirst().getClass().getDeclaredFields()) {
            assertThat(BlockEntity.class.isAssignableFrom(field.getType())).isFalse();
        }
    }

    @Test
    void per_thread_parallelism_is_not_reduced_by_other_active_threads() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        first.setActiveRecipeForTesting(activeRecipeWithParallelism(16));
        second.setActiveRecipeForTesting(activeRecipeWithParallelism(16));
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        scheduler.tickThreads(null, List.of(), 1L, 16, new RecipeCraftingContextPool());

        assertThat(scheduler.perThreadParallelLimit()).isEqualTo(16);
        assertThat(scheduler.availableParallelism()).isEqualTo(16);
        assertThat(scheduler.usedParallelism()).isEqualTo(32);
    }

    @Test
    void available_candidates_skip_recipes_that_reached_thread_limit() {
        MachineRecipe limited = recipe("factory_limited", 1);
        MachineRecipe unlimited = recipe("factory_unlimited", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread activeLimited = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        activeLimited.setActiveRecipeForTesting(new ActiveMachineRecipe(limited, 1));
        FactoryRecipeThread activeUnlimited = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        activeUnlimited.setActiveRecipeForTesting(new ActiveMachineRecipe(unlimited, 1));
        scheduler.addThreadForTesting(activeLimited);
        scheduler.addThreadForTesting(activeUnlimited);

        assertThat(scheduler.availableCandidates(List.of(limited, unlimited))).containsExactly(unlimited);
    }

    @Test
    void scheduler_ticks_recipe_threads_and_removes_timed_out_dynamic_threads() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        scheduler.addThreadForTesting(thread);

        for (int i = 0; i < FactoryRecipeThread.IDLE_TIMEOUT_TICKS; i++) {
            scheduler.tickThreads(null, List.of(), 1L, 4, new RecipeCraftingContextPool());
        }

        assertThat(scheduler.activeThreadCount()).isZero();
    }

    @Test
    void scheduler_always_exposes_non_expiring_base_thread_at_index_zero() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1);

        for (int i = 0; i <= FactoryRecipeThread.IDLE_TIMEOUT_TICKS; i++) {
            scheduler.tickThreads(null, List.of(), 1L, 1, new RecipeCraftingContextPool());
        }

        assertThat(scheduler.threadSnapshots()).containsExactly(FactoryRecipeScheduler.ThreadSnapshot.idleBase());
    }

    @Test
    void thread_snapshots_include_idle_placeholders_up_to_thread_limit() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);

        assertThat(scheduler.threadSnapshots())
                .extracting(FactoryRecipeScheduler.ThreadSnapshot::index)
                .containsExactly(0, 1, 2, 3);
        assertThat(scheduler.threadSnapshots()).allSatisfy(snapshot -> assertThat(snapshot.active()).isFalse());
    }

    @Test
    void active_thread_count_excludes_idle_cached_threads() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2);
        scheduler.addThreadForTesting(FactoryRecipeThread.simple(null, new RecipeCraftingContextPool()));

        assertThat(scheduler.activeThreadCount()).isZero();
    }

    @Test
    void parallel_limit_does_not_cap_thread_count() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        first.setActiveRecipeForTesting(activeRecipeWithParallelism(2));
        second.setActiveRecipeForTesting(activeRecipeWithParallelism(1));
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        scheduler.tickThreads(null, List.of(), 1L, 2, new RecipeCraftingContextPool());

        assertThat(scheduler.threadLimit()).isEqualTo(4);
    }

    @Test
    void set_thread_limit_stops_threads_above_new_capacity() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(3);
        FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        first.setActiveRecipeForTesting(activeRecipeWithParallelism(1));
        second.setActiveRecipeForTesting(activeRecipeWithParallelism(1));
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        scheduler.setThreadLimit(1);

        assertThat(scheduler.threadLimit()).isEqualTo(1);
        assertThat(scheduler.activeThreadCount()).isZero();
    }

    @Test
    void cached_recipe_restarts_before_normal_candidate_ordering() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_cache_machine"));
        MachineRecipe cached = recipe("factory_cached", 0);
        MachineRecipe fallback = recipe("factory_fallback", 0);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.rememberLastRecipe(cached, controller.getStructureVersion(), controller.getModifierSnapshotVersion());

        assertThat(thread.tryRestartLastRecipe(List.of(fallback, cached), 1,
                controller.getStructureVersion(), controller.getModifierSnapshotVersion())).isTrue();

        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(cached);
    }

    @Test
    void invalid_cached_recipe_allows_normal_candidate_search() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_stale_cache_machine"));
        MachineRecipe stale = recipe("factory_stale", 0);
        MachineRecipe fallback = recipe("factory_cache_fallback", 0);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.rememberLastRecipe(stale, controller.getStructureVersion() + 1, controller.getModifierSnapshotVersion());

        assertThat(thread.tryRestartLastRecipe(List.of(fallback), 1,
                controller.getStructureVersion(), controller.getModifierSnapshotVersion())).isFalse();
        assertThat(thread.searchAndStartRecipe(List.of(fallback), 1, controller.getStructureVersion())).isTrue();

        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(fallback);
    }

    @Test
    void thread_zero_starts_recipe_without_a_thread_disperser() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_base_thread_machine"));
        MachineRecipe recipe = recipe("factory_base_thread_recipe", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, new RecipeCraftingContextPool());

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, new RecipeCraftingContextPool());

        assertThat(scheduler.threadSnapshots()).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.index()).isZero();
                    assertThat(snapshot.active()).isTrue();
                    assertThat(snapshot.recipeId()).isEqualTo(recipe.id().toString());
                });
    }

    @Test
    void allocated_idle_workers_are_reused_before_new_workers_are_created() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_reuse_machine"));
        MachineRecipe first = recipe("factory_reuse_first", 0);
        MachineRecipe second = recipe("factory_reuse_second", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2, new RecipeCraftingContextPool());
        scheduler.addThreadForTesting(FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool()));

        scheduler.tickThreads(controller, List.of(first, second), controller.getStructureVersion(), 2, new RecipeCraftingContextPool());

        assertThat(scheduler.threadSnapshots()).extracting(FactoryRecipeScheduler.ThreadSnapshot::index)
                .containsExactly(0, 1);
        assertThat(scheduler.threadSnapshots()).allSatisfy(snapshot -> assertThat(snapshot.active()).isTrue());
    }

    @Test
    void factory_threads_each_receive_full_parallelism_limit() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_multi_thread_machine"));
        MachineRecipe recipe = parallelizedRecipe("factory_multi_thread_recipe", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4, new RecipeCraftingContextPool());

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 16, new RecipeCraftingContextPool());

        assertThat(scheduler.threadSnapshots()).filteredOn(FactoryRecipeScheduler.ThreadSnapshot::active)
                .hasSize(4)
                .allSatisfy(snapshot -> assertThat(snapshot.parallelism()).isEqualTo(16));
    }

    @Test
    void factory_threads_prioritize_private_cache_and_invalidate_on_structure_change() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_cache_invalidation_machine"));
        MachineRecipe cached = recipe("factory_cache_invalidation_cached", 0);
        MachineRecipe fallback = new MachineRecipe(MMCR.id("factory_cache_invalidation_fallback"),
                MMCR.id("factory_machine"), 20, List.of(), List.of(), List.of(), -1, 0);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, pool);

        scheduler.tickThreads(controller, List.of(cached), controller.getStructureVersion(), 1, pool);
        FactoryRecipeThread thread = scheduler.allThreads().getFirst();
        thread.invalidate();

        scheduler.tickThreads(controller, List.of(fallback, cached), controller.getStructureVersion(), 1, pool);

        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(cached);
        thread.invalidate();
        scheduler.tickThreads(controller, List.of(fallback, cached), controller.getStructureVersion() + 1, 1, pool);

        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(fallback);
    }

    @Test
    void core_thread_searches_only_its_declared_recipes() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_core_filter_machine"));
        MachineRecipe allowed = recipe("factory_core_allowed", 0);
        MachineRecipe denied = recipe("factory_core_denied", 0);
        FactoryRecipeThread thread = FactoryRecipeThread.core(controller, new RecipeCraftingContextPool(),
                "allowed_only", Set.of(allowed));

        assertThat(thread.candidatesFor(List.of(denied, allowed))).containsExactly(allowed);
        assertThat(thread.searchAndStartRecipe(List.of(denied, allowed), 1, controller.getStructureVersion())).isTrue();
        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(allowed);
    }

    @Test
    void core_thread_reconciliation_retains_named_threads_and_updates_their_recipe_sets() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_core_sync_machine"));
        MachineRecipe first = recipe("factory_core_sync_first", 0);
        MachineRecipe second = recipe("factory_core_sync_second", 0);
        var machineId = MMCR.id("factory_core_sync_machine");
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Core Sync",
                new BlockArray(java.util.Map.of()), MachineControllerSpec.defaultsFor(machineId),
                MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), java.util.Map.of(),
                1, false, true, 3, List.of(new FactoryThreadSpec("core", List.of(first.id()))));
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(3, new RecipeCraftingContextPool());

        scheduler.syncCoreThreads(controller, machine, List.of(first, second), new RecipeCraftingContextPool());
        FactoryRecipeThread original = scheduler.allThreads().stream()
                .filter(FactoryRecipeThread::isCoreThread)
                .findFirst().orElseThrow();

        DynamicMachine updatedMachine = new DynamicMachine(machineId, "Factory Core Sync",
                new BlockArray(java.util.Map.of()), MachineControllerSpec.defaultsFor(machineId),
                MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), java.util.Map.of(),
                1, false, true, 3, List.of(new FactoryThreadSpec("core", List.of(second.id()))));
        scheduler.syncCoreThreads(controller, updatedMachine, List.of(first, second), new RecipeCraftingContextPool());

        assertThat(scheduler.allThreads()).contains(original);
        assertThat(original.recipeSet()).containsExactly(second);
        assertThat(scheduler.allThreads()).filteredOn(FactoryRecipeThread::isCoreThread).hasSize(1);
    }

    @Test
    void paused_factory_threads_round_trip_their_recipe_progress_context_and_failure_state() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("paused_factory_machine"));
        MachineRecipe firstRecipe = recipe("paused_factory_first", 0);
        MachineRecipe secondRecipe = recipe("paused_factory_second", 0);
        cn.howxu.mmcr.api.recipe.RecipeRegistry.register(firstRecipe);
        cn.howxu.mmcr.api.recipe.RecipeRegistry.register(secondRecipe);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(3, pool);
        FactoryRecipeThread first = FactoryRecipeThread.simple(controller, pool);
        FactoryRecipeThread second = FactoryRecipeThread.simple(controller, pool);
        ActiveMachineRecipe firstActive = new ActiveMachineRecipe(firstRecipe, 1);
        ActiveMachineRecipe secondActive = new ActiveMachineRecipe(secondRecipe, 1);
        firstActive.setTick(3);
        secondActive.setTick(7);
        first.setActiveRecipeForTesting(firstActive);
        second.setActiveRecipeForTesting(secondActive);
        first.bindController(controller);
        second.bindController(controller);
        setField(RecipeThread.class, first, "lastFailureUnloc", "mmcr.failure.first");
        setField(RecipeThread.class, second, "lastFailureUnloc", "mmcr.failure.second");
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        scheduler.save(output);
        FactoryRecipeScheduler loaded = new FactoryRecipeScheduler(3, pool);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()), output.buildResult()), controller, pool);

        assertThat(loaded.allThreads()).hasSize(3);
        List<FactoryRecipeThread> restored = loaded.allThreads().subList(1, 3);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getRecipe().id())
                .containsExactly(firstRecipe.id(), secondRecipe.id());
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(3, 7);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTotalTick()).containsExactly(20, 20);
        assertThat(restored).extracting(thread -> fieldValue(RecipeThread.class, thread, "context")).doesNotContainNull();
        assertThat(restored).extracting(FactoryRecipeThread::getLastFailureUnloc)
                .containsExactly("mmcr.failure.first", "mmcr.failure.second");

        setField(MachineControllerBlockEntity.class, controller, "redstonePaused", true);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(3, 7);
        loaded.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(3, 7);
        setField(MachineControllerBlockEntity.class, controller, "redstonePaused", false);
        loaded.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(4, 8);
    }

    private static ActiveMachineRecipe activeRecipeWithParallelism(int parallelism) {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_parallel_" + parallelism), MMCR.id("factory_machine"), 20, List.of(), List.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, parallelism);
        active.setParallelism(parallelism);
        return active;
    }

    private static MachineRecipe recipe(String id, int maxThreads) {
        return new MachineRecipe(MMCR.id(id), MMCR.id("factory_machine"), 20, List.of(), List.of(), List.of(), 0, maxThreads);
    }

    private static MachineRecipe parallelizedRecipe(String id, int maxThreads) {
        return new MachineRecipe(MMCR.id(id), MMCR.id("factory_machine"), 20, List.of(), List.of(), List.of(), 0, maxThreads,
                false, List.of(), List.of(), true);
    }

    private static MachineControllerBlockEntity controller(net.minecraft.resources.Identifier machineId) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(MachineControllerBlockEntity.class);
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Cache", new BlockArray(java.util.Map.of()));
        Field foundMachine = MachineControllerBlockEntity.class.getDeclaredField("foundMachine");
        foundMachine.setAccessible(true);
        foundMachine.set(controller, machine);
        return controller;
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

    private static final class FakeLane implements FactoryRecipeScheduler.Lane {
        private final int finishAfterTicks;
        private int starts;
        private int ticks;
        private int stops;

        private FakeLane(int finishAfterTicks) {
            this.finishAfterTicks = finishAfterTicks;
        }

        @Override
        public void start() {
            starts++;
        }

        @Override
        public boolean tick() {
            ticks++;
            return ticks >= finishAfterTicks;
        }

        @Override
        public void stop() {
            stops++;
        }
    }
}
