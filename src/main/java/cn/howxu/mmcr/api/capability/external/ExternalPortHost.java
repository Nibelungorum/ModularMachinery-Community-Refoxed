package cn.howxu.mmcr.api.capability.external;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** Opt-in to exposing selected bindings to external pipes. Invalidate level capabilities when sides change.
 * @author howxu <dev@howxu.cn>
 */
public interface ExternalPortHost extends CapabilityHost {
    IOType ioType();
    boolean isNativeSideExposed(CapabilityBinding binding, @Nullable Direction side);
}
