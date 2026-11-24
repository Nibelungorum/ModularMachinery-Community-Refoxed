package cn.howxu.mmcr;

import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

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
            f.host.loadWithComponents(savedHost, access);
            f.controller.loadWithComponents(savedController, access);
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

    public static void refundUnpaidWhileRunning(GameTestHelper helper) {
        var f = fixture(helper, "refund_running");
        f.ready().thenExecute(() -> {
            payTwo(helper, f, 102);
            f.storage.insertionLimit = f.total + 60;
            f.host.markLocalHostedRefund();
            f.host.tryLocalHostedRefund();
            f.host.markLocalHostedRefund();
            f.host.tryLocalHostedRefund();
            var group = f.host.hostedPatterns().groups().getFirst();
            helper.assertTrue(f.stored(f.input) == f.total + 60 && group.materials().get(f.input) == 40
                            && !f.host.hostedPatterns().canAccept(group.definition()),
                    "Only the sixty accepted iron return; retry cannot spend the forty remaining");
        }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.output) == 2
                        && f.host.hostedPatterns().groups().getFirst().materials().get(f.input) == 40,
                "Paid lanes finish but unpaid refund-only iron never starts"))
                .thenExecute(() -> f.storage.insertionLimit = Long.MAX_VALUE)
                .thenWaitUntil(() -> helper.assertTrue(f.host.hostedPatterns().isEmpty() && f.stored(f.input) == f.total + 100
                                && !f.host.getLogic().getAvailablePatterns().isEmpty() && f.stored(f.output) == 2,
                        "Space becoming available returns the rest without removing the pattern")).thenSucceed();
    }

    public static void dismantle(GameTestHelper helper, boolean online) {
        var f = fixture(helper, online ? "break_online" : "break_offline");
        ItemInputBusBlockEntity bus = helper.getBlockEntity(NativePatternCpuFixture.BUS);
        var sequence = f.ready().thenExecute(() -> {
            bus.itemHandler().insertItem(0, new ItemStack(Items.REDSTONE, 3), false);
            payTwo(helper, f, 7);
            f.storage.insertionLimit = f.total + 2;
            if (!online) f.disconnectHost();
        });
        if (!online) sequence.thenWaitUntil(() -> helper.assertTrue(!f.host.hostedOnline(), "Interface goes offline"));
        sequence.thenExecute(() -> {
            helper.setBlock(NativePatternCpuFixture.PORT, Blocks.AIR.defaultBlockState());
            f.host.dropContents(); // Multiple removal paths must not refund a second time.
            helper.assertTrue(f.host.hostedPatterns().isEmpty() && f.stored(f.input) == f.total + (online ? 2 : 0)
                    && bus.nativeItemHandler().amount(0) == 3, "Removal disposes only unpaid escrow and debits no ordinary input");
        }).thenSucceed();
    }

    private static NativePatternCpuFixture fixture(GameTestHelper helper, String name) {
        var f = new NativePatternCpuFixture(helper, name, false, 1, 2, ParallelTier.NORMAL);
        helper.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(1);
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
