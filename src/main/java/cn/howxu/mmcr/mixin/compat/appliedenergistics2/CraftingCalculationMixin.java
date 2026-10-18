package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures data once, before the calculation is submitted to AE2's worker pool.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationMixin implements PatternBatchPlanning.SnapshotSource {
    @Unique
    private PatternBatchPlanning.Snapshot mmcr$batchSnapshot = PatternBatchPlanning.Snapshot.EMPTY;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mmcr$captureBatchSnapshot(Level level, IGrid grid, ICraftingSimulationRequester requester,
                                          GenericStack output, CalculationStrategy strategy, CallbackInfo callback) {
        mmcr$batchSnapshot = PatternBatchPlanning.capture(grid);
    }

    @Override
    public PatternBatchPlanning.Snapshot mmcr$batchSnapshot() {
        return mmcr$batchSnapshot;
    }
}
