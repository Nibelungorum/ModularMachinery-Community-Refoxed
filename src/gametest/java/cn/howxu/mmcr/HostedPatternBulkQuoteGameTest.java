package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

/** Exercises the view actually published by an AE grid, independently of a special CPU protocol.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class HostedPatternBulkQuoteGameTest {
    public static void quoteAndDeliver(GameTestHelper helper) { exercise(helper, false, 2, ParallelTier.PRO); }

    public static void compressedFluidQuote(GameTestHelper helper) { exercise(helper, true, 2, ParallelTier.ULTIMATE); }

    public static void openEscrowAndRefund(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "bulk_open_refund", false, 2, 2, ParallelTier.PRO, false);
        helper.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(256);
        f.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(f.input, 1)), List.of(new GenericStack(f.output, 1))));
        var one = new KeyCounter();
        one.add(f.input, 1);
        var unit = new KeyCounter[]{one};
        var provider = f.host.getLogic();
        f.ready().thenWaitUntil(() -> helper.assertTrue(BulkPatternProvider.find(provider).orElseThrow()
                .suggestPatternCopies(provider.getAvailablePatterns().getFirst(), unit, 600) == 512, "An eligible lane is ready"))
                .thenExecute(() -> {
                    var pattern = provider.getAvailablePatterns().getFirst();
                    var view = BulkPatternProvider.find(provider).orElseThrow();
                    var six = new KeyCounter();
                    six.add(f.input, 6);
                    helper.assertTrue(provider.pushPattern(pattern, new KeyCounter[]{six}), "First material delivery");
                    f.host.dispatchHostedPatterns();
                    var group = f.host.hostedPatterns().group(pattern.getDefinition());
                    helper.assertTrue(view.suggestPatternCopies(pattern, unit, 600) == 506
                                    && view.suggestPatternCopies(pattern, unit, 600) == 506 && group == f.host.hostedPatterns().group(pattern.getDefinition()),
                            "Six validated iron reduce the offer without resetting the wait clock or changing the group");
                    f.storage.insertionLimit = f.total;
                    f.host.requestGroupHostedRefund();
                    helper.assertTrue(f.host.hasGroupHostedMaterials() && view.suggestPatternCopies(pattern, unit, 600) == 0,
                            "Refund-only material can never count as a new delivery's purchasing power");
                    f.storage.insertionLimit = Long.MAX_VALUE;
                }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.input) == f.total + 6 && !f.host.hasGroupHostedMaterials()
                        && f.stored(f.output) == 0, "Queries did not turn refund-only escrow back into payable inputs")).thenSucceed();
    }

    private static void exercise(GameTestHelper helper, boolean fluid, int cost, ParallelTier tier) {
        int parallel = tier.maxParallelism();
        var f = new NativePatternCpuFixture(helper, "bulk_query_" + (fluid ? "fluid" : "item"), fluid, cost, parallel, tier, false);
        helper.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(parallel);
        var plain = new BlockPos(2, 1, 2);
        helper.setBlock(plain, AEBlocks.PATTERN_PROVIDER.block());
        f.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(f.input, 1)), List.of(new GenericStack(f.output, 1))));
        long wanted = Math.multiplyExact((long) parallel, cost);
        var one = new KeyCounter();
        one.add(f.input, 1);
        var unit = new KeyCounter[]{one};
        f.ready().thenWaitUntil(() -> {
            var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
            var provider = ((CraftingService) f.grid().getCraftingService()).getProviders(pattern).iterator().next();
            var quote = BulkPatternProvider.find(provider).orElseThrow();
            helper.assertTrue(provider == f.host.getMainNode().getNode().getService(ICraftingProvider.class), "Test the actual grid provider");
            helper.assertTrue(quote.suggestPatternCopies(pattern, unit, wanted) == wanted, "Next available lane quotes original pattern copies");
        }).thenExecute(() -> {
            var provider = f.host.getLogic();
            var pattern = provider.getAvailablePatterns().getFirst();
            var quote = BulkPatternProvider.find(provider).orElseThrow();
            var plainProvider = helper.<PatternProviderBlockEntity>getBlockEntity(plain).getLogic();
            helper.assertTrue(BulkPatternProvider.find(plainProvider).isEmpty() && BulkPatternProvider.find(f.provider).isEmpty(),
                    "Neither vanilla pattern providers nor arbitrary wrappers claim MMCR capacity");
            long revision = f.host.hostedPatterns().revision();
            var quota = f.controller.runtimeSnapshot().factory().activeLaneCount();
            helper.assertTrue(quote.suggestPatternCopies(pattern, unit, 1) == 0
                            && quote.suggestPatternCopies(pattern, unit, wanted - 1) == wanted - 2
                            && quote.suggestPatternCopies(pattern, unit, Long.MAX_VALUE) == wanted
                            && f.host.hostedPatterns().isEmpty() && f.host.hostedPatterns().revision() == revision
                            && f.controller.runtimeSnapshot().factory().activeLaneCount() == quota && one.get(f.input) == 1,
                    "Repeated quotes move no resources or threads; a lone iron cannot pay this recipe");
            var returns = provider.getReturnInv();
            int last = returns.size() - 1;
            returns.setStack(last, new GenericStack(f.output, Long.MAX_VALUE));
            helper.assertTrue(quote.suggestPatternCopies(pattern, unit, wanted) == 0 && returns.getAmount(last) == Long.MAX_VALUE,
                    "A full output return cache blocks the quote without draining or changing it");
            returns.setStack(last, null);
            if (!fluid) {
                var bus = helper.<ItemInputBusBlockEntity>getBlockEntity(NativePatternCpuFixture.BUS);
                helper.assertTrue(bus.nativeItemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT, 12), false).isEmpty(),
                        "Ordinary bus has twelve usable iron");
                helper.assertTrue(quote.suggestPatternCopies(pattern, unit, wanted) == wanted - 12
                                && bus.nativeItemHandler().getStackInSlot(0).getCount() == 12,
                        "Quotes count but do not spend legal ordinary inputs");
                bus.setInputIsolated(true);
                helper.assertTrue(quote.suggestPatternCopies(pattern, unit, wanted) == wanted
                                && bus.nativeItemHandler().getStackInSlot(0).getCount() == 12,
                        "A different isolated bus cannot reduce the requested delivery");
                bus.nativeItemHandler().extractItem(0, 12, false);
                bus.setInputIsolated(false);
            }
            var stock = f.grid().getStorageService().getInventory();
            var raw = new KeyCounter();
            raw.add(f.input, stock.extract(f.input, wanted, Actionable.MODULATE, IActionSource.ofMachine(f.host)));
            helper.assertTrue(raw.get(f.input) == wanted && f.stored(f.input) == 0, "Sender really has the complete batch");
            provider.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW);
            helper.assertTrue(quote.suggestPatternCopies(pattern, unit, wanted) == 0
                    && !provider.pushPattern(pattern, new KeyCounter[]{raw}) && raw.get(f.input) == wanted, "Stale quote does not bypass a native lock");
            provider.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
            helper.assertTrue(provider.pushPattern(pattern, new KeyCounter[]{raw}) && raw.isEmpty()
                    && f.host.hostedPatterns().group(pattern.getDefinition()).materials().get(f.input) == wanted
                    && quote.suggestPatternCopies(pattern, unit, wanted) == 0, "One real push transfers ownership, first gate blocks a second batch");
        }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.output) == wanted && f.host.hostedPatterns().isEmpty()
                && f.stored(f.input) == 0, "The actual input pays for the expected real output")).thenSucceed();
    }
}
