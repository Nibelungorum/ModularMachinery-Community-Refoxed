package cn.howxu.mmcr;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedflux.AppliedFluxBridge;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyNetwork;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.compat.appliedflux.loaded.tile.FluxEnergyInputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedflux.loaded.tile.FluxEnergyOutputInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.Set;

/**
 * Deterministic GameTest coverage for the optional AppFlux ME Flux port integration.
 *
 * <p>Each test asserts only state that is observable immediately after placement or after
 * a bounded number of server ticks. Tick-dependent asynchronous behaviours such as the
 * AE2 {@code TickRates.Interface} ticker wake-up and the output budget refresh are
 * deferred to {@link #pendingGameTests()} so unstable tick assertions do not block the
 * required GameTest server validation.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AppliedFluxInterfaceGameTest {

    private static final Identifier INPUT_PORT_ID = MMCR.id("appflux_me_flux_input_interface");
    private static final Identifier OUTPUT_PORT_ID = MMCR.id("appflux_me_flux_output_interface");

    public void portBlocksResolveToFluxBlockEntities(GameTestHelper helper) {
        assertBridgesAvailable();

        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        BlockPos controllerPos = new BlockPos(1, 1, 0);

        helper.setBlock(inputPos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get(OUTPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(controllerPos,
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                        .setValue(MachineControllerBlock.FACING, Direction.SOUTH));

        BlockEntity inputEntity = helper.getBlockEntity(inputPos, BlockEntity.class);
        BlockEntity outputEntity = helper.getBlockEntity(outputPos, BlockEntity.class);

        helper.assertTrue(inputEntity instanceof FluxEnergyInputInterfaceBlockEntity,
                "AppFlux input block resolves to FluxEnergyInputInterfaceBlockEntity");
        helper.assertTrue(outputEntity instanceof FluxEnergyOutputInterfaceBlockEntity,
                "AppFlux output block resolves to FluxEnergyOutputInterfaceBlockEntity");

        helper.succeed();
    }

    public void prefetchReservationRestoresWithActualPortNbt(GameTestHelper helper) {
        assertBridgesAvailable();
        BlockPos originalPos = new BlockPos(0, 1, 0);
        BlockPos restoredPos = new BlockPos(2, 1, 0);
        helper.setBlock(originalPos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(restoredPos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        var original = helper.getBlockEntity(originalPos, FluxEnergyInputInterfaceBlockEntity.class);
        var restored = helper.getBlockEntity(restoredPos, FluxEnergyInputInterfaceBlockEntity.class);
        var networkStorage = new TestEnergyStorage(100);
        var network = new FluxEnergyNetwork(networkStorage);
        var capability = new FluxEnergyInputCapability(inputBuffer(original), "gametest-restored-prefetch", network::planExactExtract);
        var plan = capability.planPrefetch(60).orElseThrow();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(plan.operation().commit(transaction).success(), "Production prefetch adapter journals the actual placed port buffer");
            transaction.commit();
        }
        var originalCapability = (FluxEnergyInputCapability) original.capabilitySnapshot().capabilities().getFirst();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(originalCapability.consumeReservation(20, transaction).success(), "Original port consumes part of the reservation");
            transaction.commit();
        }
        var saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        var buffer = inputBuffer(restored);
        helper.assertTrue(buffer.amount() == 40 && buffer.reserved() == 40 && buffer.idleExcess() == 0
                        && networkStorage.amount == 40,
                "Actual registered port NBT restores remaining energy and reservation without repeating bridge extraction");
        var restoredCapability = (FluxEnergyInputCapability) restored.capabilitySnapshot().capabilities().getFirst();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(restoredCapability.consumeReservation(10, transaction).success(), "Restored port supports actual transactional consumption");
        }
        helper.assertTrue(buffer.amount() == 40 && buffer.reserved() == 40 && networkStorage.amount == 40,
                "Restored consumption rollback preserves cache, reservation and network balance");
        try (Transaction transaction = Transaction.openRoot()) {
            restoredCapability.consumeReservation(10, transaction);
            transaction.commit();
        }
        helper.assertTrue(restoredCapability.releaseReservation(30) == 30 && restoredCapability.releaseReservation(30) == 0
                        && buffer.amount() == 30 && buffer.reserved() == 0 && buffer.idleExcess() == 30
                        && networkStorage.amount == 40,
                "Restored remainder becomes idle exactly once; save/load, consume and release conserve total energy");
        helper.succeed();
    }

    public void fullPrefetchCacheRejectsWaitingSearchesAndRecoversAfterConsumption(GameTestHelper helper) {
        assertBridgesAvailable();
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        var port = helper.getBlockEntity(pos, FluxEnergyInputInterfaceBlockEntity.class);
        FluxEnergyBuffer buffer = inputBuffer(port);
        var networkStorage = new TestEnergyStorage(Long.MAX_VALUE);
        var network = new FluxEnergyNetwork(networkStorage);
        int[] networkPlans = {0};
        var capability = new FluxEnergyInputCapability(buffer, "gametest-full-prefetch", requested -> {
            networkPlans[0]++;
            return network.planExactExtract(requested);
        });
        var occupied = capability.planPrefetch(Long.MAX_VALUE - 10L).orElseThrow();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(occupied.operation().commit(transaction).success(),
                    "Running work owns the actual port's cached energy");
            transaction.commit();
        }
        networkStorage.amount = 100L;
        var context = new CraftingContext(new CapabilitySnapshot(List.of(capability)), List.of());
        var recipe = MachineRecipe.fromCanonical(MMCR.id("full_cache_search"), MMCR.id("test_cube"), 20,
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 1L)), List.of(), List.of(),
                0, 64, false, false, false, Set.of());

        for (int lane = 0; lane < 64; lane++) {
            helper.assertTrue(!context.planStartResult(recipe, 1L).successful(),
                    "A waiting search cannot prove a prefetch which does not fit its port");
        }
        helper.assertTrue(networkPlans[0] == 1 && networkStorage.amount == 100L
                        && buffer.amount() == Long.MAX_VALUE - 10L && buffer.reserved() == buffer.amount(),
                "Rejected search proofs do not query the network or change already-owned energy");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(capability.consumeReservation(20L, transaction).success(),
                    "Running work frees actual local cache space");
            transaction.commit();
        }
        helper.assertTrue(context.planStartResult(recipe, 1L).successful()
                        && networkStorage.amount == 100L && buffer.amount() == Long.MAX_VALUE - 30L,
                "A new live proof succeeds after space is freed, without consuming network energy during proof");
        var recovered = capability.planPrefetch(20L).orElseThrow();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(recovered.operation().commit(transaction).success(),
                    "Recovered work acquires its own energy");
            transaction.commit();
        }
        helper.assertTrue(networkStorage.amount == 80L
                        && buffer.amount() == Long.MAX_VALUE - 10L && buffer.reserved() == buffer.amount(),
                "Recovered work pays its own energy exactly once");
        helper.succeed();
    }

    public void gridNodeHostsAreExposedAndExternalFeIsSuppressed(GameTestHelper helper) {
        assertBridgesAvailable();

        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);

        helper.setBlock(inputPos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get(OUTPUT_PORT_ID.getPath()).get().defaultBlockState());

        helper.runAtTickTime(1, () -> {
            IInWorldGridNodeHost inputHost = helper.getLevel().getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    helper.absolutePos(inputPos), null);
            IInWorldGridNodeHost outputHost = helper.getLevel().getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    helper.absolutePos(outputPos), null);

            helper.assertTrue(inputHost != null,
                    "AppFlux input exposes the AE2 in-world grid node host");
            helper.assertTrue(outputHost != null,
                    "AppFlux output exposes the AE2 in-world grid node host");

            BlockState inputState = helper.getLevel().getBlockState(helper.absolutePos(inputPos));
            BlockEntity inputEntity = helper.getBlockEntity(inputPos, BlockEntity.class);
            helper.assertTrue(cn.howxu.mmcr.internal.event.ModCapabilities.ENERGY_BLOCK.getCapability(
                            helper.getLevel(), helper.absolutePos(inputPos), inputState,
                            inputEntity, Direction.NORTH) == null,
                    "External NeoForge FE capability must remain suppressed on the AppFlux input");
            helper.assertTrue(cn.howxu.mmcr.internal.event.ModCapabilities.ENERGY_BLOCK.getCapability(
                            helper.getLevel(), helper.absolutePos(outputPos), inputState,
                            inputEntity, Direction.NORTH) == null,
                    "External NeoForge FE capability must remain suppressed on the AppFlux output");

            helper.succeed();
        });
    }

    public void fluxCapabilitiesBelongToEnergyFamilyWithoutTransferFacet(GameTestHelper helper) {
        assertBridgesAvailable();

        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);

        helper.setBlock(inputPos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get(OUTPUT_PORT_ID.getPath()).get().defaultBlockState());

        helper.runAtTickTime(1, () -> {
            BlockEntity inputEntity = helper.getBlockEntity(inputPos, BlockEntity.class);
            BlockEntity outputEntity = helper.getBlockEntity(outputPos, BlockEntity.class);

            assertHasNoTransferFacet(inputEntity);
            assertHasNoTransferFacet(outputEntity);

            helper.succeed();
        });
    }

    public void controllerFormsWithFluxPortsAndResolvesEnergyFamily(GameTestHelper helper) {
        assertBridgesAvailable();

        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(2, 1, 0);
        BlockPos controllerPos = new BlockPos(1, 1, 0);

        helper.setBlock(inputPos, ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get(OUTPUT_PORT_ID.getPath()).get().defaultBlockState());
        helper.setBlock(controllerPos,
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                        .setValue(MachineControllerBlock.FACING, Direction.SOUTH));

        BlockArray pattern = new BlockArray(java.util.Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get(INPUT_PORT_ID.getPath()).get()),
                new BlockPos(2, 1, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get(OUTPUT_PORT_ID.getPath()).get())));
        DynamicMachine machine = new DynamicMachine(INPUT_PORT_ID,
                "AppFlux Interface Integration Test",
                pattern);
        if (!MachineRegistry.containsStatic(INPUT_PORT_ID)) {
            MachineRegistry.register(machine);
        }

        var controller = helper.getBlockEntity(controllerPos,
                MachineControllerBlockEntity.class);
        long availabilityGameTime = helper.getLevel().getGameTime();
        long setupAvailabilityEpoch = controller.resourceAvailabilityEpoch();
        controller.setMachine(machine);
        helper.assertTrue(controller.resourceAvailabilityEpoch() == setupAvailabilityEpoch + 1L,
                "Initial module-state binding publishes availability in this action's game time");
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        assertCommittedEnergyPresentation(helper, controller, inputPos, outputPos, availabilityGameTime);
        helper.succeed();
    }

    private static void assertCommittedEnergyPresentation(GameTestHelper helper,
                                                          MachineControllerBlockEntity controller,
                                                          BlockPos inputPos, BlockPos outputPos,
                                                          long availabilityGameTime) {
        BlockPos itemPos = new BlockPos(1, 1, 2);
        helper.setBlock(itemPos, ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());
        var itemPort = helper.getBlockEntity(itemPos, ItemInputBusBlockEntity.class);
        var inputPort = helper.getBlockEntity(inputPos, FluxEnergyInputInterfaceBlockEntity.class);
        var outputPort = helper.getBlockEntity(outputPos, FluxEnergyOutputInterfaceBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(itemPort.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 3L, transaction) == 3L,
                    "Item fixture commits its initial contents");
            transaction.commit();
        }
        // Establish the item availability baseline before attaching the controller.
        itemPort.flushStorageChanges();
        List<IOPortBlockEntity> ports = List.of(itemPort, inputPort, outputPort);
        for (IOPortBlockEntity port : ports) port.linkControllerAppearance(controller.getBlockPos(), null);
        controller.componentRuntime().replaceComponents(ports.stream().map(port -> new ProcessingComponent(
                new MachineComponent(port.kind(), port.ioType()), port, port.getBlockPos(),
                port.getBlockPos().subtract(controller.getBlockPos()), List.of())).toList());
        var before = controller.currentRuntimeSnapshot();
        helper.assertTrue(before.capabilityPresentations().size() == 3,
                "Controller binds the item and both real Flux port capabilities");
        var itemRow = before.capabilityPresentations().getFirst();
        helper.assertTrue(BuiltinCapabilityDefinitions.ITEM_TYPE.id().equals(itemRow.typeId())
                        && itemRow.slots().getFirst().amount() == 3L,
                "Controller captures the initialized item row");
        helper.assertTrue(BuiltinCapabilityDefinitions.ENERGY_TYPE.id().equals(
                        before.capabilityPresentations().get(1).typeId())
                        && BuiltinCapabilityDefinitions.ENERGY_TYPE.id().equals(
                        before.capabilityPresentations().get(2).typeId()),
                "Both Flux ports resolve to the energy family in controller presentation");
        long beforeEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(outputPort.getEnergyStorage().insert(80L, transaction) == 80L,
                    "Actual Flux output buffer accepts pending recipe energy");
            helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == beforeEpoch,
                    "Uncommitted output storage does not invalidate presentation");
            transaction.commit();
        }
        var pending = controller.currentRuntimeSnapshot();
        helper.assertTrue(pending.capabilityPresentations().get(2).amount() == 80L
                        && outputPort.getEnergyStorage().amount() == 80L,
                "Latest presentation contains the committed Flux output buffer amount");
        helper.assertTrue(pending.capabilityPresentations().getFirst() == itemRow
                        && pending.capabilityPresentations().get(1) == before.capabilityPresentations().get(1),
                "Output commit reuses the unrelated item and input energy rows without recollection");
        helper.assertTrue(before.capabilityPresentations().get(2).amount() == 0L,
                "Output commit leaves the old snapshot isolated");
        long pendingEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        long availabilityEpoch = controller.resourceAvailabilityEpoch();
        outputPort.flushStorageChanges();
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == pendingEpoch
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch,
                "Flushing increased pending output establishes its baseline without publishing capacity availability");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(outputPort.getEnergyStorage().insert(40L, transaction) == 40L,
                    "Output rollback exercises an actual buffer mutation");
        }
        outputPort.flushStorageChanges();
        helper.assertTrue(outputPort.getEnergyStorage().amount() == 80L
                        && controller.componentRuntime().capabilityPresentationEpoch() == pendingEpoch
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch
                        && controller.currentRuntimeSnapshot().capabilityPresentations() == pending.capabilityPresentations(),
                "Output rollback restores storage without presentation or availability notifications");

        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(inputPort.getEnergyStorage().insert(100L, transaction) == 100L,
                    "Actual Flux input cache accepts the consumption fixture energy");
            helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == pendingEpoch
                            && controller.resourceAvailabilityEpoch() == availabilityEpoch,
                    "Input insertion does not notify before root commit");
            transaction.commit();
        }
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == pendingEpoch + 2L
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch,
                "Input root commit immediately retains storage/input callbacks while availability awaits flush");
        var cached = controller.currentRuntimeSnapshot();
        helper.assertTrue(cached.capabilityPresentations().get(1).amount() == 100L,
                "Controller observes input cache energy immediately after commit");
        helper.assertTrue(cached.capabilityPresentations().getFirst() == itemRow
                        && cached.capabilityPresentations().get(2) == pending.capabilityPresentations().get(2),
                "Input insertion reuses the unrelated item and output rows despite availability/input notifications");
        inputPort.flushStorageChanges();
        long cachedEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        long cachedAvailabilityEpoch = controller.resourceAvailabilityEpoch();
        helper.assertTrue(cachedEpoch == pendingEpoch + 3L
                        && cachedAvailabilityEpoch == availabilityEpoch
                        && helper.getLevel().getGameTime() == availabilityGameTime,
                "Input commit and flush retain all three callbacks and coalesce availability with setMachine in the same game time");
        var inputCapability = (FluxEnergyInputCapability) inputPort.capabilitySnapshot().capabilities().getFirst();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(inputCapability.consumeReservation(30L, transaction).success(),
                    "Real Flux input capability consumes local energy transactionally");
            helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == cachedEpoch
                            && controller.resourceAvailabilityEpoch() == cachedAvailabilityEpoch,
                    "Input consumption does not notify before root commit");
            transaction.commit();
        }
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == cachedEpoch + 2L,
                "Unreserved consumption retains immediate storage/input callbacks before flush");
        inputPort.flushStorageChanges();
        var consumed = controller.currentRuntimeSnapshot();
        helper.assertTrue(inputPort.getEnergyStorage().amount() == 70L
                        && consumed.capabilityPresentations().get(1).amount() == 70L,
                "Controller observes actual consumed input energy immediately after root commit");
        helper.assertTrue(cached.capabilityPresentations().get(1).amount() == 100L
                        && consumed.capabilityPresentations().getFirst() == itemRow
                        && consumed.capabilityPresentations().get(2) == pending.capabilityPresentations().get(2),
                "Input consumption preserves the old snapshot and reuses unrelated item/output rows");
        long consumedEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        availabilityEpoch = controller.resourceAvailabilityEpoch();
        helper.assertTrue(consumedEpoch == cachedEpoch + 2L && availabilityEpoch == cachedAvailabilityEpoch,
                "Consumption retains separate storage/input-change callbacks without an availability increase");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(inputCapability.consumeReservation(20L, transaction).success(),
                    "Input rollback exercises actual capability consumption");
        }
        inputPort.flushStorageChanges();
        helper.assertTrue(inputPort.getEnergyStorage().amount() == 70L
                        && controller.componentRuntime().capabilityPresentationEpoch() == consumedEpoch
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch
                        && controller.currentRuntimeSnapshot().capabilityPresentations() == consumed.capabilityPresentations(),
                "Input rollback restores consumed energy without extra controller notifications");

        // Inject only the deterministic network seam; keep the placed port's actual buffer and onChange callbacks.
        FluxEnergyBuffer buffer = inputBuffer(inputPort);
        TestEnergyStorage networkStorage = new TestEnergyStorage(120L);
        FluxEnergyNetwork network = new FluxEnergyNetwork(networkStorage);
        FluxEnergyInputCapability prefetchCapability = new FluxEnergyInputCapability(buffer,
                "gametest-prefetch", network::planExactExtract);
        var prefetchPlan = prefetchCapability.planPrefetch(100L).orElseThrow();
        helper.assertTrue(networkStorage.amount == 120L && buffer.reserved() == 0L,
                "Planning real prefetch does not mutate the network or reservation");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(prefetchPlan.operation().commit(transaction).success(),
                    "Real prefetch operation applies bridge, storage and reservation journals");
            helper.assertTrue(networkStorage.amount == 20L && buffer.amount() == 170L && buffer.reserved() == 100L,
                    "Open prefetch transaction has read-your-writes across all three journals");
            helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == consumedEpoch
                            && controller.resourceAvailabilityEpoch() == availabilityEpoch,
                    "Neither prefetch journal notifies before root commit");
        }
        inputPort.flushStorageChanges();
        helper.assertTrue(networkStorage.amount == 120L && buffer.amount() == 70L && buffer.reserved() == 0L
                        && buffer.idleExcess() == 0L
                        && controller.componentRuntime().capabilityPresentationEpoch() == consumedEpoch
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch
                        && controller.currentRuntimeSnapshot().capabilityPresentations() == consumed.capabilityPresentations(),
                "Prefetch rollback compensates the bridge and restores metadata/storage without notifications");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(prefetchPlan.operation().commit(transaction).success(),
                    "The rolled-back prefetch plan can be committed");
            transaction.commit();
        }
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == consumedEpoch + 4L
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch,
                "Both prefetch journals retain immediate storage/input callbacks while availability awaits flush");
        var prefetched = controller.currentRuntimeSnapshot();
        helper.assertTrue(networkStorage.amount == 20L && buffer.amount() == 170L && buffer.reserved() == 100L
                        && buffer.idleExcess() == 0L && prefetched.capabilityPresentations().get(1).amount() == 170L,
                "Committed real prefetch publishes energy and its actual reservation");
        inputPort.flushStorageChanges();
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == consumedEpoch + 5L
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch
                        && helper.getLevel().getGameTime() == availabilityGameTime,
                "Both prefetch journal callbacks and flush retain storage/input-change events plus one availability event in the same game time");
        helper.assertTrue(prefetched.capabilityPresentations().getFirst() == itemRow
                        && prefetched.capabilityPresentations().get(2) == pending.capabilityPresentations().get(2)
                        && consumed.capabilityPresentations().get(1).amount() == 70L,
                "Every prefetch callback is source-local and leaves published history isolated");
        long prefetchedEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(inputCapability.consumeReservation(30L, transaction).success(),
                    "The actual port capability consumes genuinely reserved prefetched energy");
            helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == prefetchedEpoch,
                    "Reserved consumption journals do not notify before commit");
            transaction.commit();
        }
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == prefetchedEpoch + 4L,
                "Reserved consumption retains both immediate storage/input journal callbacks before flush");
        inputPort.flushStorageChanges();
        var reservedConsumed = controller.currentRuntimeSnapshot();
        helper.assertTrue(buffer.amount() == 140L && buffer.reserved() == 70L && networkStorage.amount == 20L
                        && reservedConsumed.capabilityPresentations().get(1).amount() == 140L,
                "Reserved consumption commits storage and reservation metadata together");
        helper.assertTrue(controller.componentRuntime().capabilityPresentationEpoch() == prefetchedEpoch + 4L
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch,
                "Reserved consumption preserves both distinct storage/input-change journal callbacks");
        helper.assertTrue(reservedConsumed.capabilityPresentations().getFirst() == itemRow
                        && reservedConsumed.capabilityPresentations().get(2) == pending.capabilityPresentations().get(2)
                        && prefetched.capabilityPresentations().get(1).amount() == 170L,
                "Reserved consumption remains source-local with immutable previous rows");
        long reservedConsumedEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(inputCapability.consumeReservation(20L, transaction).success(),
                    "Reserved consumption rollback exercises both journals");
        }
        inputPort.flushStorageChanges();
        helper.assertTrue(buffer.amount() == 140L && buffer.reserved() == 70L && buffer.idleExcess() == 0L
                        && networkStorage.amount == 20L
                        && controller.componentRuntime().capabilityPresentationEpoch() == reservedConsumedEpoch
                        && controller.resourceAvailabilityEpoch() == availabilityEpoch
                        && controller.currentRuntimeSnapshot().capabilityPresentations() == reservedConsumed.capabilityPresentations(),
                "Reserved consumption rollback restores metadata/storage and emits no callbacks");
    }

    private static FluxEnergyBuffer inputBuffer(FluxEnergyInputInterfaceBlockEntity port) {
        try {
            var field = FluxEnergyInputInterfaceBlockEntity.class.getDeclaredField("buffer");
            field.setAccessible(true);
            return (FluxEnergyBuffer) field.get(port);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to obtain the actual placed Flux input buffer", exception);
        }
    }

    /** Deterministic storage behind the production compensating network adapter.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestEnergyStorage implements FluxEnergyNetwork.EnergyStorage {
        private long amount;

        private TestEnergyStorage(long amount) {
            this.amount = amount;
        }

        @Override
        public long extract(long requestedAmount, boolean simulate) {
            long extracted = Math.min(requestedAmount, amount);
            if (!simulate) amount -= extracted;
            return extracted;
        }

        @Override
        public long insert(long requestedAmount, boolean simulate) {
            if (!simulate) amount += requestedAmount;
            return requestedAmount;
        }
    }

    private static void assertBridgesAvailable() {
        if (!AE2Bridge.get().available()) {
            throw new IllegalStateException("AE2 must be loaded for the AppFlux GameTest integration");
        }
        if (!AppliedFluxBridge.get().available()) {
            throw new IllegalStateException("AppFlux must be loaded for the AppFlux GameTest integration");
        }
    }

    private static void assertHasNoTransferFacet(BlockEntity entity) {
        if (!(entity instanceof CapabilityHost host)) {
            throw new IllegalStateException("Block entity is not a capability host: " + entity);
        }
        var snapshot = host.capabilitySnapshot();
        for (MachineCapability capability : snapshot.capabilities()) {
            if (capability.facet(TransferFacet.class).isPresent()) {
                throw new IllegalStateException(
                        "AppFlux capability must not expose TransferFacet: " + capability.getClass());
            }
        }
    }

    /**
     * Tick-dependent or asynchronous behaviours that must not block the GameTest server
     * run because they rely on AE2 ticker wake-ups or the output budget refresh cycle.
     * Stored so they can be enabled once a deterministic harness is available.
     */
    public static java.util.List<String> pendingGameTests() {
        return java.util.List.of(
                "appflux_me_flux_output_ticker_sends_to_active_grid",
                "appflux_me_flux_input_idle_excess_returns_only_after_soft_limit",
                "appflux_me_flux_output_disconnect_preserves_pending");
    }
}
