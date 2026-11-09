package cn.howxu.mmcr.mixin.compat.extendedae;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.PatternInterfaceRenaming;
import com.glodblock.github.extendedae.container.ContainerRenamer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Supplies a native-renamer setter without changing ExtendedAE's handling of AE2 hosts.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = ContainerRenamer.class, remap = false)
public abstract class ContainerRenamerMixin {
    @Inject(method = "setter", at = @At("HEAD"), cancellable = true)
    private static void mmcr$patternInterfaceSetter(Object host, CallbackInfoReturnable<Consumer<String>> callback) {
        if (PatternInterfaceRenaming.supports(host)) {
            var pattern = (PatternInterfaceBlockEntity) host;
            callback.setReturnValue(name -> pattern.setCustomName(name.isBlank() ? null : Component.literal(name)));
        }
    }
}
