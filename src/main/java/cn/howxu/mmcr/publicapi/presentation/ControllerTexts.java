package cn.howxu.mmcr.publicapi.presentation;

import cn.howxu.mmcr.internal.api.facade.presentation.PresentationAdapters;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Registers text callbacks within the core registration/thread window.
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerTexts {
    private ControllerTexts() {}
    public static Registration register(ResourceLocation machineId, ControllerTextHandler handler) {
        return PresentationAdapters.register(machineId, handler);
    }
    /** Runtime-provided registration handle; unregister is idempotent.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    public interface Registration { void unregister(); }
}
