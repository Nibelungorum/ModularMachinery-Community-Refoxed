package cn.howxu.mmcr.client.gui;

import net.minecraft.network.chat.Component;

/**
 * One logical controller screen line and its internal render color.
 *
 * @param text the line text
 * @param color the render color
 * @author howxu <dev@howxu.cn>
 */
public record ControllerTextLine(Component text, int color) {
}
