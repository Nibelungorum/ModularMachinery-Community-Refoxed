package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ExecutingCraftingJob;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accesses AE2's package-private CPU task state for the narrow batch dispatcher.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(ExecutingCraftingJob.class)
public interface ExecutingCraftingJobAccessor {
    @Accessor("tasks")
    Map<IPatternDetails, Object> mmcr$tasks();
}
