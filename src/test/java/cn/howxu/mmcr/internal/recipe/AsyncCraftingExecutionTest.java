package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.AsyncCraftingExecution;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies shared-IO asynchronous recipe execution boundaries.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncCraftingExecutionTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void async_finish_release_commits_its_continuation_in_the_same_shared_io_fence() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        Identifier machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(machineId, "async execution", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();

        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_execution_finish"), machineId, 1,
                List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        AtomicInteger finishes = new AtomicInteger();
        thread.setFinishContinuation(finishes::incrementAndGet);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        completeTick(controller);
        long epochBeforeFinish = controller.resourceAvailabilityEpoch();
        thread.tick();
        completeTick(controller);
        thread.tick();
        completeTick(controller);

        assertThat(thread.runtime().active()).isFalse();
        assertThat(finishes).hasValue(1);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(epochBeforeFinish + 1L);
    }

    @Test
    void finished_async_lane_restarts_on_the_next_tick_through_the_shared_io_fence() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        Identifier machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(machineId, "async restart", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();

        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_execution_restart"), machineId, 1,
                List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        completeTick(controller);
        thread.tick();
        completeTick(controller);
        thread.tick();
        completeTick(controller);

        assertThat(thread.runtime().active()).isFalse();
        RuntimeTestFixtures.advanceGameTime(level);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        completeTick(controller);
        assertThat(thread.runtime().active()).isTrue();
    }

    @Test
    void async_last_recipe_restarts_inside_the_finish_shared_io_fence() throws InterruptedException {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        Identifier machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(machineId, "same fence restart", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("same_fence_last_recipe"), machineId, 1,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        completeTick(controller);
        thread.tick();
        completeTick(controller);
        assertThat(thread.runtime().finishPending()).isTrue();

        thread.tick();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        assertThat(coordinator.awaitPendingMainStepForTesting(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        coordinator.completeTick();
        assertThat(coordinator.awaitPendingMainStepForTesting(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        coordinator.completeTick();
        assertThat(coordinator.awaitPendingMainStepForTesting(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        coordinator.completeTick(() -> sharedIo.resolve(level));

        assertThat(thread.runtime().active()).isTrue();
        assertThat(thread.runtime().recipe()).isEqualTo(recipe);
    }

    @Test
    void empty_per_tick_workset_waits_for_level_tick_submission() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        Identifier machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(machineId, "empty tick workset", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("empty_tick_workset"), machineId, 20,
                List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        completeTick(controller);

        thread.tick();

        assertThat(thread.runtime().activeRecipe().getTick()).isZero();
        assertThat(SharedIoCoordinator.get(level).resolve(level)).isZero();
        assertThat(MachineAsyncCoordinator.get(level).hasPendingMainStepForTesting()).isTrue();

        completeTick(controller);

        assertThat(thread.runtime().activeRecipe().getTick()).isEqualTo(1);
        assertThat(MachineAsyncCoordinator.get(level).hasPendingMainStepForTesting()).isFalse();
    }

    @Test
    void failed_async_tick_releases_the_lane_for_the_next_tick() throws Exception {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        Identifier machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(machineId, "failed async tick", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(controller.getBlockPos());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("failed_async_tick"), machineId, 20,
                List.of(), List.of());
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        completeTick(controller);

        var beginPendingTick = RecipeThread.class.getDeclaredMethod("beginPendingTick",
                StructureClaimRegistry.ResourceDomain.class);
        beginPendingTick.setAccessible(true);
        long token = (long) beginPendingTick.invoke(thread, domain);
        var tickTaskHooks = RecipeThread.class.getDeclaredMethod("tickTaskHooks", long.class, long.class);
        tickTaskHooks.setAccessible(true);
        long lifecycleEpoch = controller.lifecycleEpoch();
        MachineAsyncCoordinator.TaskHooks hooks = (MachineAsyncCoordinator.TaskHooks)
                tickTaskHooks.invoke(thread, token, lifecycleEpoch);
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        coordinator.submitDetailed(new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(), level.getGameTime(),
                        MachineWorkMode.ASYNC, thread.asyncLaneId(), lifecycleEpoch), ignored -> {
                    throw new IllegalStateException("planned failure");
                }, null, hooks);
        coordinator.completeUntilIdleForTesting(() -> 0);

        assertThat(thread.tickPendingForTesting()).isFalse();

        RuntimeTestFixtures.advanceGameTime(level);
        int tickBeforeRetry = thread.runtime().activeRecipe().getTick();
        thread.tick(controller.currentRuntimeSnapshot());
        assertThat(thread.tickPendingForTesting()).isTrue();
        assertThat(thread.runtime().activeRecipe().getTick()).isEqualTo(tickBeforeRetry);
        completeTick(controller);
        assertThat(thread.tickPendingForTesting()).isFalse();
        assertThat(thread.runtime().activeRecipe().getTick()).isEqualTo(tickBeforeRetry + 1);
    }

    @Test
    void async_start_prefetches_only_during_the_main_thread_shared_io_commit() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        Identifier machineId = MMCR.id("test_cube");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(machineId, "async prefetch", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        AsyncPrefetchNetworkCapability network = new AsyncPrefetchNetworkCapability(new BlockPos(1, 0, 0));
        network.setLevel(level);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, network,
                network.getBlockPos(), network.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(controller);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(controller.getBlockPos(), List.of()).accepted()).isTrue();

        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_execution_prefetch"), machineId, 2,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();

        // Prefetch feasibility is planned on the main thread before shared IO, but nothing commits yet.
        MachineAsyncCoordinator.get(level).completeTick();
        assertThat(network.planCalls()).isEqualTo(1);
        assertThat(network.commitCalls()).isZero();
        assertThat(network.planThread()).isSameAs(Thread.currentThread());
        assertThat(network.commitThread()).isNull();
        assertThat(thread.runtime().active()).isFalse();

        completeTick(controller);

        assertThat(network.planCalls()).isEqualTo(2);
        assertThat(network.commitCalls()).isEqualTo(1);
        assertThat(network.committedAmount()).isEqualTo(4L);
        assertThat(network.planThread()).isSameAs(Thread.currentThread());
        assertThat(network.commitThread()).isSameAs(Thread.currentThread());
        assertThat(thread.runtime().active()).isTrue();
    }

    @Test
    void async_finish_forwards_a_shared_io_restart_continuation() {
        AsyncExecutionContext context = new AsyncExecutionContext(new MachineAsyncCoordinator.TaskKey(BlockPos.ZERO, 0L));
        AsyncContinuation continuation = AsyncCraftingExecution.finish("base", 1L);

        AsyncContinuation.Yield.MainThread lifecycle = (AsyncContinuation.Yield.MainThread) continuation.advance(context);
        continuation = lifecycle.resume().apply(MainThreadStep.Result.success());
        AsyncContinuation.Yield.MainThread screenFlush = (AsyncContinuation.Yield.MainThread) continuation.advance(context);
        continuation = screenFlush.resume().apply(MainThreadStep.Result.success());
        AsyncContinuation.Yield.MainThread sharedIo = (AsyncContinuation.Yield.MainThread) continuation.advance(context);
        AsyncContinuation restart = ignored -> AsyncContinuation.Yield.complete();

        assertThat(sharedIo.resume().apply(MainThreadStep.Result.value(restart))).isSameAs(restart);
    }

    private static void completeTick(MachineControllerBlockEntity controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        long gameTime = level.getGameTime();
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }

    private static final class AsyncPrefetchNetworkCapability extends BlockEntity
            implements CapabilityHost, MachineCapability, RecipeEnergyPrefetchFacet, ValueFacet<LongValueStorage> {
        private final LongValueStorage storage = new LongValueStorage(100L, 100L, null);
        private final CapabilityType type = new CapabilityType(EnergyRequirement.TYPE.id());
        private final CapabilityView view = new CapabilityView() {
            @Override
            public CapabilityType type() {
                return AsyncPrefetchNetworkCapability.this.type;
            }

            @Override
            public CapabilityDirections directions() {
                return CapabilityDirections.input();
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return Set.of(RecipeEnergyPrefetchFacet.class, ValueFacet.class);
            }
        };
        private final AtomicInteger planCalls = new AtomicInteger();
        private final AtomicInteger commitCalls = new AtomicInteger();
        private final AtomicLong committedAmount = new AtomicLong();
        private final AtomicReference<Thread> planThread = new AtomicReference<>();
        private final AtomicReference<Thread> commitThread = new AtomicReference<>();

        private AsyncPrefetchNetworkCapability(BlockPos pos) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            storage.setAmount(100L);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of(this));
        }

        @Override
        public String reservationKey() {
            return "test:async-prefetch-network";
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.input();
        }

        @Override
        public Optional<PrefetchPlan> planPrefetch(long requestedAmount) {
            planCalls.incrementAndGet();
            planThread.set(Thread.currentThread());
            return Optional.of(new PrefetchPlan(requestedAmount, transaction -> {
                commitCalls.incrementAndGet();
                commitThread.set(Thread.currentThread());
                long extracted = storage.extract(requestedAmount, transaction);
                if (extracted != requestedAmount) throw new IllegalStateException("async prefetch storage shortage");
                committedAmount.addAndGet(extracted);
                return CapabilityResult.successful();
            }));
        }

        @Override
        public void restoreReservation(long amount) {
        }

        @Override
        public long releaseReservation(long amount) {
            return amount;
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public CapabilityView view() {
            return view;
        }

        @Override
        public LongValueStorage storage() {
            return storage;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return transaction -> CapabilityResult.successful();
        }

        private int planCalls() {
            return planCalls.get();
        }

        private int commitCalls() {
            return commitCalls.get();
        }

        private long committedAmount() {
            return committedAmount.get();
        }

        private Thread planThread() {
            return planThread.get();
        }

        private Thread commitThread() {
            return commitThread.get();
        }
    }
}
