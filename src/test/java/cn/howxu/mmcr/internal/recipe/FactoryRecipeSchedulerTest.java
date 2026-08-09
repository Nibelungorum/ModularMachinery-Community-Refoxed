package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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
    void available_parallelism_subtracts_active_thread_budget() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        first.setActiveRecipeForTesting(activeRecipeWithParallelism(3));
        second.setActiveRecipeForTesting(activeRecipeWithParallelism(1));
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        assertThat(scheduler.availableParallelism()).isZero();
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

    private static ActiveMachineRecipe activeRecipeWithParallelism(int parallelism) {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_parallel_" + parallelism), MMCR.id("factory_machine"), 20, List.of(), List.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, parallelism);
        active.setParallelism(parallelism);
        return active;
    }

    private static MachineRecipe recipe(String id, int maxThreads) {
        return new MachineRecipe(MMCR.id(id), MMCR.id("factory_machine"), 20, List.of(), List.of(), List.of(), 0, maxThreads);
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
