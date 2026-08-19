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
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.LinkedAppearanceBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
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
            assertThat(Level.class.isAssignableFrom(field.getType())).isFalse();
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
    void locked_dynamic_threads_are_not_removed_by_idle_timeout() {
        MachineRecipe firstRecipe = recipe("factory_locked_idle_first", 0);
        MachineRecipe secondRecipe = recipe("factory_locked_idle_second", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-1");
        FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-2");
        first.setLockedRecipeId(firstRecipe.id());
        second.setLockedRecipeId(secondRecipe.id());
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        for (int i = 0; i < FactoryRecipeThread.IDLE_TIMEOUT_TICKS; i++) {
            scheduler.tickThreads(null, List.of(), 1L, 4, new RecipeCraftingContextPool());
        }

        assertThat(scheduler.threadSnapshots())
                .extracting(FactoryRecipeScheduler.ThreadSnapshot::lockedRecipeId)
                .contains("mmcr:factory_locked_idle_first", "mmcr:factory_locked_idle_second");
    }

    @Test
    void unlocked_dynamic_threads_keep_their_thread_slots_after_idle_timeout() {
        MachineRecipe firstRecipe = recipe("factory_unlocked_idle_first", 0);
        MachineRecipe secondRecipe = recipe("factory_unlocked_idle_second", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
        FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-1");
        FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-2");
        first.setLockedRecipeId(firstRecipe.id());
        second.setLockedRecipeId(secondRecipe.id());
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);

        assertThat(scheduler.toggleRecipeLock(1)).isTrue();
        for (int i = 0; i < FactoryRecipeThread.IDLE_TIMEOUT_TICKS; i++) {
            scheduler.tickThreads(null, List.of(), 1L, 4, new RecipeCraftingContextPool());
        }

        assertThat(scheduler.allThreads())
                .extracting(FactoryRecipeThread::threadName)
                .containsExactly("", "factory-1", "factory-2");
        assertThat(scheduler.threadSnapshots())
                .extracting(FactoryRecipeScheduler.ThreadSnapshot::index)
                .containsExactly(0, 1, 2, 3);
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
    void lockedThreadCannotStartAnUnlockedRecipe() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_locked_candidate_machine"));
        MachineRecipe locked = recipe("factory_locked_z", 0);
        MachineRecipe unlocked = recipe("factory_locked_a", 0);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.setLockedRecipeId(locked.id());

        assertThat(thread.searchAndStartRecipe(List.of(locked, unlocked), 1, controller.getStructureVersion())).isTrue();

        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(locked);
    }

    @Test
    void missingInputKeepsLockAndReturnsFailure() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_locked_missing_input_machine"));
        MachineRecipe locked = new MachineRecipe(MMCR.id("factory_locked_missing_input_z"), MMCR.id("factory_machine"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        MachineRecipe unlocked = recipe("factory_locked_missing_input_a", 0);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.setLockedRecipeId(locked.id());

        assertThat(thread.searchAndStartRecipe(List.of(locked, unlocked), 1, controller.getStructureVersion())).isFalse();

        assertThat(thread.lockedRecipeId()).isEqualTo(locked.id());
        assertThat(thread.getActiveRecipe()).isNull();
    }

    @Test
    void activeLockDisablesCachedRecipeRestart() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_locked_cache_machine"));
        MachineRecipe locked = recipe("factory_locked_cache_target", 0);
        MachineRecipe cached = recipe("factory_locked_cache_other", 0);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.rememberLastRecipe(cached, controller.getStructureVersion(), controller.getModifierSnapshotVersion());
        thread.setLockedRecipeId(locked.id());

        assertThat(thread.tryRestartLastRecipe(List.of(cached, locked), 1,
                controller.getStructureVersion(), controller.getModifierSnapshotVersion())).isFalse();
    }

    @Test
    void threadSnapshotsExposeLockStateAndKeepIdlePlaceholdersUnlocked() {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2, new RecipeCraftingContextPool());
        FactoryRecipeThread baseThread = scheduler.allThreads().getFirst();
        baseThread.setLockedRecipeId(MMCR.id("snapshot_locked_recipe"));

        assertThat(scheduler.threadSnapshots()).satisfiesExactly(
                snapshot -> {
                    assertThat(snapshot.locked()).isTrue();
                    assertThat(snapshot.lockedRecipeId()).isEqualTo("mmcr:snapshot_locked_recipe");
                },
                snapshot -> {
                    assertThat(snapshot.locked()).isFalse();
                    assertThat(snapshot.lockedRecipeId()).isEmpty();
                });
    }

    @Test
    void failed_shared_restart_forgets_cached_recipe_and_allows_fallback_search() throws Exception {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_cached_shared_failure"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe cached = new MachineRecipe(MMCR.id("factory_cached_shared_sword"), MMCR.id("factory_cached_shared_failure"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(enchantedInput(2)), true);
        MachineRecipe fallback = new MachineRecipe(MMCR.id("factory_cached_shared_iron"), MMCR.id("factory_cached_shared_failure"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, pool);
        FactoryRecipeThread baseThread = scheduler.allThreads().getFirst();
        baseThread.rememberLastRecipe(cached, controller.getStructureVersion(), controller.getModifierSnapshotVersion());
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        scheduler.tickThreads(controller, List.of(cached, fallback), controller.getStructureVersion(), 1, pool);
        SharedIoCoordinator.get(level).resolve(domain);
        scheduler.tickThreads(controller, List.of(cached, fallback), controller.getStructureVersion(), 1, pool);
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(baseThread.getActiveRecipe()).isNotNull();
        assertThat(baseThread.getActiveRecipe().getRecipe()).isSameAs(fallback);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
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
    void factory_threads_notify_once_when_recipe_finishes() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_thread_finish_machine"));
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_thread_finish_recipe"),
                MMCR.id("factory_thread_finish_machine"), 1, List.of(), List.of());
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, pool);
        AtomicInteger finished = new AtomicInteger();

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool, finished::incrementAndGet);
        scheduler.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool, finished::incrementAndGet);
        scheduler.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool, finished::incrementAndGet);

        assertThat(finished).hasValue(1);
    }

    @Test
    void synchronous_completion_restarts_only_the_locked_single_worker_recipe() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_sync_continuation_machine"));
        MachineRecipe lockedRecipe = new MachineRecipe(MMCR.id("factory_sync_continuation_locked"),
                MMCR.id("factory_sync_continuation_machine"), 2, List.of(), List.of(), List.of(), 0, 1);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2, pool);
        AtomicInteger finished = new AtomicInteger();

        scheduler.tickThreads(controller, List.of(lockedRecipe), controller.getStructureVersion(), 1, pool,
                finished::incrementAndGet);
        FactoryRecipeThread thread = scheduler.allThreads().getFirst();
        thread.setLockedRecipeId(lockedRecipe.id());
        thread.getActiveRecipe().setTotalTick(1);
        scheduler.tickThreads(controller, List.of(lockedRecipe), controller.getStructureVersion(), 1, pool,
                finished::incrementAndGet);

        assertThat(finished).hasValue(1);
        assertThat(thread.getActiveRecipe()).isNotNull();
        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(lockedRecipe);
        assertThat(thread.getActiveRecipe().getTick()).isZero();
        assertThat(scheduler.allThreads()).hasSize(1);

        scheduler.tickThreads(controller, List.of(lockedRecipe), controller.getStructureVersion(), 1, pool,
                finished::incrementAndGet);

        assertThat(thread.getActiveRecipe().getTick()).isEqualTo(1);
    }

    @Test
    void shared_factory_threads_restart_when_their_recipe_finishes() throws Exception {
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_shared_continuation"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_shared_continuation_recipe"),
                MMCR.id("factory_shared_continuation"), 1, List.of(), List.of(), List.of(), 0, 1);
        MachineRecipe unlockedRecipe = new MachineRecipe(MMCR.id("factory_shared_continuation_unlocked_recipe"),
                MMCR.id("factory_shared_continuation"), 1, List.of(), List.of());
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, pool);
        AtomicInteger finished = new AtomicInteger();

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool, finished::incrementAndGet);
        SharedIoCoordinator.get(level).resolve(domain);
        FactoryRecipeThread thread = scheduler.allThreads().getFirst();
        thread.setLockedRecipeId(recipe.id());
        scheduler.tickThreads(controller, List.of(unlockedRecipe, recipe), controller.getStructureVersion(), 1, pool,
                finished::incrementAndGet);
        SharedIoCoordinator.get(level).resolve(domain);
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(finished).hasValue(1);
        assertThat(thread.getActiveRecipe()).isNotNull();
        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(recipe);
        assertThat(thread.getActiveRecipe().getTick()).isZero();
        assertThat(scheduler.allThreads()).hasSize(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void finish_continuation_runs_after_recipe_thread_state_is_cleared() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_finish_continuation_machine"));
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_finish_continuation_recipe"),
                MMCR.id("factory_finish_continuation_machine"), 1, List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        AtomicReference<RecipeThread.Status> statusAtCompletion = new AtomicReference<>();
        AtomicReference<ActiveMachineRecipe> activeAtCompletion = new AtomicReference<>();
        thread.setFinishContinuation(() -> {
            statusAtCompletion.set(thread.getStatus());
            activeAtCompletion.set(thread.getActiveRecipe());
        });

        thread.searchAndStartRecipe(List.of(recipe), 1, controller.getStructureVersion());
        thread.tick();

        assertThat(statusAtCompletion).hasValue(RecipeThread.Status.IDLE);
        assertThat(activeAtCompletion).hasValue(null);
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
    void factoryLaneIdsAreStableAndDistinct() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_lane_id_machine"));
        MachineRecipe recipe = parallelizedRecipe("factory_lane_id_recipe", 0);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(3, new RecipeCraftingContextPool());

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, new RecipeCraftingContextPool());

        assertThat(scheduler.allThreads()).extracting(FactoryRecipeThread::laneId)
                .containsExactly("base", "factory-0", "factory-1");
    }

    @Test
    void loadedFactoryLanesAdvanceTheGeneratedLaneId() throws Exception {
        FactoryRecipeScheduler saved = new FactoryRecipeScheduler(4, new RecipeCraftingContextPool());
        saved.addThreadForTesting(FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-4"));
        saved.addThreadForTesting(FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-9"));
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        saved.save(output);
        MachineControllerBlockEntity controller = controller(MMCR.id("factory_loaded_lanes"));
        FactoryRecipeScheduler loaded = new FactoryRecipeScheduler(4, new RecipeCraftingContextPool());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()), controller,
                new RecipeCraftingContextPool());

        loaded.tickThreads(controller, List.of(parallelizedRecipe("factory_loaded_lane_recipe", 0)),
                controller.getStructureVersion(), 1, new RecipeCraftingContextPool());

        assertThat(loaded.allThreads()).extracting(FactoryRecipeThread::laneId)
                .containsExactly("base", "factory-4", "factory-9", "factory-10");
    }

    @Test
    void sharedFactoryStartsUseCoordinatorCallbacksAndInstallPartialParallelism() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_shared_start"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_shared_start_recipe"), MMCR.id("factory_shared_start"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2, new RecipeCraftingContextPool());

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 8, new RecipeCraftingContextPool());
        assertThat(scheduler.allThreads()).hasSize(2);
        assertThat(scheduler.allThreads()).allSatisfy(thread -> assertThat(thread.isStartPending()).isTrue());
        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 8, new RecipeCraftingContextPool());
        assertThat(scheduler.allThreads()).allSatisfy(thread -> assertThat(thread.isStartPending()).isTrue());

        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(scheduler.allThreads()).allSatisfy(thread -> assertThat(thread.getActiveRecipe()).isNotNull());
        assertThat(scheduler.usedParallelism()).isEqualTo(10);
        assertThat(scheduler.allThreads()).extracting(FactoryRecipeThread::usedParallelism).containsExactly(8, 2);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void sharedBaseThreadStartsNoOutputRecipeWithEnchantedInput() throws Exception {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, enchantedSword("minecraft:sharpness", 2));
        BlockPos firstControllerPos = new BlockPos(0, 64, 0);
        BlockPos secondControllerPos = new BlockPos(2, 64, 0);
        MachineControllerBlockEntity firstController = controllerWithInput(MMCR.id("factory_shared_enchanted"), firstControllerPos, input);
        MachineControllerBlockEntity secondController = controllerWithInput(MMCR.id("factory_shared_enchanted"), secondControllerPos, input);
        ServerLevel level = serverLevel(List.of(firstController, secondController, input));
        setField(BlockEntity.class, firstController, "level", level);
        setField(BlockEntity.class, secondController, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        List<StructureClaimRegistry.Claim> sharedInput = List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED));
        registry.claim(firstControllerPos, sharedInput);
        registry.claim(secondControllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(firstControllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_shared_enchanted_recipe"), MMCR.id("factory_shared_enchanted"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(enchantedInput(2)), true);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, new RecipeCraftingContextPool());

        scheduler.tickThreads(firstController, List.of(recipe), firstController.getStructureVersion(), 1, new RecipeCraftingContextPool());
        SharedIoCoordinator.get(level).resolve(domain);

        FactoryRecipeScheduler.ThreadSnapshot snapshot = scheduler.threadSnapshots().getFirst();
        assertThat(snapshot.index()).isZero();
        assertThat(snapshot.active()).isTrue();
        assertThat(snapshot.recipeId()).isEqualTo(recipe.id().toString());
        for (int tick = 0; tick < recipe.getRecipeTotalTickTime(); tick++) {
            scheduler.tickThreads(firstController, List.of(), firstController.getStructureVersion(), 1, new RecipeCraftingContextPool());
            SharedIoCoordinator.get(level).resolve(domain);
        }

        assertThat(scheduler.threadSnapshots().getFirst().active()).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).is(Items.DIAMOND_SWORD)).isTrue();
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void rejectedSharedStartsExposeMissingInputOnEveryThread() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_shared_failure"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_shared_failure_recipe"), MMCR.id("factory_shared_failure"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2, new RecipeCraftingContextPool());

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, new RecipeCraftingContextPool());
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(scheduler.threadSnapshots()).allSatisfy(snapshot -> {
            assertThat(snapshot.active()).isFalse();
            assertThat(snapshot.lastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        });
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void removingNonConsumableInputCancelsEveryActiveThread() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_non_consumable"),
                new BlockPos(0, 64, 0), input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_non_consumable_recipe"), MMCR.id("factory_non_consumable"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F)
        ), true);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2, pool);

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool);
        assertThat(scheduler.threadSnapshots()).allSatisfy(snapshot -> assertThat(snapshot.active()).isTrue());

        input.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        scheduler.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool);

        assertThat(scheduler.allThreads()).allSatisfy(thread -> {
            assertThat(thread.isIdle()).isTrue();
            assertThat(thread.getStatus()).isEqualTo(RecipeThread.Status.FAILED);
            assertThat(thread.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        });
    }

    @Test
    void invalidatedSharedStartCannotCommitOrReplaceANewerPendingStart() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_stale_start"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe oldRecipe = new MachineRecipe(MMCR.id("factory_stale_start_old"), MMCR.id("factory_stale_start"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        MachineRecipe newRecipe = new MachineRecipe(MMCR.id("factory_stale_start_new"), MMCR.id("factory_stale_start"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(), true);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, pool);

        assertThat(thread.searchAndStartRecipe(List.of(oldRecipe), 1, controller.getStructureVersion())).isTrue();
        thread.invalidate();
        assertThat(thread.searchAndStartRecipe(List.of(newRecipe), 1, controller.getStructureVersion())).isTrue();

        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.getActiveRecipe()).isNotNull();
        assertThat(thread.getActiveRecipe().getRecipe()).isSameAs(newRecipe);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void sharedDomainRebuildClearsPendingStartSoTheLaneCanRetry() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_domain_rebuild"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        List<StructureClaimRegistry.Claim> claims = List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED));
        registry.claim(controllerPos, claims);
        StructureClaimRegistry.ResourceDomain originalDomain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_domain_rebuild_recipe"), MMCR.id("factory_domain_rebuild"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, controller.getStructureVersion())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        registry.release(controllerPos);
        registry.claim(controllerPos, claims);
        SharedIoCoordinator.get(level).resolve(originalDomain);
        thread.tick();

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.isIdle()).isTrue();
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void sharedFinalOutputRetryDoesNotRepeatItsLastTickIo() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        Items.COBBLESTONE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 64, 0));
        setField(ItemBusBlockEntity.class, input, "handler", new ItemStackHandler(6) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                if (!simulate && !extracted.isEmpty()) {
                    for (int outputSlot = 0; outputSlot < output.getItemStackHandler(null).getSlots(); outputSlot++) {
                        output.getItemStackHandler(null).setStackInSlot(outputSlot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                }
                return extracted;
            }

            @Override protected void onContentsChanged(int slot) { }
        });
        EnergyInputHatchBlockEntity energy = energyInputHatch(new BlockPos(3, 64, 0));
        energy.getMutableEnergyStorage().forceInsert(20, false);
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithComponents(MMCR.id("shared_finish_retry"), controllerPos, input, output, energy);
        ServerLevel level = serverLevel(List.of(controller, input, output, energy));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, energy, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(
                new StructureClaimRegistry.Claim(input.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(output.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(energy.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)
        ));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        setField(MachineControllerBlockEntity.class, controller, "resourceDomain", domain);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("shared_finish_retry_recipe"), MMCR.id("shared_finish_retry"),
                1, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new EnergyRequirement(10),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT))
        ));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        active.setTick(active.getTotalTick() - 1);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.setActiveRecipeForTesting(active);
        setField(RecipeThread.class, thread, "context", new RecipeCraftingContext(controller));

        thread.tick();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.getStatus()).isEqualTo(RecipeThread.Status.WAITING);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(itemCount(output, Items.IRON_INGOT)).isZero();

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        ((TestServerLevel) level).gameTime = 10L;
        thread.tick();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.isIdle()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(itemCount(output, Items.IRON_INGOT)).isEqualTo(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void restoredSharedFinishPendingRetriesOnlyOutputs() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        Items.COBBLESTONE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 64, 0));
        setField(ItemBusBlockEntity.class, input, "handler", new ItemStackHandler(6) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                if (!simulate && !extracted.isEmpty()) {
                    for (int outputSlot = 0; outputSlot < output.getItemStackHandler(null).getSlots(); outputSlot++) {
                        output.getItemStackHandler(null).setStackInSlot(outputSlot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                }
                return extracted;
            }

            @Override protected void onContentsChanged(int slot) { }
        });
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        EnergyInputHatchBlockEntity energy = energyInputHatch(new BlockPos(3, 64, 0));
        energy.getMutableEnergyStorage().forceInsert(20, false);
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithComponents(MMCR.id("restored_shared_finish"), controllerPos, input, output, energy);
        ServerLevel level = serverLevel(List.of(controller, input, output, energy));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, energy, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(
                new StructureClaimRegistry.Claim(input.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(output.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(energy.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)
        ));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        setField(MachineControllerBlockEntity.class, controller, "resourceDomain", domain);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("restored_shared_finish_recipe"), MMCR.id("restored_shared_finish"),
                1, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new EnergyRequirement(10),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT))
        ));
        RecipeRegistry.register(recipe);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        active.setTick(active.getTotalTick() - 1);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.setActiveRecipeForTesting(active);
        setField(RecipeThread.class, thread, "context", new RecipeCraftingContext(controller));

        thread.tick();
        SharedIoCoordinator.get(level).resolve(domain);
        assertThat(active.isFinishPending()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        thread.save(saved);
        FactoryRecipeThread restored = FactoryRecipeThread.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), saved.buildResult()), controller,
                new RecipeCraftingContextPool());
        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        ((TestServerLevel) level).gameTime = 10L;

        restored.tick();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(restored.isIdle()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(itemCount(output, Items.IRON_INGOT)).isEqualTo(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void sharedFinalTickPreflightsFullOutputBeforeConsumingIo() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        Items.COBBLESTONE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 64, 0));
        EnergyInputHatchBlockEntity energy = energyInputHatch(new BlockPos(3, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        energy.getMutableEnergyStorage().forceInsert(20, false);
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithComponents(MMCR.id("shared_final_output_preflight"),
                controllerPos, input, output, energy);
        ServerLevel level = serverLevel(List.of(controller, input, output, energy));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, energy, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(
                new StructureClaimRegistry.Claim(input.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(output.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(energy.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED)
        ));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        setField(MachineControllerBlockEntity.class, controller, "resourceDomain", domain);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("shared_final_output_preflight_recipe"), MMCR.id("shared_final_output_preflight"),
                1, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new EnergyRequirement(10),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT))
        ));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());
        thread.setActiveRecipeForTesting(active);
        setField(RecipeThread.class, thread, "context", new RecipeCraftingContext(controller));

        thread.tick();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.getStatus()).isEqualTo(RecipeThread.Status.WAITING);
        assertThat(thread.getActiveRecipe()).isSameAs(active);
        assertThat(active.getTick()).isZero();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(20);
        assertThat(itemCount(output, Items.IRON_INGOT)).isZero();

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        thread.tick();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.isIdle()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(itemCount(output, Items.IRON_INGOT)).isEqualTo(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void privateFactoryLaneFinalOutputRetryDoesNotRepeatItsLastTickIo() throws Exception {
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        Items.COBBLESTONE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 64, 0));
        setField(ItemBusBlockEntity.class, input, "handler", new ItemStackHandler(6) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                if (!simulate && !extracted.isEmpty()) {
                    for (int outputSlot = 0; outputSlot < output.getItemStackHandler(null).getSlots(); outputSlot++) {
                        output.getItemStackHandler(null).setStackInSlot(outputSlot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                }
                return extracted;
            }

            @Override protected void onContentsChanged(int slot) { }
        });
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        EnergyInputHatchBlockEntity energy = energyInputHatch(new BlockPos(3, 64, 0));
        energy.getMutableEnergyStorage().forceInsert(20, false);
        MachineControllerBlockEntity controller = controllerWithComponents(MMCR.id("private_factory_finish_retry"),
                new BlockPos(0, 64, 0), input, output, energy);
        ServerLevel level = serverLevel(List.of(controller, input, output, energy));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, energy, "level", level);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("private_factory_finish_retry_recipe"),
                MMCR.id("private_factory_finish_retry"), 1, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new EnergyRequirement(10),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT))
        ));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        FactoryRecipeLane lane = new FactoryRecipeLane(active, new RecipeCraftingContext(controller), ignored -> { });

        assertThat(lane.tick(0)).isFalse();

        assertThat(active.isFinishPending()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(itemCount(output, Items.IRON_INGOT)).isZero();

        assertThat(lane.tick(1)).isFalse();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);

        assertThat(lane.tick(10)).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(itemCount(output, Items.IRON_INGOT)).isEqualTo(1);
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
    void paused_factory_threads_round_trip_their_recipe_progress_and_context_without_failure_state() throws Exception {
        MachineControllerBlockEntity controller = controller(MMCR.id("paused_factory_machine"));
        MachineRecipe firstRecipe = recipe("paused_factory_first", 0);
        MachineRecipe secondRecipe = recipe("paused_factory_second", 0);
        RecipeRegistry.register(firstRecipe);
        RecipeRegistry.register(secondRecipe);
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
        setField(RecipeThread.class, first, "context", new RecipeCraftingContext(controller));
        setField(RecipeThread.class, second, "context", new RecipeCraftingContext(controller));
        setField(RecipeThread.class, first, "lastFailureUnloc", "mmcr.failure.first");
        setField(RecipeThread.class, second, "lastFailureUnloc", "mmcr.failure.second");
        scheduler.addThreadForTesting(first);
        scheduler.addThreadForTesting(second);
        scheduler.pause();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        scheduler.save(output);
        FactoryRecipeScheduler loaded = new FactoryRecipeScheduler(3, pool);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()), controller, pool);

        assertThat(loaded.allThreads()).hasSize(3);
        List<FactoryRecipeThread> restored = loaded.allThreads().subList(1, 3);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getRecipe().id())
                .containsExactly(firstRecipe.id(), secondRecipe.id());
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(3, 7);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTotalTick()).containsExactly(20, 20);
        assertThat(restored).extracting(thread -> fieldValue(RecipeThread.class, thread, "context")).doesNotContainNull();
        assertThat(restored).extracting(FactoryRecipeThread::getLastFailureUnloc)
                .containsOnlyNulls();

        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(3, 7);
        loaded.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(3, 7);
        loaded.resume();
        loaded.tickThreads(controller, List.of(), controller.getStructureVersion(), 1, pool);
        assertThat(restored).extracting(thread -> thread.getActiveRecipe().getTick()).containsExactly(4, 8);
    }

    @Test
    void paused_scheduler_does_not_restart_after_a_queued_shared_finish_resolves() throws Exception {
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_paused_shared_finish"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_paused_shared_finish_recipe"),
                MMCR.id("factory_paused_shared_finish"), 1, List.of(), List.of(), List.of(), 0, 1);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, pool);

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool);
        SharedIoCoordinator.get(level).resolve(domain);
        FactoryRecipeThread thread = scheduler.allThreads().getFirst();
        thread.getActiveRecipe().beginFinishCommit();
        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool);
        scheduler.pause();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.getActiveRecipe()).isNull();
        assertThat(thread.isStartPending()).isFalse();
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void pause_clears_pending_shared_finish_continuation_and_resume_installs_the_latest_one() throws Exception {
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_paused_shared_continuation"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_paused_shared_continuation_recipe"),
                MMCR.id("factory_paused_shared_continuation"), 1, List.of(), List.of(), List.of(), 0, 1);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1, pool);
        AtomicInteger staleContinuations = new AtomicInteger();
        AtomicInteger latestContinuations = new AtomicInteger();

        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool, staleContinuations::incrementAndGet);
        SharedIoCoordinator.get(level).resolve(domain);
        FactoryRecipeThread thread = scheduler.allThreads().getFirst();
        thread.getActiveRecipe().beginFinishCommit();
        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool, staleContinuations::incrementAndGet);
        scheduler.pause();
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(staleContinuations).hasValue(0);
        assertThat(thread.getActiveRecipe()).isNull();

        scheduler.resume();
        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool, latestContinuations::incrementAndGet);
        SharedIoCoordinator.get(level).resolve(domain);
        thread.getActiveRecipe().beginFinishCommit();
        scheduler.tickThreads(controller, List.of(recipe), controller.getStructureVersion(), 1, pool, latestContinuations::incrementAndGet);
        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(staleContinuations).hasValue(0);
        assertThat(latestContinuations).hasValue(1);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void thread_snapshot_exposes_waiting_failure_without_marking_thread_running() throws Exception {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(1);
        FactoryRecipeThread thread = FactoryRecipeThread.base(null, RecipeCraftingContextPool.global());
        setField(RecipeThread.class, thread, "status", RecipeThread.Status.FAILED);
        setField(RecipeThread.class, thread, "lastFailureUnloc", "gui.mmcr.controller.failure.missing_output");
        scheduler.addThreadForTesting(thread);

        FactoryRecipeScheduler.ThreadSnapshot snapshot = scheduler.threadSnapshots().get(1);

        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.lastFailureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_output");
    }

    @Test
    void activeThreadCountOnlyCountsThreadsWithAnActiveRecipe() throws Exception {
        FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(2);
        FactoryRecipeThread pendingThread = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        FactoryRecipeThread workingThread = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        setField(RecipeThread.class, pendingThread, "startPending", true);
        workingThread.setActiveRecipeForTesting(activeRecipeWithParallelism(1));
        scheduler.addThreadForTesting(pendingThread);
        scheduler.addThreadForTesting(workingThread);

        assertThat(scheduler.activeThreadCount()).isEqualTo(1);
    }

    @Test
    void tickClearsStartPendingWhenContextHasBeenReleasedWithoutClearingTheFlag() throws Exception {
        FactoryRecipeThread thread = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
        setField(RecipeThread.class, thread, "startPending", true);
        setField(RecipeThread.class, thread, "pendingStartDomain",
                new StructureClaimRegistry.ResourceDomain(1L, 1L, Set.of()));

        thread.tick();

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.isIdle()).isTrue();
    }

    @Test
    void asyncStartFailureSurfacesTheLastFailureUnloc() throws Exception {
        // The base lane must commit its shared start so a single-thread controller
        // can still pick up recipes that other shared components have not yet drained.
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64).build());
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        BlockPos controllerPos = new BlockPos(0, 64, 0);
        MachineControllerBlockEntity controller = controllerWithInput(MMCR.id("factory_async_failure"), controllerPos, input);
        ServerLevel level = serverLevel(List.of(controller, input));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        registry.claim(controllerPos, List.of(new StructureClaimRegistry.Claim(input.getBlockPos(),
                ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controllerPos);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_async_failure_recipe"), MMCR.id("factory_async_failure"),
                20, List.of(), List.of(), List.of(), 0, 0, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, new RecipeCraftingContextPool());

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, controller.getStructureVersion())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        SharedIoCoordinator.get(level).resolve(domain);

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.getActiveRecipe()).isNotNull();
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
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

    private static ItemRequirement enchantedInput(int sharpnessLevel) {
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", sharpnessLevel);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup()), enchantments))));
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD), 1,
                ItemStack.EMPTY, 1F, List.of(), predicates, 0F);
    }

    private static ItemStack enchantedSword(String enchantmentId, int level) {
        var lookup = VanillaRegistries.createLookup();
        var enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, Identifier.parse(enchantmentId)));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return sword;
    }

    private static MachineControllerBlockEntity controller(Identifier machineId) throws Exception {
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

    private static MachineControllerBlockEntity controllerWithInput(Identifier machineId, BlockPos pos,
                                                                      ItemInputBusBlockEntity input) throws Exception {
        return controllerWithComponents(machineId, pos, input);
    }

    private static MachineControllerBlockEntity controllerWithComponents(Identifier machineId, BlockPos pos,
                                                                           BlockEntity... components) throws Exception {
        MachineControllerBlockEntity controller = controller(machineId);
        setField(BlockEntity.class, controller, "worldPosition", pos);
        List<ProcessingComponent> processingComponents = new ArrayList<>();
        for (BlockEntity component : components) {
            processingComponents.add(new ProcessingComponent(componentFor(component), component, component.getBlockPos(), BlockPos.ZERO, (String) null));
        }
        setField(MachineControllerBlockEntity.class, controller, "components", processingComponents);
        return controller;
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(ItemInputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
        initializeLinkedAppearance(bus);
        setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6) {
            @Override protected void onContentsChanged(int slot) { }
        });
        return bus;
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        ItemOutputBusBlockEntity bus = (ItemOutputBusBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(ItemOutputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
        initializeLinkedAppearance(bus);
        setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6) {
            @Override protected void onContentsChanged(int slot) { }
        });
        return bus;
    }

    private static EnergyInputHatchBlockEntity energyInputHatch(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        EnergyInputHatchBlockEntity hatch = (EnergyInputHatchBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(EnergyInputHatchBlockEntity.class);
        setField(BlockEntity.class, hatch, "type", null);
        setField(BlockEntity.class, hatch, "worldPosition", pos);
        setField(BlockEntity.class, hatch, "blockState", Blocks.CHEST.defaultBlockState());
        setField(EnergyHatchBlockEntity.class, hatch, "storage",
                new LongEnergyStorage(100, 100, () -> {}));
        return hatch;
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

    private static int itemCount(ItemOutputBusBlockEntity output, Item item) {
        int count = 0;
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            ItemStack stack = output.getItemStackHandler(null).getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static MachineComponent componentFor(BlockEntity component) {
        if (component instanceof ItemInputBusBlockEntity) {
            return new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT);
        }
        if (component instanceof ItemOutputBusBlockEntity) {
            return new MachineComponent(PortKinds.ITEM_OUTPUT, IOType.OUTPUT);
        }
        if (component instanceof EnergyInputHatchBlockEntity) {
            return new MachineComponent(PortKinds.ENERGY_INPUT, IOType.INPUT);
        }
        throw new IllegalArgumentException("Unsupported test component: " + component.getClass().getSimpleName());
    }

    private static ServerLevel serverLevel(List<BlockEntity> blockEntities) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestServerLevel level = (TestServerLevel) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestServerLevel.class);
        setField(TestServerLevel.class, level, "blocks", new HashMap<>());
        setField(TestServerLevel.class, level, "blockEntities", blockEntities.stream()
                .collect(Collectors.toMap(BlockEntity::getBlockPos, entity -> entity)));
        return level;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockState> blocks;
        private Map<BlockPos, BlockEntity> blockEntities;
        private long gameTime;

        private TestServerLevel() {
            super(null, null, null, null, null, null, false, 0L, List.of(), false);
        }

        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
        @Override public void blockEntityChanged(BlockPos pos) { }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) { blocks.put(pos, state); return true; }
        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
        @Override public boolean hasChunk(int chunkX, int chunkZ) { return true; }
        @Override public void invalidateCapabilities(BlockPos pos) { }
        @Override public long getGameTime() { return gameTime; }

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
