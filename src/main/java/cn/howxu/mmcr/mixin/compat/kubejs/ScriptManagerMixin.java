package cn.howxu.mmcr.mixin.compat.kubejs;

import cn.howxu.mmcr.compat.kubejs.KubeJSReloadHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.latvian.mods.kubejs.script.ScriptManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Ensures an interrupted KubeJS reload cannot retain MMCR's server-script transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(ScriptManager.class)
public abstract class ScriptManagerMixin {
    @WrapMethod(method = "reload")
    private void mmcr$finishServerReload(Operation<Void> original) {
        try {
            original.call();
        } finally {
            KubeJSReloadHooks.abortServerReload(this);
        }
    }
}
