package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.InterfaceLogic;
import java.util.OptionalInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Retains AE2's same-grid interface request priority checks for local-only ME storage.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(InterfaceLogic.class)
public interface InterfaceLogicAccessor {
    @Invoker("getRequestInterfacePriority") OptionalInt mmcr$requestPriority(IActionSource source);
    @Invoker("isSameGrid") boolean mmcr$isSameGrid(IActionSource source);
}
