package cn.howxu.mmcr;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.compat.ae2lt.LightningViewFacet;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirementHandler;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.compat.ae2lt.loaded.OutputLightningStorage;
import cn.howxu.mmcr.compat.ae2lt.loaded.InventoryLightningStorage;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import java.util.Set;
import java.util.List;
import cn.howxu.mmcr.util.IOType;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;

/** Actual native inventories, ME grid lifecycle and long request quotas; no exact-tick assertions.
 * @author howxu <dev@howxu.cn> */
public final class LightningInterfaceGameTest {
    public void localKeysPersistAndReservationsSharePhysicalSlots(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity input = helper.getBlockEntity(BlockPos.ZERO);
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        input.getStorage().setStack(0, new GenericStack(high, 123L));
        input.getStorage().setStack(1, new GenericStack(extreme, 456L));
        var capability = input.capabilitySnapshot().capabilities().stream()
                .filter(value -> value.type().id().equals(LightningRecipeTypes.LIGHTNING)).findFirst().orElseThrow();
        var query = capability.facet(LightningViewFacet.class).orElseThrow();
        helper.assertTrue(query.storage().amount(0) == 123L && query.storage().amount(1) == 456L,
                "Both native lightning keys are visible through the internal resource view");
        helper.assertTrue(capability.facet(TransferFacet.class).isEmpty(), "ME lightning has no adjacent transfer facet");
        CompoundTag saved = input.saveWithoutMetadata(helper.getLevel().registryAccess());
        input.getStorage().clear();
        input.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(query.storage().amount(0) == 123L && query.storage().amount(1) == 456L,
                "Existing native interface NBT preserves both lightning tiers");

        var inventory = new GenericStackInv(null, GenericStackInv.Mode.STORAGE, 1);
        var reservations = new PlanningReservations();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        helper.assertTrue(reservations.reserveNativeInsert(inventory, 0, iron, null, 0L, 64L, 1L), "Item projection reserves its real slot");
        var lightning = new InventoryLightningStorage(inventory);
        helper.assertTrue(lightning.reserveOutput(HIGH_VOLTAGE, 1L, reservations) == 0L,
                "Lightning cannot reuse an empty physical slot already reserved by another family");

        var holders = new KeyCounter();
        holders.add(high, 8_589_934_588L);
        var owner = AE2NativeAdapters.requestInventory(new KeyCounter[]{holders});
        var views = new InventoryLightningStorage[]{
                new InventoryLightningStorage(AE2NativeAdapters.requestInventoryView(owner, 1L, 2L)),
                new InventoryLightningStorage(AE2NativeAdapters.requestInventoryView(owner, 1L, 2L))};
        for (var view : views) {
            helper.assertTrue(view.extract(0, HIGH_VOLTAGE, 2_147_483_647L, true) == 2_147_483_647L,
                    "Native request simulation leaves the batch owner untouched");
            helper.assertTrue(view.extract(0, HIGH_VOLTAGE, 2_147_483_647L, false) == 2_147_483_647L,
                    "Each accepted lane debits only its quota from the single material owner");
        }
        helper.assertTrue(owner.getAmount(0) == 4_294_967_294L && holders.get(high) == 8_589_934_588L,
                "Long remainders are exact and original holders remain unclaimed until batch acceptance");
        for (var tier : List.of(HIGH_VOLTAGE, EXTREME_HIGH_VOLTAGE)) {
            var key = LoadedLightningBridge.key(tier);
            var network = new GenericStackInv(Set.of(key.getType()), null, GenericStackInv.Mode.STORAGE, 1) {
                @Override public long getMaxAmount(AEKey what) { return 100L; }
            };
            network.setStack(0, new GenericStack(key, 10L));
            var output = new OutputLightningStorage(new GenericStackInv(null, GenericStackInv.Mode.STORAGE, 0),
                    () -> network, IActionSource.empty(), () -> {}, false);
            var outputCapability = new LightningCapability(output, IOType.OUTPUT);
            var plan = new LightningRequirementHandler().plan(LightningRequirement.output(tier, 20L, 1F),
                    List.of(outputCapability), new PlanningContext(1L, 0));
            var prepared = plan.materialize(1L, new PlanningReservations(), null);
            helper.assertTrue(prepared.successful() && network.getAmount(0) == 10L,
                    "A nonempty single-slot network admits output without an overflowing unlimited probe");
            helper.assertTrue(prepared.operations().getFirst().commit().success() && network.getAmount(0) == 30L,
                    "The admitted lightning output reaches the existing native stack");
            var shared = new PlanningReservations();
            helper.assertTrue(output.reserveOutput(tier, 20L, shared) == 20L
                            && output.reserveOutput(tier, 60L, shared) == 50L
                            && output.reserveOutput(tier, 1L, shared) == 0L && network.getAmount(0) == 30L,
                    "Bounded cumulative probes share the remaining network capacity without mutating it");
        }
        helper.succeed();
    }

    public void stockingReconnectsAndPreservesTierIdentity(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_stocking_input_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 0, 0), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        StockingInterfaceBlockEntity port = helper.getBlockEntity(BlockPos.ZERO);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(new BlockPos(3, 0, 0));
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        var backing = new GenericStackInv(Set.of(high.getType()), null, GenericStackInv.Mode.STORAGE, 2);
        backing.setStack(0, new GenericStack(high, 150L));
        backing.setStack(1, new GenericStack(extreme, 250L));
        energy.getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(backing, 0));
        port.getInterfaceLogic().getConfig().setStack(0, new GenericStack(high, 1L));
        port.getInterfaceLogic().getConfig().setStack(1, new GenericStack(extreme, 1L));
        var storage = LoadedLightningBridge.storage(port);
        IGridConnection[] connection = new IGridConnection[1];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(port.getMainNode().getNode() != null
                        && energy.getMainNode().getNode() != null, "AE2 nodes initialize"))
                .thenExecute(() -> connection[0] = GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(port.getMainNode().isActive() && storage.amount(0) == 150L
                        && port.getStorage().getAmount(1) == 250L, "Real ME watcher mirrors both tiers"))
                .thenExecute(() -> {
                    helper.assertTrue(storage.extract(0, HIGH_VOLTAGE, 3L, true) == 3L && backing.getAmount(0) == 150L,
                            "Network simulation does not consume resources");
                    helper.assertTrue(storage.extract(0, HIGH_VOLTAGE, 3L, false) == 3L, "Native extraction consumes the selected tier");
                }).thenWaitUntil(() -> helper.assertTrue(storage.amount(0) == 147L && storage.amount(1) == 250L,
                        "Only the selected tier changes"))
                .thenExecute(() -> connection[0].destroy())
                .thenWaitUntil(() -> helper.assertTrue(storage.amount(0) == 0L && storage.amount(1) == 0L, "Disconnect clears the live view"))
                .thenExecute(() -> connection[0] = GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(storage.amount(0) == 147L && storage.amount(1) == 250L, "The same view follows the reconnected grid"))
                .thenSucceed();
    }

    public void outputFlushSharesBudgetAndAsyncHasNoOfflineBuffer(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 0, 0), ModBlocks.BLOCKS.get("ae2_me_async_output_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(4, 0, 0), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        OutputInterfaceBlockEntity output = helper.getBlockEntity(BlockPos.ZERO);
        AsyncOutputInterfaceBlockEntity async = helper.getBlockEntity(new BlockPos(2, 0, 0));
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(new BlockPos(4, 0, 0));
        AEKey high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        AEKey extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        AEKey iron = AEItemKey.of(Items.IRON_INGOT);
        var backing = new GenericStackInv(Set.of(high.getType(), iron.getType()), null, GenericStackInv.Mode.STORAGE, 6);
        energy.getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(backing, 0));
        var asyncStorage = LoadedLightningBridge.storage(async);
        helper.assertTrue(asyncStorage.insert(EXTREME_HIGH_VOLTAGE, 19L, false) == 0L && async.getStorage().isEmpty(),
                "Offline async output cannot accumulate a persistent buffer");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(output.getMainNode().getNode() != null
                        && async.getMainNode().getNode() != null && energy.getMainNode().getNode() != null, "Output nodes initialize"))
                .thenExecute(() -> {
                    GridHelper.createConnection(output.getMainNode().getNode(), energy.getMainNode().getNode());
                    GridHelper.createConnection(async.getMainNode().getNode(), energy.getMainNode().getNode());
                }).thenWaitUntil(() -> helper.assertTrue(output.getMainNode().isActive() && async.getMainNode().isActive(), "Output grid is active"))
                .thenExecute(() -> {
                    for (int slot = 0; slot < 4; slot++) output.getStorage().setStack(slot, new GenericStack(iron, 64L));
                    output.getStorage().setStack(4, new GenericStack(high, 50L));
                    IGridTickable ticker = output.outputTicker;
                    ticker.tickingRequest(output.getMainNode().getNode(), 1);
                    helper.assertTrue(backing.getAvailableStacks().get(iron) == 256L && backing.getAvailableStacks().get(high) == 0L,
                            "Item and lightning share one bounded flush budget");
                    ticker.tickingRequest(output.getMainNode().getNode(), 1);
                    helper.assertTrue(backing.getAvailableStacks().get(high) == 50L && output.getStorage().isEmpty(), "Next bounded flush drains lightning");
                    helper.assertTrue(asyncStorage.insert(EXTREME_HIGH_VOLTAGE, 11L, false) == 11L
                            && backing.getAvailableStacks().get(extreme) == 11L && async.getStorage().isEmpty(),
                            "Async output inserts directly into the native grid without another queue");
                    async.onChunkUnloaded();
                    helper.assertTrue(asyncStorage.reserveOutput(EXTREME_HIGH_VOLTAGE, 1L, new PlanningReservations()) == 0L,
                            "Unloaded async output admits no new lightning");
                }).thenSucceed();
    }

    public void patternReturnReservationsKeepExactLongBudgets(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var inventory = host.getLogic().getReturnInv();
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        inventory.setStack(inventory.size() - 1, new GenericStack(high, Long.MAX_VALUE - 3L));
        var storage = new InventoryLightningStorage(inventory);
        var reservations = new PlanningReservations();
        helper.assertTrue(storage.reserveOutput(HIGH_VOLTAGE, 5L, reservations) == 3L
                        && storage.reserveOutput(HIGH_VOLTAGE, 1L, reservations) == 0L,
                "Repeated reservations share the native per-key long return budget");
        helper.assertTrue(storage.insert(HIGH_VOLTAGE, 5L, true) == 3L
                        && inventory.getAvailableStacks().get(high) == Long.MAX_VALUE - 3L,
                "Simulated insertion honors the same global key budget without modifying balances");
        helper.assertTrue(storage.insert(HIGH_VOLTAGE, 5L, false) == 3L
                        && inventory.getAvailableStacks().get(high) == Long.MAX_VALUE,
                "Committed insertion never overflows the existing long return accounting");
        helper.assertTrue(storage.insert(EXTREME_HIGH_VOLTAGE, 7L, false) == 7L,
                "The other lightning tier keeps an independent key budget");
        helper.succeed();
    }
}
