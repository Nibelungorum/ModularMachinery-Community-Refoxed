package cn.howxu.mmcr.mixin.compat.extendedae_plus;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.MirrorPatternInterfaceLogic;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents native EAEP mirrors from following an MMCR mirror interface.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = MirrorPatternProviderBlockEntity.class, remap = false)
public abstract class MirrorPatternProviderBlockEntityMixin {
    @Inject(method = "isValidMasterHost", at = @At("HEAD"), cancellable = true)
    private static void mmcr$excludeMirrorMaster(Object host, CallbackInfoReturnable<Boolean> callback) {
        if (host instanceof PatternInterfaceBlockEntity mirror
                && mirror.getLogic() instanceof MirrorPatternInterfaceLogic) {
            callback.setReturnValue(false);
        }
    }
}
