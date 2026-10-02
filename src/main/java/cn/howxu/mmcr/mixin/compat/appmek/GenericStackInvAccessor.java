package cn.howxu.mmcr.mixin.compat.appmek;

import appeng.api.storage.AEKeySlotFilter;
import appeng.helpers.externalstorage.GenericStackInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** @author howxu <dev@howxu.cn> */
@Mixin(GenericStackInv.class)
public interface GenericStackInvAccessor {
    @Invoker("setFilter")
    void mmcr$setFilter(AEKeySlotFilter filter);
}
