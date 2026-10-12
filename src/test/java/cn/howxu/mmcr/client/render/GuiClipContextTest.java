package cn.howxu.mmcr.client.render;

import cn.howxu.mmcr.client.render.GuiClipContext.Rect;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure screenshot-target projection and nested-scissor geometry, without GL/window access.
 * @author howxu <dev@howxu.cn>
 */
class GuiClipContextTest {
    @Test void screenshotScaleAndTargetHeightDetermineScissorInsteadOfTheWindow() {
        Matrix4f view = new Matrix4f().translation(-1, 1, 0).scale(2F / 188, -2F / 108, -0.001F);
        Matrix4f pose = new Matrix4f().translation(4, 4, 0);
        var one = new GuiClipContext(new Matrix4f(), view, new Rect(0, 0, 188, 108));
        var four = new GuiClipContext(new Matrix4f(), view, new Rect(0, 0, 752, 432));
        assertEquals(new Rect(12, 26, 160, 70), one.pixelBounds(pose, 8, 8, 160, 70));
        assertEquals(new Rect(48, 104, 640, 280), four.pixelBounds(pose, 8, 8, 160, 70));
    }

    @Test void actualProjectionViewportOffsetAndNestedPoseAllParticipate() {
        Matrix4f view = new Matrix4f().translation(-1, 1, 0).scale(2F / 128, -2F / 64, -0.001F);
        Matrix4f projection = new Matrix4f().scaling(0.5F, 0.5F, 1);
        Matrix4f pose = new Matrix4f().translation(4, 4, 0).scale(2, 1, 1);
        var context = new GuiClipContext(projection, view, new Rect(17, 23, 256, 128));
        assertEquals(new Rect(89, 103, 16, 8), context.pixelBounds(pose, 2, 4, 8, 8));
    }

    @Test void nestedScissorsIntersectAndOffTargetRegionsAreEmpty() {
        Matrix4f view = new Matrix4f().translation(-1, 1, 0).scale(2F / 128, -2F / 64, -0.001F);
        var context = new GuiClipContext(new Matrix4f(), view, new Rect(0, 0, 128, 64));
        Rect outer = context.pixelBounds(new Matrix4f(), 4, 4, 100, 50);
        Rect inner = context.pixelBounds(new Matrix4f().translation(4, 4, 0), 90, 0, 30, 60);
        assertEquals(new Rect(94, 10, 10, 50), inner.intersect(outer));
        assertEquals(0, context.pixelBounds(new Matrix4f(), 200, 4, 18, 18).width());
    }
}
