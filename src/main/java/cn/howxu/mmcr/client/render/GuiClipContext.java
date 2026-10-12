package cn.howxu.mmcr.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** Explicit offscreen GUI clipping; ordinary hosts retain Minecraft's window scissor path.
 * @author howxu <dev@howxu.cn>
 */
public final class GuiClipContext {
    private static final ThreadLocal<GuiClipContext> CURRENT = new ThreadLocal<>();
    private final Matrix4f viewProjection;
    private final Rect viewport;

    public GuiClipContext(Matrix4f projection, Matrix4f modelView, Rect viewport) {
        this.viewProjection = new Matrix4f(projection).mul(modelView);
        this.viewport = viewport;
    }

    public static Scope offscreen() {
        RenderSystem.assertOnRenderThread();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return new Scope(new GuiClipContext(RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix(),
                new Rect(viewport[0], viewport[1], viewport[2], viewport[3])));
    }

    /** Projects all corners through the actual target matrices; no window size or GUI scale is used. */
    public Rect pixelBounds(Matrix4f pose, int x, int y, int width, int height) {
        Matrix4f transform = new Matrix4f(viewProjection).mul(pose);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            Vector4f point = transform.transform(new Vector4f(x + (corner % 2) * width,
                    y + (corner / 2) * height, 0, 1));
            double px = viewport.x() + (point.x / point.w + 1D) * viewport.width() / 2D;
            double py = viewport.y() + (point.y / point.w + 1D) * viewport.height() / 2D;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
        }
        int left = (int) Math.floor(minX + 0.0001D), bottom = (int) Math.floor(minY + 0.0001D);
        int right = (int) Math.ceil(maxX - 0.0001D), top = (int) Math.ceil(maxY - 0.0001D);
        return new Rect(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom)).intersect(viewport);
    }

    public static void drawClipped(GuiGraphics graphics, int x, int y, int width, int height, Runnable draw) {
        GuiClipContext context = CURRENT.get();
        if (context == null) {
            Matrix4f pose = graphics.pose().last().pose();
            Vector4f first = pose.transform(new Vector4f(x, y, 0, 1));
            Vector4f last = pose.transform(new Vector4f(x + width, y + height, 0, 1));
            graphics.enableScissor((int) first.x, (int) first.y, (int) last.x, (int) last.y);
            try { draw.run(); }
            finally { graphics.disableScissor(); }
            return;
        }
        graphics.flush();
        boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        Rect previous = scissorBox();
        Rect clip = context.pixelBounds(graphics.pose().last().pose(), x, y, width, height);
        if (enabled) clip = clip.intersect(previous);
        RenderSystem.enableScissor(clip.x(), clip.y(), clip.width(), clip.height());
        try { draw.run(); }
        finally {
            try { graphics.flush(); }
            finally { restoreScissor(enabled, previous); }
        }
    }

    private static Rect scissorBox() {
        int[] box = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        return new Rect(box[0], box[1], box[2], box[3]);
    }

    private static void restoreScissor(boolean enabled, Rect box) {
        RenderSystem.enableScissor(box.x(), box.y(), box.width(), box.height());
        if (!enabled) RenderSystem.disableScissor();
    }

    /** GL lower-left pixel rectangle, also used for pure projection/intersection tests.
     * @author howxu <dev@howxu.cn>
     */
    public record Rect(int x, int y, int width, int height) {
        public Rect intersect(Rect other) {
            int left = Math.max(x, other.x), bottom = Math.max(y, other.y);
            int right = Math.min(x + width, other.x + other.width);
            int top = Math.min(y + height, other.y + other.height);
            return new Rect(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public static final class Scope implements AutoCloseable {
        private final GuiClipContext previous = CURRENT.get();
        private final boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final Rect scissor = scissorBox();
        private boolean closed;

        private Scope(GuiClipContext context) {
            CURRENT.set(context);
            // An inherited window scissor does not belong to this new offscreen target.
            if (previous == null) RenderSystem.disableScissor();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try { restoreScissor(scissorEnabled, scissor); }
            finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        }
    }
}
