package cn.howxu.mmcr;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuHost;
import com.moakiee.ae2lt.logic.tianshu.TianshuCraftingCpuHost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** @author HowXu {@literal <dev@howxu.cn>} */
final class TianshuCpuBulkGameTest {
    static void deliveries(GameTestHelper h) { AddonCpuBulkGameTest.deliveries(h, "tianshu_bulk", f -> submit(f, true)); }
    static void refused(GameTestHelper h) { AddonCpuBulkGameTest.refused(h, "tianshu_refused", f -> submit(f, true)); }

    static void fractionalPower(GameTestHelper h) {
        var f = new NativePatternCpuFixture(h, "tianshu_fluid_power", true, 1, 4, ParallelTier.NORMAL, false);
        h.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(4);
        f.calculate().thenWaitUntil(() -> h.assertTrue(AddonCpuBulkGameTest.quote(f, 4) == 4, "Four fluid copies fit"))
                .thenExecute(() -> {
                    var cpu = submit(f, true);
                    double price = 1D / f.input.getAmountPerOperation();
                    double initial = 4 * price - Math.min(0.005, price / 2);
                    double[] power = {initial}; // within Thunderbolt's 0.01 tolerance, but cannot buy four
                    cpu.step().accept(AddonCpuBulkGameTest.meter(power));
                    AddonCpuBulkGameTest.check(h, cpu, 1, 3);
                    h.assertTrue(Math.abs(power[0] - (initial - 3 * price)) < 1e-12,
                            "Only three paid fluid copies leave the CPU");
                }).thenSucceed();
    }

    static void nonTianshu(GameTestHelper h) {
        var f = AddonCpuBulkGameTest.fixture(h, "non_tianshu", 1, 4);
        f.calculate().thenWaitUntil(() -> h.assertTrue(AddonCpuBulkGameTest.quote(f, 4) == 4, "Bulk intake ready"))
                .thenExecute(() -> {
                    var cpu = submit(f, false);
                    cpu.step().accept(f.grid().getEnergyService());
                    AddonCpuBulkGameTest.check(h, cpu, 3, 1);
                }).thenSucceed();
    }

    private static AddonCpuBulkGameTest.Cpu submit(NativePatternCpuFixture f, boolean tianshu) {
        var cpu = new TimeWheelCraftingCPU(tianshu ? new Tianshu(f) : new Host(f), 65536, 0, 64, false);
        f.helper.assertTrue(cpu.submitJob(f.grid(), f.plan, IActionSource.ofMachine(f.host), null).successful(), "Time wheel accepts AE plan");
        var logic = cpu.getCraftingLogic();
        return new AddonCpuBulkGameTest.Cpu(e -> logic.tickCraftingLogic(e, (CraftingService) f.grid().getCraftingService()),
                () -> logic.getStored(f.input), () -> logic.getWaitingFor(f.output), () -> logic.getPendingOutputs(f.output));
    }

    /** Only the physical host is substituted; both host types use AE2LT's real time wheel.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    private static class Host implements TimeWheelCraftingCpuHost {
        private final NativePatternCpuFixture f;
        Host(NativePatternCpuFixture f) { this.f = f; }
        public boolean isCpuActive() { return true; }
        public IGrid getGrid() { return f.grid(); }
        public IActionSource getActionSource() { return IActionSource.ofMachine(f.host); }
        public Level getLevel() { return f.helper.getLevel(); }
        public void markCpuDirty() {}
        public Component getDisplayName() { return Component.empty(); }
    }

    /** @author HowXu {@literal <dev@howxu.cn>} */
    private static final class Tianshu extends Host implements TianshuCraftingCpuHost {
        private final UUID id = UUID.randomUUID();
        Tianshu(NativePatternCpuFixture f) { super(f); }
        public UUID getTianshuId() { return id; }
    }
}
