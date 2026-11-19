package cn.howxu.mmcr;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageProvider;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.OutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Set;

import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;

/** Real AE2 grids and native key persistence, without exact-tick assertions.
 * @author howxu <dev@howxu.cn> */
public class LightningInterfaceGameTest {
    public void stockingRebindWatcherAndTransaction(GameTestHelper helper) {
        var family = LoadedLightningBridge.FAMILY;
        BlockPos portPos = BlockPos.ZERO;
        BlockPos energyPos = new BlockPos(3, 0, 0);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("ae2_me_stocking_input_interface").get().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var port = helper.getBlockEntity(portPos, StockingInterfaceBlockEntity.class);
        var energy = helper.getBlockEntity(energyPos, CreativeEnergyCellBlockEntity.class);
        var high = family.adapter().toKey(HIGH_VOLTAGE);
        var extreme = family.adapter().toKey(EXTREME_HIGH_VOLTAGE);
        var view = port.resourceStorage(family);
        var backing = new GenericStackInv(Set.of(family.adapter().keyType()), null, GenericStackInv.Mode.STORAGE, 2);
        backing.setStack(0, new GenericStack(high, 150L));
        backing.setStack(1, new GenericStack(extreme, 250L));
        IStorageProvider provider = mounts -> mounts.mount(backing, 0);
        energy.getMainNode().addService(IStorageProvider.class, provider);
        IGridConnection[] connection = new IGridConnection[1];
        port.getInterfaceLogic().getConfig().setStack(0, new GenericStack(high, 7L));
        port.getInterfaceLogic().getConfig().setStack(1, new GenericStack(extreme, 1L));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(port.getMainNode().getNode() != null
                        && energy.getMainNode().getNode() != null, "AE2 nodes initialize"))
                .thenExecute(() -> {
                    connection[0] = GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode());
                }).thenWaitUntil(() -> helper.assertTrue(port.getMainNode().isActive()
                        && view.amount(0) == 150L && view.amount(1) == 250L, "Both tiers become visible through real watcher updates"))
                .thenExecute(() -> {
                    helper.assertTrue(view == port.resourceStorage(family), "Capability storage wrappers remain cached");
                    helper.assertTrue(port.getInterfaceLogic().getStorage().getAmount(1) == 250L,
                            "The native UI mirror includes lightning");
                    try (var transaction = Transaction.openRoot()) {
                        helper.assertTrue(view.extract(0, HIGH_VOLTAGE, 3L, transaction) == 3L, "Lightning extraction stages");
                    }
                    helper.assertTrue(backing.getAmount(0) == 150L, "Rollback never consumes network lightning");
                    try (var transaction = Transaction.openRoot()) {
                        helper.assertTrue(view.extract(0, HIGH_VOLTAGE, 3L, transaction) == 3L, "Extraction can be retried");
                        transaction.commit();
                    }
                }).thenWaitUntil(() -> helper.assertTrue(view.amount(0) == 147L && view.amount(1) == 250L,
                        "Committed extraction updates only its own tier"))
                .thenExecute(() -> connection[0].destroy())
                .thenWaitUntil(() -> helper.assertTrue(view.amount(0) == 0L && view.amount(1) == 0L,
                        "Disconnect clears both cached amounts"))
                .thenExecute(() -> connection[0] = GridHelper.createConnection(
                        port.getMainNode().getNode(), energy.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(view.amount(0) == 147L && view.amount(1) == 250L,
                        "The same cached view rebinds to the reconnected grid"))
                .thenExecute(() -> {
                    var lightning = port.capabilitySnapshot().capabilities().stream()
                            .filter(capability -> capability.type().id().equals(LightningRecipeTypes.LIGHTNING)).findFirst().orElseThrow();
                    helper.assertTrue(lightning.facet(TransferFacet.class).isEmpty(), "Lightning exposes no adjacent transfer facet");
                }).thenSucceed();
    }

    public void outputBudgetAndAsyncLifecycle(GameTestHelper helper) {
        var family = LoadedLightningBridge.FAMILY;
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 0, 0), ModBlocks.BLOCKS.get("ae2_me_async_output_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(4, 0, 0), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var output = helper.getBlockEntity(BlockPos.ZERO, OutputInterfaceBlockEntity.class);
        var async = helper.getBlockEntity(new BlockPos(2, 0, 0), AsyncOutputInterfaceBlockEntity.class);
        var energy = helper.getBlockEntity(new BlockPos(4, 0, 0), CreativeEnergyCellBlockEntity.class);
        var backing = new GenericStackInv(Set.of(AEKeyType.items(), AEKeyType.fluids(), family.adapter().keyType()),
                null, GenericStackInv.Mode.STORAGE, 5);
        IStorageProvider provider = mounts -> mounts.mount(backing, 0);
        energy.getMainNode().addService(IStorageProvider.class, provider);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(output.getMainNode().getNode() != null
                        && async.getMainNode().getNode() != null && energy.getMainNode().getNode() != null, "AE2 nodes initialize"))
                .thenExecute(() -> {
                    GridHelper.createConnection(output.getMainNode().getNode(), energy.getMainNode().getNode());
                    GridHelper.createConnection(async.getMainNode().getNode(), energy.getMainNode().getNode());
                }).thenWaitUntil(() -> helper.assertTrue(output.getMainNode().isActive() && async.getMainNode().isActive(), "Output grid becomes active"))
                .thenExecute(() -> {
                    var iron = AEItemKey.of(Items.IRON_INGOT);
                    var water = AEFluidKey.of(Fluids.WATER);
                    var high = family.adapter().toKey(HIGH_VOLTAGE);
                    output.getStorage().setStack(0, new GenericStack(iron, 64L));
                    output.getStorage().setStack(1, new GenericStack(water, 192L));
                    output.getStorage().setStack(2, new GenericStack(high, 50L));
                    IGridTickable ticker = output.outputTicker;
                    ticker.tickingRequest(output.getMainNode().getNode(), 1);
                    var received = backing.getAvailableStacks();
                    helper.assertTrue(received.get(iron) == 64L && received.get(water) == 192L && received.get(high) == 0L,
                            "All families share one 256-unit flush budget");
                    ticker.tickingRequest(output.getMainNode().getNode(), 1);
                    helper.assertTrue(backing.getAvailableStacks().get(high) == 50L && output.getStorage().isEmpty(),
                            "The next bounded flush drains the remaining lightning");
                    var view = async.resourceStorage(family);
                    helper.assertTrue(view == async.resourceStorage(family), "Async getter never creates a new queue view");
                    var plan = view.planOutput(EXTREME_HIGH_VOLTAGE, 11L, new PlanningReservations(), true);
                    helper.assertTrue(plan.accepted() == 11L, "Async output reserves real grid capacity");
                    try (var transaction = Transaction.openRoot()) {
                        helper.assertTrue(plan.operation().commit(transaction).success(), "Async admission commits");
                        transaction.commit();
                    }
                    var asyncTicker = async.getMainNode().getNode().getService(IGridTickable.class);
                    asyncTicker.tickingRequest(async.getMainNode().getNode(), 1);
                    helper.assertTrue(backing.getAvailableStacks().get(family.adapter().toKey(EXTREME_HIGH_VOLTAGE)) == 11L,
                            "One existing async service transports the second tier");
                    plan = view.planOutput(EXTREME_HIGH_VOLTAGE, 19L, new PlanningReservations(), true);
                    helper.assertTrue(plan.accepted() == 19L && plan.operation() != null,
                            "The backing inventory has capacity for a second admission before unload");
                    try (var transaction = Transaction.openRoot()) {
                        helper.assertTrue(plan.operation().commit(transaction).success(), "Second async admission commits");
                        transaction.commit();
                    }
                    async.onChunkUnloaded();
                    helper.assertTrue(view.outputCapacity(EXTREME_HIGH_VOLTAGE) == 0L && async.getStorage().isEmpty(),
                            "Unload has no persistent buffer or offline fallback");
                }).thenSucceed();
    }

    public void localInterfacesPersistNativeKeys(GameTestHelper helper) {
        var family = LoadedLightningBridge.FAMILY;
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 0, 0), ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());
        var input = helper.getBlockEntity(BlockPos.ZERO, InputInterfaceBlockEntity.class);
        var output = helper.getBlockEntity(new BlockPos(2, 0, 0), OutputInterfaceBlockEntity.class);
        var inputView = input.resourceStorage(family);
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(inputView.insert(0, HIGH_VOLTAGE, 123L, transaction) == 123L
                    && inputView.insert(1, EXTREME_HIGH_VOLTAGE, 456L, transaction) == 456L, "Local input accepts both native keys");
            transaction.commit();
        }
        var outputView = output.resourceStorage(family);
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(outputView.insert(0, HIGH_VOLTAGE, 321L, transaction) == 321L
                    && outputView.insert(1, EXTREME_HIGH_VOLTAGE, 654L, transaction) == 654L, "Offline normal output uses its local cache");
            transaction.commit();
        }
        var slots = helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV, input.getBlockPos(), null);
        var me = helper.getLevel().getCapability(AECapabilities.ME_STORAGE, input.getBlockPos(), null);
        var high = family.adapter().toKey(HIGH_VOLTAGE);
        slots.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
        helper.assertTrue(slots.getStack(0) == null && slots.insert(2, high, 1, Actionable.MODULATE) == 0
                        && me.insert(high, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                        && me.extract(high, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                        && me.getAvailableStacks().isEmpty() && inputView.amount(0) == 123,
                "Lightning is usable by the machine but cannot be listed, replaced or transferred externally");
        var inputSaved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        var outputSaved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        input.saveAdditional(inputSaved);
        output.saveAdditional(outputSaved);
        var restoredInput = InputInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(4, 0, 0)), input.getBlockState());
        var restoredOutput = OutputInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(6, 0, 0)), output.getBlockState());
        restoredInput.setLevel(helper.getLevel());
        restoredOutput.setLevel(helper.getLevel());
        restoredInput.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), inputSaved.buildResult()));
        restoredOutput.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), outputSaved.buildResult()));
        helper.assertTrue(restoredInput.resourceStorage(family).amount(0) == 123L
                        && restoredInput.resourceStorage(family).resource(1) == EXTREME_HIGH_VOLTAGE
                        && restoredInput.resourceStorage(family).amount(1) == 456L, "Native input NBT retains both tiers and balances");
        helper.assertTrue(restoredOutput.resourceStorage(family).amount(0) == 321L
                        && restoredOutput.resourceStorage(family).resource(1) == EXTREME_HIGH_VOLTAGE
                        && restoredOutput.resourceStorage(family).amount(1) == 654L, "Normal output NBT uses the existing save path");
        helper.succeed();
    }
}
