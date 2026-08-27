package cn.howxu.mmcr.api.publicapi.controller;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Runtime context supplied to controller screen text handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ControllerRuntimeContext(Identifier machineId, BlockPos controllerPos,
                                       ControllerScreenText screenText) {
    public ControllerRuntimeContext {
        Objects.requireNonNull(machineId, "machineId");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        Objects.requireNonNull(screenText, "screenText");
    }
}
