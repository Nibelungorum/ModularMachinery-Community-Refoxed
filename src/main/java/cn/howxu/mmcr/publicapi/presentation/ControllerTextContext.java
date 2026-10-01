package cn.howxu.mmcr.publicapi.presentation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Runtime-produced text callback context. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ControllerTextContext {
    ResourceLocation machineId();
    BlockPos controllerPos();
    ControllerText screenText();
}
