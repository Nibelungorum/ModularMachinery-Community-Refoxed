package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.BulkPatternProviderSource;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Exercise the real add-on dispatchers against a two-lane factory and the unchanged AE plan.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
final class AddonCpuBulkGameTest {
    /** The fixture supplies a host/grid; the add-on supplies the actual CPU, inventory and ledger.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    record Cpu(Consumer<IEnergyService> step, LongSupplier stored, LongSupplier waiting, LongSupplier pending) {}

    static NativePatternCpuFixture fixture(GameTestHelper h, String id, int cost, int operations) {
        var f = new NativePatternCpuFixture(h, id, false, cost, operations, ParallelTier.NORMAL, false);
        h.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(4);
        f.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(f.input, 1)), List.of(new GenericStack(f.output, 1))));
        return f;
    }

    static void deliveries(GameTestHelper h, String id, Function<NativePatternCpuFixture, Cpu> submit) {
        var f = fixture(h, id, 1, 9);
        var cpu = new Cpu[1];
        double[] power = {9};
        var meter = meter(power);
        var seq = f.calculate().thenExecute(() -> cpu[0] = submit.apply(f));
        for (int paid : new int[]{4, 8, 9}) {
            int batch = paid == 9 ? 1 : 4;
            seq.thenWaitUntil(() -> h.assertTrue(quote(f, 9 - paid + batch) == batch, "A lane can accept the next batch"))
                    .thenExecute(() -> {
                        long revision = f.host.hostedPatterns().revision();
                        cpu[0].step.accept(meter);
                        h.assertTrue(f.host.hostedPatterns().revision() == revision + 1
                                        && f.host.hostedPatterns().groups().getFirst().materials().get(f.input) == batch,
                                "One real push delivers " + batch + " original-pattern copies");
                        check(h, cpu[0], 9 - paid, paid);
                        h.assertTrue(power[0] == 9 - paid && f.plan.patternTimes().containsValue(9L),
                                "Only accepted copies use power; the plan is untouched");
                    });
            if (paid == 8) seq.thenWaitUntil(() -> h.assertTrue(f.controller.runtimeSnapshot().factory().activeLaneCount() == 2
                    && f.controller.runtimeSnapshot().factory().lanes().stream().filter(l -> l.parallelism() == 4).count() == 2,
                    "Two concurrent recipe lanes each paid for four operations"));
        }
        seq.thenWaitUntil(() -> h.assertTrue(f.stored(f.output) == 9 && f.stored(f.input) == 0
                && f.host.hostedPatterns().isEmpty(), "All nine real outputs returned to the ME network")).thenSucceed();
    }

    static void refused(GameTestHelper h, String id, Function<NativePatternCpuFixture, Cpu> submit) {
        var f = fixture(h, id, 2, 4);
        var reject = new RefusingProvider(f.host.getLogic());
        f.host.getMainNode().addService(ICraftingProvider.class, reject);
        var plainPos = new BlockPos(2, 1, 2);
        h.setBlock(plainPos, AEBlocks.PATTERN_PROVIDER.block());
        h.setBlock(plainPos.south(), Blocks.BARREL);
        PatternProviderBlockEntity plain = h.getBlockEntity(plainPos);
        var cpu = new Cpu[1];
        double[] power = {1};
        var meter = meter(power);
        f.calculate().thenWaitUntil(() -> h.assertTrue(quote(f, 8) == 8, "Machine is ready"))
                .thenExecute(() -> {
                    cpu[0] = submit.apply(f);
                    cpu[0].step.accept(meter);
                    check(h, cpu[0], 8, 0);
                    h.assertTrue(reject.offers == 0 && power[0] == 1 && f.host.hostedPatterns().isEmpty(),
                            "A single funded iron cannot pay the first operation; no push or charge");
                    power[0] = 8;
                }).thenWaitUntil(() -> {
                    cpu[0].step.accept(meter);
                    h.assertTrue(reject.offers > 0, "A funded batch reaches the refusing provider");
                }).thenExecute(() -> {
                    check(h, cpu[0], 8, 0);
                    h.assertTrue(reject.last == 8 && reject.offers == 1 && power[0] == 8,
                            "Rejected batch restored without retrying one copy");
                    GridHelper.createConnection(plain.getMainNode().getNode(), f.energy.getMainNode().getNode());
                    plain.getLogic().getPatternInv().setItemDirect(0, f.host.getLogic().getPatternInv().getStackInSlot(0).copy());
                    plain.getLogic().updatePatterns();
                    power[0] = 1;
                }).thenWaitUntil(() -> {
                    cpu[0].step.accept(meter);
                    h.assertTrue(h.<BarrelBlockEntity>getBlockEntity(plainPos.south()).countItem(Items.IRON_INGOT) == 1,
                            "The ordinary provider can still receive one copy");
                }).thenExecute(() -> {
                    check(h, cpu[0], 7, 1);
                    h.assertTrue(power[0] == 0 && f.host.hostedPatterns().isEmpty(), "No duplicate settlement");
                }).thenSucceed();
    }

    static long quote(NativePatternCpuFixture f, long cap) {
        var logic = f.host.getLogic();
        var unit = new KeyCounter();
        unit.add(f.input, 1);
        return BulkPatternProvider.find(logic).orElseThrow().suggestPatternCopies(
                logic.getAvailablePatterns().getFirst(), new KeyCounter[]{unit}, cap);
    }

    static void check(GameTestHelper h, Cpu cpu, long remaining, long waiting) {
        h.assertTrue(cpu.stored.getAsLong() == remaining && cpu.pending.getAsLong() == remaining
                        && cpu.waiting.getAsLong() == waiting,
                "CPU stored/pending/waiting: " + cpu.stored.getAsLong() + "/" + cpu.pending.getAsLong() + "/" + cpu.waiting.getAsLong());
    }

    static IEnergyService meter(double[] balance) {
        return (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(), new Class<?>[]{IEnergyService.class},
                (p, method, args) -> {
                    double amount = (double) args[0];
                    return switch (method.getName()) {
                        case "extractAEPower" -> {
                            double got = Math.min(amount, balance[0]);
                            if (args[1] == Actionable.MODULATE) balance[0] -= got;
                            yield got;
                        }
                        case "injectPower" -> {
                            if (args[1] == Actionable.MODULATE) balance[0] += amount;
                            yield 0D;
                        }
                        default -> throw new AssertionError(method);
                    };
                });
    }

    /** Advertises the real host's quote but refuses ownership at the final boundary.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    private static final class RefusingProvider implements ICraftingProvider, BulkPatternProviderSource {
        private final ICraftingProvider host;
        int offers;
        long last;

        RefusingProvider(ICraftingProvider host) { this.host = host; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return host.getAvailablePatterns(); }
        @Override public boolean isBusy() { return host.isBusy(); }
        @Override public BulkPatternProvider mmcr$bulkPatternProvider() { return BulkPatternProvider.find(host).orElseThrow(); }
        @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            offers++;
            last = inputs[0].getFirstEntry().getLongValue();
            return false;
        }
    }
}
