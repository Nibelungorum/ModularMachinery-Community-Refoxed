package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real shared-controller ticker boundaries and configuration mutations with a faulty presentation source.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerExecutionBoundaryTest {
    private static final long TICK_ENERGY = 7L;
    private final List<ServerLevel> levels = new ArrayList<>();
    private ServerThreadTestScope serverThreadScope;
    private MachineWorkMode previousMode;
    private int previousBudget;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void setup() throws Exception {
        TestBootstrap.bootstrapCapabilities();
        serverThreadScope = ServerThreadTestScope.open();
        previousMode = ServerConfig.machineWorkMode();
        previousBudget = ServerConfig.asyncSharedIoRequestsPerLevelTick();
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        RecipeRegistry.clearForTesting();
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            for (ServerLevel level : levels) SharedIoEvents.onLevelUnload(new LevelEvent.Unload(level));
            RecipeRegistry.clearForTesting();
            ConfigTestSupport.setMachineWorkMode(previousMode);
            setBudget(previousBudget);
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void shared_sync_consumes_at_each_real_be_boundary_and_never_replays_in_post(int budget) throws Exception {
        List<MachineControllerBlockEntity> controllers = sharedControllers(budget, 1, true);
        MachineControllerBlockEntity first = controllers.get(0);
        MachineControllerBlockEntity second = controllers.get(1);
        ServerLevel level = (ServerLevel) first.getLevel();
        EnergyInputHatchBlockEntity input = inputOf(first);
        long afterStarts = input.energyStorage().getAmountAsLong();
        assertThat(first.resourceDomain()).isEqualTo(second.resourceDomain());
        assertThat(first.resourceDomain().controllers()).hasSize(2);

        for (int tick = 1; tick <= 6; tick++) {
            pre(level);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - (tick - 1L) * budget * TICK_ENERGY);
            first.serverTick();
            int firstTicks = budget == 2 ? tick : (tick + 1) / 2;
            int secondBefore = budget == 2 ? tick - 1 : (tick - 1) / 2;
            assertThat(runtime(first).tickCount()).isEqualTo(firstTicks);
            assertThat(runtime(second).tickCount()).isEqualTo(secondBefore);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - TICK_ENERGY * (firstTicks + secondBefore));
            assertThat(pendingTickRequests(level)).isZero();

            second.serverTick();
            int secondTicks = budget == 2 ? tick : tick / 2;
            assertThat(runtime(second).tickCount()).isEqualTo(secondTicks);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - tick * budget * TICK_ENERGY);
            assertThat(runtime(first).installedProgram()).isNotNull();
            assertThat(runtime(second).installedProgram()).isNotNull();
            assertThat(pendingTickRequests(level)).isZero();

            // Duplicate real tickers and Post events cannot spend the same world tick twice.
            first.serverTick();
            second.serverTick();
            post(level);
            post(level);
            assertThat(runtime(first).tickCount()).isEqualTo(firstTicks);
            assertThat(runtime(second).tickCount()).isEqualTo(secondTicks);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - tick * budget * TICK_ENERGY);
            assertThat(pendingTickRequests(level)).isZero();
        }
    }

    @Test
    void one_budget_rotates_two_shared_domains_despite_fixed_real_ticker_order() throws Exception {
        List<MachineControllerBlockEntity> controllers = sharedControllers(1, 2, true);
        ServerLevel level = (ServerLevel) controllers.getFirst().getLevel();
        EnergyInputHatchBlockEntity firstInput = inputOf(controllers.get(0));
        EnergyInputHatchBlockEntity secondInput = inputOf(controllers.get(2));
        long firstStart = firstInput.energyStorage().getAmountAsLong();
        long secondStart = secondInput.energyStorage().getAmountAsLong();
        assertThat(controllers.get(0).resourceDomain()).isEqualTo(controllers.get(1).resourceDomain());
        assertThat(controllers.get(2).resourceDomain()).isEqualTo(controllers.get(3).resourceDomain());
        assertThat(controllers.get(0).resourceDomain()).isNotEqualTo(controllers.get(2).resourceDomain());

        for (int tick = 1; tick <= 8; tick++) {
            pre(level);
            for (MachineControllerBlockEntity controller : controllers) {
                controller.serverTick();
                assertThat(firstInput.energyStorage().getAmountAsLong()).isEqualTo(firstStart - TICK_ENERGY * domainTicks(controllers, 0));
                assertThat(secondInput.energyStorage().getAmountAsLong()).isEqualTo(secondStart - TICK_ENERGY * domainTicks(controllers, 2));
                assertThat(pendingTickRequests(level)).isZero();
            }
            int firstTicks = domainTicks(controllers, 0);
            int secondTicks = domainTicks(controllers, 2);
            assertThat(firstTicks + secondTicks).isEqualTo(tick);
            assertThat(Math.abs(firstTicks - secondTicks)).isLessThanOrEqualTo(1);
            post(level);
            post(level);
            assertThat(domainTicks(controllers, 0)).isEqualTo(firstTicks);
            assertThat(domainTicks(controllers, 2)).isEqualTo(secondTicks);
            assertThat(firstInput.energyStorage().getAmountAsLong()).isEqualTo(firstStart - TICK_ENERGY * firstTicks);
            assertThat(secondInput.energyStorage().getAmountAsLong()).isEqualTo(secondStart - TICK_ENERGY * secondTicks);
        }
        assertThat(controllers).allSatisfy(controller -> assertThat(runtime(controller).tickCount()).isEqualTo(2));
    }

    @Test
    void shared_sync_start_still_pays_in_post_and_only_ticks_at_the_next_be_boundary() throws Exception {
        List<MachineControllerBlockEntity> controllers = sharedControllers(2, 1, false);
        ServerLevel level = (ServerLevel) controllers.getFirst().getLevel();
        EnergyInputHatchBlockEntity input = inputOf(controllers.getFirst());
        long beforeStart = input.energyStorage().getAmountAsLong();
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("shared_boundary_start"), MMCR.id("test_cube"),
                40, List.of(new EnergyRequirement(TICK_ENERGY)), List.of()));

        pre(level);
        for (MachineControllerBlockEntity controller : controllers) {
            controller.serverTick();
            assertThat(runtime(controller).active()).isFalse();
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(beforeStart);
            assertThat(pendingTickRequests(level)).isZero();
        }
        post(level);
        post(level);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(beforeStart - 2L * TICK_ENERGY);
        assertThat(controllers).allSatisfy(controller -> {
            assertThat(runtime(controller).active()).isTrue();
            assertThat(runtime(controller).tickCount()).isZero();
            assertThat(runtime(controller).installedProgram()).isNotNull();
        });
        pre(level);
        controllers.get(0).serverTick();
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(beforeStart - 3L * TICK_ENERGY);
        controllers.get(1).serverTick();
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(beforeStart - 4L * TICK_ENERGY);
        post(level);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(beforeStart - 4L * TICK_ENERGY);
        assertThat(controllers).allSatisfy(controller -> assertThat(runtime(controller).tickCount()).isEqualTo(1));
        assertThat(pendingTickRequests(level)).isZero();
    }

    @Test
    void uninstalled_legacy_requests_remain_single_until_post_then_handoff_to_real_sync_tickers() throws Exception {
        List<MachineControllerBlockEntity> controllers = sharedControllers(2, 1, true);
        ServerLevel level = (ServerLevel) controllers.getFirst().getLevel();
        EnergyInputHatchBlockEntity input = inputOf(controllers.getFirst());
        long afterStarts = input.energyStorage().getAmountAsLong();
        // No Pre round yet: retain the compatibility boundary and its pending-request guard.
        for (int repeat = 0; repeat < 2; repeat++) {
            for (MachineControllerBlockEntity controller : controllers) {
                controller.serverTick();
                assertThat(runtime(controller).installedProgram()).isNull();
                assertThat(runtime(controller).tickCount()).isZero();
            }
            assertThat(pendingTickRequests(level)).isEqualTo(2);
            assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts);
        }
        pre(level);
        for (MachineControllerBlockEntity controller : controllers) controller.serverTick();
        assertThat(pendingTickRequests(level)).isEqualTo(2);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts);
        post(level);
        post(level);
        assertThat(controllers).allSatisfy(controller -> assertThat(runtime(controller).tickCount()).isEqualTo(1));
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - 2L * TICK_ENERGY);
        assertThat(pendingTickRequests(level)).isZero();

        pre(level);
        controllers.get(0).serverTick();
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - 3L * TICK_ENERGY);
        controllers.get(1).serverTick();
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - 4L * TICK_ENERGY);
        assertThat(pendingTickRequests(level)).isZero();
        post(level);
        assertThat(input.energyStorage().getAmountAsLong()).isEqualTo(afterStarts - 4L * TICK_ENERGY);
        assertThat(controllers).allSatisfy(controller -> {
            assertThat(runtime(controller).tickCount()).isEqualTo(2);
            assertThat(runtime(controller).installedProgram()).isNotNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"clear-machine", "replace-machine", "unform"})
    void setters_mutate_structure_without_reading_a_permanently_faulty_storage(String operation) throws Exception {
        ResourceLocation machineId = MMCR.id("faulty_setter_" + operation.replace('-', '_'));
        RuntimeTestFixtures.registerRecipePool(machineId);
        TestBootstrap.bindControllerForTesting(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, BlockPos.ZERO,
                ModBlocks.controllerFor(machineId).get().defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        FaultyStorageInputBus input = new FaultyStorageInputBus(new BlockPos(1, 0, 0));
        DynamicMachine machine = new DynamicMachine(machineId, "faulty setter",
                new BlockArray(Map.of(input.getBlockPos(), new BlockPredicate.OfBlock(input.getBlockState().getBlock()))),
                MachineControllerSpec.defaultsFor(machineId));
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, input);
        levels.add((ServerLevel) controller.getLevel());
        // Explicit membership also verifies the fixture cannot pass with an empty presentation.
        controller.componentRuntime().replaceComponents(List.of(component(input)));
        controller.componentRuntime().replaceModifiers(Map.of("old-modifier", List.of()));
        RuntimeTestFixtures.republish(controller);
        var before = controller.runtimeSnapshot();
        assertThat(before.capabilityPresentations()).isNotEmpty();
        assertThat(before.linkedPortPositions()).contains(input.getBlockPos());
        assertThat(input.linkedControllerPositions()).contains(controller.getBlockPos());
        assertThat(controller.currentRecipePoolId()).isEqualTo(machineId);
        long lifecycle = controller.lifecycleEpoch();
        ResourceLocation replacementId = MMCR.id("faulty_setter_replacement");
        RuntimeTestFixtures.registerRecipePool(replacementId);
        DynamicMachine replacement = new DynamicMachine(replacementId, "replacement", machine.pattern(),
                MachineControllerSpec.defaultsFor(replacementId));
        input.broken = true;
        controller.componentRuntime().markCapabilitySourceChanged(input.getBlockPos());
        assertThatThrownBy(controller::runtimeSnapshot).isInstanceOf(IllegalStateException.class)
                .hasMessage("broken setter storage");
        int reads = input.failedReads;

        assertThatCode(() -> {
            switch (operation) {
                case "clear-machine" -> controller.setMachine(null);
                case "replace-machine" -> controller.setMachine(replacement);
                case "unform" -> controller.setFormed(false);
                default -> throw new AssertionError(operation);
            }
        }).doesNotThrowAnyException();

        assertThat(input.failedReads).isEqualTo(reads);
        assertThat(input.broken).isTrue();
        var structure = controller.currentStructureSnapshot();
        assertThat(structure.configuredMachine()).isSameAs(operation.equals("clear-machine") ? null
                : operation.equals("replace-machine") ? replacement : machine);
        assertThat(structure.formed()).isEqualTo(!operation.equals("unform"));
        assertThat(structure.version()).isGreaterThan(before.structure().version());
        assertThat(controller.lifecycleEpoch()).isEqualTo(lifecycle + (operation.equals("unform") ? 0L : 1L));
        assertThat(controller.componentRuntime().linkedPortPositions()).isEqualTo(before.linkedPortPositions());
        assertThat(input.linkedControllerPositions()).contains(controller.getBlockPos());
        assertThat(controller.componentRuntime().components()).singleElement()
                .satisfies(component -> assertThat(component.getContainer()).isSameAs(input));
        if (!operation.equals("unform")) {
            assertThat(controller.componentRuntime().foundModifiers()).isEmpty();
            assertThat(controller.componentRuntime().modifierVersion()).isGreaterThan(before.modifierVersion());
            assertThat(controller.componentRuntime().foundLevels()).isEmpty();
        }
        assertThat(controller.currentRecipePoolId()).isEqualTo(machineId);
        assertThat(input.failedReads).isEqualTo(reads);
        // The setters preserve the source; an explicit content query must still report its fault.
        assertThatThrownBy(controller::runtimeSnapshot).isInstanceOf(IllegalStateException.class);
        input.broken = false;
        var recovered = controller.runtimeSnapshot();
        assertThat(recovered.structure()).isEqualTo(structure);
        assertThat(recovered.crafting().structureVersion()).isEqualTo(structure.version());
        assertThat(before.structure().formed()).isTrue();
        assertThat(before.structure().configuredMachine()).isSameAs(machine);
    }

    private List<MachineControllerBlockEntity> sharedControllers(int budget, int domainCount, boolean start) throws Exception {
        setBudget(budget);
        List<MachineControllerBlockEntity> controllers = new ArrayList<>();
        ServerLevel level = null;
        for (int domain = 0; domain < domainCount; domain++) {
            EnergyInputHatchBlockEntity input = RuntimeTestFixtures.energyInput(new BlockPos(domain * 20 + 1, 0, 0));
            for (int index = 0; index < 2; index++) {
                MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"),
                        new BlockPos(domain * 20 + index * 10, 0, 0));
                RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"),
                        "shared boundary", new BlockArray(Map.of())));
                levels.add((ServerLevel) controller.getLevel());
                if (level == null) level = (ServerLevel) controller.getLevel();
                else RuntimeTestFixtures.attachLevel(controller, level);
                RuntimeTestFixtures.replaceBlockEntity(controller, controller);
                level.setBlock(controller.getBlockPos(), controller.getBlockState(), 3);
                RuntimeTestFixtures.replaceBlockEntity(controller, input);
                level.setBlock(input.getBlockPos(), input.getBlockState(), 3);
                controller.componentRuntime().replaceComponents(List.of(component(input)));
                RuntimeTestFixtures.republish(controller);
                controllers.add(controller);
            }
            var claim = new StructureClaimRegistry.Claim(input.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED);
            for (MachineControllerBlockEntity controller : controllers.subList(domain * 2, domain * 2 + 2)) {
                assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of(claim)).accepted()).isTrue();
            }
            input.energyStorage().setAmount(1_000L);
        }
        // Receipt workers are deliberately stopped; SYNC must not depend on them.
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        installAsync(level, MachineAsyncCoordinator.forTesting(workers::addLast));
        if (start) {
            var recipe = RecipeTestSupport.create(MMCR.id("shared_boundary_recipe"), MMCR.id("test_cube"), 40,
                    List.of(new EnergyRequirement(TICK_ENERGY)), List.of());
            for (MachineControllerBlockEntity controller : controllers) {
                assertThat(runtime(controller).start(recipe, 1L).isCrafting()).isTrue();
                assertThat(runtime(controller).installedProgram()).isNull();
            }
        }
        return controllers;
    }

    private static ProcessingComponent component(IOPortBlockEntity port) {
        return new ProcessingComponent(new MachineComponent(port.kind(), port.ioType()), port,
                port.getBlockPos(), port.getBlockPos(), (String) null);
    }

    private static CraftingRuntime runtime(MachineControllerBlockEntity controller) {
        return RuntimeTestFixtures.craftingRuntime(controller);
    }

    private static EnergyInputHatchBlockEntity inputOf(MachineControllerBlockEntity controller) {
        return (EnergyInputHatchBlockEntity) controller.componentRuntime().components().getFirst().getContainer();
    }

    private static int domainTicks(List<MachineControllerBlockEntity> controllers, int offset) {
        return runtime(controllers.get(offset)).tickCount() + runtime(controllers.get(offset + 1)).tickCount();
    }

    private static void pre(ServerLevel level) {
        RuntimeTestFixtures.advanceGameTime(level);
        level.tickRateManager().tick();
        SharedIoEvents.onLevelPreTick(new LevelTickEvent.Pre(() -> true, level));
    }

    private static void post(ServerLevel level) {
        SharedIoEvents.onLevelTick(new LevelTickEvent.Post(() -> true, level));
    }

    private static void setBudget(int budget) {
        ServerConfig.ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK.clearCache();
        ServerConfig.ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK.set(budget);
    }

    private static long pendingTickRequests(ServerLevel level) throws ReflectiveOperationException {
        Field field = SharedIoCoordinator.class.getDeclaredField("requestsByController");
        field.setAccessible(true);
        Map<?, ?> requests = (Map<?, ?>) field.get(SharedIoCoordinator.get(level));
        return requests.values().stream().flatMap(value -> ((Set<?>) value).stream())
                .filter(SharedIoCoordinator.TickRequest.class::isInstance).count();
    }

    @SuppressWarnings("unchecked")
    private static void installAsync(ServerLevel level, MachineAsyncCoordinator coordinator) throws ReflectiveOperationException {
        Field field = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
        field.setAccessible(true);
        ((Map<ServerLevel, MachineAsyncCoordinator>) field.get(null)).put(level, coordinator);
    }

    /** Storage remains broken throughout configuration mutation. @author howxu <dev@howxu.cn> */
    private static final class FaultyStorageInputBus extends ItemInputBusBlockEntity {
        private boolean broken;
        private int failedReads;
        private final LongItemStorage storage = new LongItemStorage(1, 64L, null) {
            @Override
            public ItemStack getStackInSlot(int slot) {
                if (broken) {
                    failedReads++;
                    throw new IllegalStateException("broken setter storage");
                }
                return super.getStackInSlot(slot);
            }
        };

        private FaultyStorageInputBus(BlockPos pos) {
            super(pos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            storage.setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
        }

        @Override
        public LongItemStorage itemHandler() { return storage; }
    }
}
