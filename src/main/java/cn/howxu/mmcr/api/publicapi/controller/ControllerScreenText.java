package cn.howxu.mmcr.api.publicapi.controller;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Public handle for updating controller screen text.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ControllerScreenText {
    void append(ControllerScreenTextScope scope, Identifier lineId, Component text);

    void remove(ControllerScreenTextScope scope, Identifier lineId);

    void clear(ControllerScreenTextScope scope);
}
