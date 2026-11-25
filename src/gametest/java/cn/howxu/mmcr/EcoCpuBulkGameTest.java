package cn.howxu.mmcr;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;

/** @author HowXu {@literal <dev@howxu.cn>} */
final class EcoCpuBulkGameTest {
    static void deliveries(GameTestHelper h) { AddonCpuBulkGameTest.deliveries(h, "eco_bulk", EcoCpuBulkGameTest::submit); }
    static void refused(GameTestHelper h) { AddonCpuBulkGameTest.refused(h, "eco_refused", EcoCpuBulkGameTest::submit); }

    static void blocking(GameTestHelper h) {
        var f = AddonCpuBulkGameTest.fixture(h, "eco_blocking", 1, 4);
        f.host.getLogic().getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
        f.calculate().thenWaitUntil(() -> h.assertTrue(AddonCpuBulkGameTest.quote(f, 4) == 4, "Bulk intake ready"))
                .thenExecute(() -> {
                    var cpu = submit(f);
                    cpu.step().accept(f.grid().getEnergyService());
                    AddonCpuBulkGameTest.check(h, cpu, 3, 1);
                }).thenSucceed();
    }

    private static AddonCpuBulkGameTest.Cpu submit(NativePatternCpuFixture f) {
        var cpu = new ECOCraftingCPU(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO), ECOTier.L4) {
            @Override public boolean isActive() { return true; }
            @Override public IGrid getGrid() { return f.grid(); }
            @Override public Level getLevel() { return f.helper.getLevel(); }
            @Override public IActionSource getActionSource() { return IActionSource.ofMachine(f.host); }
            @Override public long getAvailableStorage() { return 65536; }
        };
        var logic = cpu.getLogic();
        f.helper.assertTrue(logic.trySubmitJob(f.grid(), f.plan, cpu.getActionSource(), null).successful(), "ECO accepts AE plan");
        return new AddonCpuBulkGameTest.Cpu(e -> logic.executeCrafting(1, (CraftingService) f.grid().getCraftingService(), e, f.helper.getLevel()),
                () -> logic.getStored(f.input), () -> logic.getWaitingFor(f.output), () -> logic.getPendingOutputs(f.output));
    }
}
