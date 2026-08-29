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

    /**
     * Appends or updates a line while keeping it after another line in the same scope.
     * The relative order is retained if the target line is added later.
     *
     * <p>The default implementation appends normally for compatibility with existing custom implementations.
     * Implementations that support relative ordering should override this method.</p>
     */
    default void appendAfter(ControllerScreenTextScope scope, Identifier lineId, Identifier afterLineId, Component text) {
        append(scope, lineId, text);
    }

    void remove(ControllerScreenTextScope scope, Identifier lineId);

    void clear(ControllerScreenTextScope scope);
}
