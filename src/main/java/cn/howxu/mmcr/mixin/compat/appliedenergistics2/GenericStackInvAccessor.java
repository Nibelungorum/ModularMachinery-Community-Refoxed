package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Resizes only the MMCR pattern return inventory instance.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(GenericStackInv.class)
public interface GenericStackInvAccessor {
    @Mutable
    @Accessor("stacks")
    void mmcr$setStacks(GenericStack[] stacks);
}
