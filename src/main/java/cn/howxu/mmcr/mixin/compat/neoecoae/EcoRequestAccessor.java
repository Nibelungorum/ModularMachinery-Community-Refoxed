package cn.howxu.mmcr.mixin.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ECO's per-candidate resolved input preview, not the pattern's possible inputs.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingDispatchRequest", remap = false)
public interface EcoRequestAccessor {
    @Accessor("pattern") IPatternDetails mmcr$pattern();
    @Accessor("inputs") KeyCounter[] mmcr$unit();
}
