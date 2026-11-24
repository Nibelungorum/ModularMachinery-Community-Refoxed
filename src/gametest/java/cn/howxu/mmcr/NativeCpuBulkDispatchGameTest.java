package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

/** @author HowXu {@literal <dev@howxu.cn>} */
public final class NativeCpuBulkDispatchGameTest {
    public static void oneDispatch(GameTestHelper helper) {
        var f = fixture(helper, "native_bulk");
        f.calculate().thenWaitUntil(() -> assertLaneReady(helper, f)).thenExecute(() -> {
            helper.assertTrue(f.plan.patternTimes().containsValue(512L) && f.cluster().getCoProcessors() == 0
                    && f.host.getMainNode().getNode().getService(ICraftingProvider.class) == f.host.getLogic(),
                    "512 native tasks, no co-processors or proxy provider");
            f.submit();
            var revision = f.host.hostedPatterns().revision();
            double[] budget = {512};
            f.cpu().setJobSuspended(false);
            f.cpu().tickCraftingLogic(meter(f, budget), (CraftingService) f.grid().getCraftingService());
            helper.assertTrue(lastDispatches(f.cpu()) == 1 && budget[0] == 0 && f.host.hostedPatterns().revision() == revision + 1
                    && f.host.hostedPatterns().groups().getFirst().materials().get(f.input) == 512L
                    && f.cpu().getStored(f.input) == 0 && f.cpu().getPendingOutputs(f.output) == 0
                    && f.cpu().getWaitingFor(f.output) == 512,
                    "One operation transfers 512 iron in one accepted push, charges 512 AE and waits for 512 gold");
        }).thenWaitUntil(f::assertSettled).thenSucceed();
    }

    public static void limitedPowerAndCancel(GameTestHelper helper) {
        var f = fixture(helper, "native_bulk_power");
        f.calculate().thenWaitUntil(() -> assertLaneReady(helper, f)).thenExecute(() -> {
            f.submit();
            double[] budget = {1};
            var service = (CraftingService) f.grid().getCraftingService();
            var power = meter(f, budget);
            helper.assertTrue(f.cpu().executeCrafting(1, service, power, helper.getLevel()) == 0 && budget[0] == 1
                    && f.host.hostedPatterns().isEmpty() && f.cpu().getStored(f.input) == 512,
                    "Do not strand a lone funded iron behind the first-delivery gate");
            budget[0] = 31;
            int used = f.cpu().executeCrafting(1, service, power, helper.getLevel());
            helper.assertTrue(used == 1 && budget[0] == 1 && f.cpu().getWaitingFor(f.output) == 30
                    && f.cpu().getPendingOutputs(f.output) == 482 && f.cpu().getStored(f.input) == 482
                    && f.host.hostedPatterns().groups().getFirst().materials().get(f.input) == 30L,
                    "Only fifteen payable pairs fit: the CPU keeps 482 iron and the unspent AE");
            var access = helper.getLevel().registryAccess();
            var saved = new CompoundTag();
            f.cpu().writeToNBT(saved, access);
            f.cpu().readFromNBT(saved, access);
            helper.assertTrue(f.cpu().getWaitingFor(f.output) == 30 && f.cpu().getPendingOutputs(f.output) == 482
                    && f.cpu().getStored(f.input) == 482, "Native job reload preserves the paid dispatch exactly once");
            f.cpu().cancel();
            helper.assertTrue(!f.cpu().hasJob() && f.stored(f.input) == 482
                    && f.host.hostedPatterns().groups().getFirst().materials().get(f.input) == 30L,
                    "Cancel returns only the undispatched iron; escrow does not belong to the CPU");
        }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.input) == 482 && f.stored(f.output) == 30
                && f.host.hostedPatterns().isEmpty(), "All 30 uncancelled machine outputs reach storage")).thenSucceed();
    }

    public static void rejectedBatchFallsBack(GameTestHelper helper) {
        var f = fixture(helper, "native_bulk_fallback");
        var pos = new BlockPos(2, 1, 2);
        var barrel = pos.south();
        helper.setBlock(pos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(barrel, Blocks.BARREL);
        PatternProviderBlockEntity plain = helper.getBlockEntity(pos);
        var service = plain.getLogic();
        f.calculate().thenExecute(() -> {
            GridHelper.createConnection(plain.getMainNode().getNode(), f.energy.getMainNode().getNode());
            service.getPatternInv().setItemDirect(0, f.host.getLogic().getPatternInv().getStackInSlot(0).copy());
            service.updatePatterns();
        }).thenWaitUntil(() -> {
            assertLaneReady(helper, f);
            var candidates = ((CraftingService) f.grid().getCraftingService()).getProviders(f.plan.patternTimes().keySet().iterator().next());
            // AE cycles the iterator. Leave MMCR at the head of the next complete traversal.
            var it = candidates.iterator();
            helper.assertTrue(it.hasNext() && it.next() == f.host.getLogic() && it.hasNext() && it.next() == service,
                    "Both real node services advertise the original pattern");
        }).thenExecute(() -> {
            f.submit();
            var config = f.host.getLogic().getConfigManager();
            // Lock *after* the quote, when the extra 511 iron have actually left the CPU.
            Consumer<AEKey> lock = key -> {
                if (key.equals(f.input) && f.cpu().getStored(key) == 0) {
                    config.putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW);
                }
            };
            f.cpu().addListener(lock);
            double[] budget = {512};
            try {
                int used = f.cpu().executeCrafting(1, (CraftingService) f.grid().getCraftingService(), meter(f, budget), helper.getLevel());
                helper.assertTrue(used == 1 && f.host.getLogic().getCraftingLockedReason() != LockCraftingMode.NONE
                        && f.host.hostedPatterns().isEmpty() && budget[0] == 511 && f.cpu().getStored(f.input) == 511
                        && f.cpu().getWaitingFor(f.output) == 1 && f.cpu().getPendingOutputs(f.output) == 511
                        && helper.<BarrelBlockEntity>getBlockEntity(barrel).countItem(Items.IRON_INGOT) == 1,
                        "The next plain provider receives only the original iron, with single-copy power, waiting and task count");
            } finally {
                f.cpu().removeListener(lock);
            }
            f.cpu().cancel();
            helper.assertTrue(f.stored(f.input) == 511, "The extra material is still refundable by the CPU");
        }).thenSucceed();
    }

    private static NativePatternCpuFixture fixture(GameTestHelper helper, String id) {
        var f = new NativePatternCpuFixture(helper, id, false, 2, 256, ParallelTier.PRO, false);
        helper.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(256);
        f.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(f.input, 1)), List.of(new GenericStack(f.output, 1))));
        return f;
    }

    private static void assertLaneReady(GameTestHelper helper, NativePatternCpuFixture f) {
        var unit = new KeyCounter();
        unit.add(f.input, 1);
        var pattern = f.plan.patternTimes().keySet().iterator().next();
        helper.assertTrue(BulkPatternProvider.find(f.host.getLogic()).orElseThrow()
                .suggestPatternCopies(pattern, new KeyCounter[]{unit}, 512) == 512, "A full lane is ready for this dispatch");
    }

    private static IEnergyService meter(NativePatternCpuFixture f, double[] budget) {
        return (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(), new Class<?>[]{IEnergyService.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("extractAEPower")) throw new AssertionError(method);
                    args[0] = Math.min((double) args[0], budget[0]);
                    double extracted = (double) method.invoke(f.grid().getEnergyService(), args);
                    if (args[1] == Actionable.MODULATE) budget[0] -= extracted;
                    return extracted;
                });
    }

    private static int lastDispatches(CraftingCpuLogic cpu) {
        try {
            var ops = CraftingCpuLogic.class.getDeclaredField("usedOps");
            ops.setAccessible(true);
            return ((int[]) ops.get(cpu))[0];
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
