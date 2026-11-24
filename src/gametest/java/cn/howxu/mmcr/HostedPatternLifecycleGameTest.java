package cn.howxu.mmcr;

import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** @author HowXu {@literal <dev@howxu.cn>} */
public final class HostedPatternLifecycleGameTest {
    public static void reloadPaidAndUnpaidOwners(GameTestHelper helper) {
        var f = fixture(helper, "offline_reload");
        f.ready().thenExecute(() -> {
            payTwo(helper, f, 3);
            var access = helper.getLevel().registryAccess();
            var oldHost = f.host;
            var oldController = f.controller;
            var savedHost = oldHost.saveWithFullMetadata(access);
            var savedController = oldController.saveWithFullMetadata(access);
            oldController.onChunkUnloaded();
            oldHost.onChunkUnloaded();
            helper.getLevel().removeBlockEntity(oldHost.getBlockPos());
            helper.getLevel().removeBlockEntity(oldController.getBlockPos());
            f.host = (PatternInterfaceBlockEntity) oldHost.getType().create(oldHost.getBlockPos(), oldHost.getBlockState());
            f.controller = (MachineControllerBlockEntity) oldController.getType().create(oldController.getBlockPos(), oldController.getBlockState());
            f.host.setLevel(helper.getLevel());
            f.controller.setLevel(helper.getLevel());
            f.controller.setMachine(f.machine);
            f.host.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, access, savedHost));
            f.controller.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, access, savedController));
            helper.getLevel().setBlockEntity(f.host);
            helper.getLevel().setBlockEntity(f.controller);
            f.controller.setStructureCheckIntervalForTesting(1);
            f.controller.requestImmediateStructureCheck();
        }).thenWaitUntil(() -> helper.assertTrue(f.host.getMainNode().getNode() != null && !f.host.hostedOnline()
                && f.returnAmount(f.output) == 2 && f.host.hostedPatterns().groups().getFirst().materials().get(f.input) == 1,
                "Paid threads finish offline; the remaining iron is not spent or refunded"))
                .thenExecute(() -> {
                    var next = new KeyCounter();
                    next.add(f.input, 1);
                    helper.assertTrue(!f.host.craftingMachine().pushPattern(f.host.getLogic().getAvailablePatterns().getFirst(),
                            new KeyCounter[]{next}, null) && next.get(f.input) == 1, "Offline delivery stays with the sender");
                    f.reconnectHost();
                }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.output) == 3 && f.stored(f.input) == f.total
                        && f.host.hostedPatterns().isEmpty() && f.host.getLogic().getReturnInv().isEmpty(),
                        "Reconnection runs only the unpaid remainder and returns all three outputs once")).thenSucceed();
    }

    public static void dismantle(GameTestHelper helper, boolean online) {
        var f = fixture(helper, online ? "break_online" : "break_offline");
        var bus = helper.getBlockEntity(NativePatternCpuFixture.BUS, ItemInputBusBlockEntity.class);
        var sequence = f.ready().thenExecute(() -> {
            try (var tx = Transaction.openRoot()) {
                bus.itemStorage().insert(0, ItemResource.of(Items.REDSTONE), 3, tx);
                tx.commit();
            }
            payTwo(helper, f, 7);
            f.storage.insertionLimit = f.total + 2;
            if (!online) f.disconnectHost();
        });
        if (!online) sequence.thenWaitUntil(() -> helper.assertTrue(!f.host.hostedOnline(), "Interface goes offline"));
        sequence.thenExecute(() -> {
            helper.setBlock(NativePatternCpuFixture.PORT, Blocks.AIR.defaultBlockState());
            f.host.dropContents(); // Multiple removal paths must not refund a second time.
            helper.assertTrue(f.host.hostedPatterns().isEmpty() && f.stored(f.input) == f.total + (online ? 2 : 0)
                    && bus.itemStorage().amount(0) == 3, "Removal disposes only unpaid escrow and debits no ordinary input");
        }).thenSucceed();
    }

    private static NativePatternCpuFixture fixture(GameTestHelper helper, String name) {
        var f = new NativePatternCpuFixture(helper, name, false, 1, 2, ParallelTier.NORMAL);
        helper.getBlockEntity(new BlockPos(0, 0, 1), ParallelControllerBlockEntity.class).setCurrentParallelism(1);
        return f;
    }

    private static void payTwo(GameTestHelper helper, NativePatternCpuFixture f, long amount) {
        var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
        var input = new KeyCounter();
        input.add(f.input, amount);
        helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{input}) && input.isEmpty(), "Accept the actual delivery");
        f.host.dispatchHostedPatterns();
        helper.assertTrue(f.controller.runtimeSnapshot().factory().activeLaneCount() == 2
                        && f.host.hostedPatterns().group(pattern.getDefinition()).materials().get(f.input) == amount - 2,
                "The two running lanes have paid for one unit each; remaining inputs still belong to the interface");
        input.add(f.input, 1);
        helper.assertTrue(!f.host.getLogic().pushPattern(pattern, new KeyCounter[]{input}) && input.get(f.input) == 1,
                "Recipe thread quota stops new deliveries, not existing paid threads");
    }
}
