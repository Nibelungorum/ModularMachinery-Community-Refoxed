package cn.howxu.mmcr.client.controller;

import net.minecraft.client.Minecraft;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerModelInvalidator {
    private ControllerModelInvalidator() {
    }

    public static void invalidate() {
        ControllerModelCache.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }
}
