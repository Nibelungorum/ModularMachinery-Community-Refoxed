package cn.howxu.mmcr.mixin.compat.fluxnetworks.loaded;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import sonar.fluxnetworks.common.connection.TransferHandler;

/** Invokes native network receipt without exposing an external energy capability.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = TransferHandler.class, remap = false)
public interface TransferHandlerAccessor {
    @Invoker("addToBuffer")
    void mmcr$addToBuffer(long amount);
}
