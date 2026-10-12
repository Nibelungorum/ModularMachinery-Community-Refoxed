package cn.howxu.mmcr.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

/** Restores the exact entering pose frame, including unbalanced pushes in nested renderers.
 * @author howxu <dev@howxu.cn>
 */
public final class GuiPoseScope implements AutoCloseable {
    private final PoseStack stack;
    private final PoseStack.Pose entry;
    private boolean closed;

    public GuiPoseScope(PoseStack stack) {
        this.stack = stack;
        this.entry = stack.last();
        // Isolate writes as well as pushes; the original frame and its trusted-normal state stay intact.
        stack.pushPose();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        while (stack.last() != entry) stack.popPose();
    }
}
