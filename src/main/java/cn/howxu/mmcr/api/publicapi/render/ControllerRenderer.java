package cn.howxu.mmcr.api.publicapi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Renders a machine controller's custom visual state.
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface ControllerRenderer {
    void render(ControllerRenderContext context, PoseStack poseStack,
                SubmitNodeCollector nodeCollector, CameraRenderState camera);

    default boolean shouldRenderOffScreen() {
        return false;
    }

    default int getViewDistance() {
        return 64;
    }
}
