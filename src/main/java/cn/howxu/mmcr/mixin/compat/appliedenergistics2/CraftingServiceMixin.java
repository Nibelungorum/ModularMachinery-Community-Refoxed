package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps scaled jobs in the same binding's MMCR-only provider index.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin {
    @ModifyVariable(method = "getProviders", at = @At("HEAD"), argsOnly = true)
    private IPatternDetails mmcr$unitProviderKey(IPatternDetails pattern) {
        return PatternBatchPlanning.providerKey(pattern);
    }
}
