package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** @author HowXu {@literal <dev@howxu.cn>} */
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress")
public interface CraftingTaskProgressAccessor {
    @Accessor("value") long mmcr$remaining();
    @Accessor("value") void mmcr$remaining(long value);
}
