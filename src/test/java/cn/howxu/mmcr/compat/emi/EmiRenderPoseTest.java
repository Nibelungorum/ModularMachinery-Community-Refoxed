package cn.howxu.mmcr.compat.emi;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.widget.DrawableWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** Real EMI DrawableWidget failure leaks its wrapper frame; no font, atlas or GL is needed.
 * @author howxu <dev@howxu.cn>
 */
class EmiRenderPoseTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void helperFinallyRestoresAllNestedFramesAfterDrawableFailureIsSwallowed(boolean screenshot) throws Exception {
        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(20, 30, 0);
        pose.scale(2, 3, 1);
        var entry = pose.last();
        Matrix4f matrix = new Matrix4f(entry.pose());
        Matrix3f normal = new Matrix3f(entry.normal());
        GuiGraphics graphics = graphicsWithPose(pose);
        DrawableWidget title = new DrawableWidget(4, 24, 80, 20, (raw, mx, my, delta) -> {
            raw.pose().pushPose();
            try {
                raw.pose().translate(5, 5, 0);
                throw new IllegalStateException("title font render");
            } finally { raw.pose().popPose(); }
        });
        try (var scope = screenshot ? EmiRenderScope.screenshot() : EmiRenderScope.hover(new Object())) {
            scope.preservePose(pose);
            try {
                pose.pushPose(); // Actual helper's outer frame; it is skipped on error.
                pose.translate(4, 4, 0);
                title.render(graphics, 0, 0, 0);
                pose.popPose();
            } catch (IllegalStateException swallowedByEmi) {
                assertNotSame(entry, pose.last());
            }
        }
        assertSame(entry, pose.last());
        assertEquals(matrix, pose.last().pose());
        assertEquals(normal, pose.last().normal());
        // A subsequent tooltip inherits precisely the entering transform, with no helper/title offset.
        pose.pushPose();
        pose.translate(6, 7, 0);
        assertEquals(new Matrix4f(matrix).translate(6, 7, 0), pose.last().pose());
        pose.popPose();
        pose.popPose();
        assertTrue(pose.clear());
        assertEquals(new Matrix4f(), pose.last().pose());
    }

    @Test void nestedRenderScopeRestoresTheOuterFrameBeforeOuterCleanup() {
        PoseStack pose = new PoseStack();
        var root = pose.last();
        try (var outer = EmiRenderScope.hover(new Object())) {
            outer.preservePose(pose);
            pose.translate(4, 8, 0);
            var outerFrame = pose.last();
            Matrix4f original = new Matrix4f(outerFrame.pose());
            try (var inner = EmiRenderScope.screenshot()) {
                inner.preservePose(pose);
                pose.pushPose();
                pose.pushPose();
                pose.translate(100, 200, 0);
            }
            assertSame(outer, EmiRenderScope.current());
            assertSame(outerFrame, pose.last());
            assertEquals(original, pose.last().pose());
        }
        assertSame(root, pose.last());
        assertTrue(pose.clear());
    }

    private static GuiGraphics graphicsWithPose(PoseStack pose) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        GuiGraphics graphics = (GuiGraphics) ((Unsafe) field.get(null)).allocateInstance(GuiGraphics.class);
        Field poseField = GuiGraphics.class.getDeclaredField("pose");
        poseField.setAccessible(true);
        poseField.set(graphics, pose);
        return graphics;
    }
}
