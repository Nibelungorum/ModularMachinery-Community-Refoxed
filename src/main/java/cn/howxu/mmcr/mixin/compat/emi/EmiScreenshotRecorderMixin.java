package cn.howxu.mmcr.mixin.compat.emi;

import cn.howxu.mmcr.compat.emi.EmiRenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.emi.emi.runtime.EmiScreenshotRecorder;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** EMI's recorder has no callback-failure finally; preserve its actual caller for MMCR captures only.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = EmiScreenshotRecorder.class, remap = false)
public abstract class EmiScreenshotRecorderMixin {
    @Unique private static final ThreadLocal<Capture> mmcr$capture = new ThreadLocal<>();

    @WrapMethod(method = "saveScreenshotInner(Ljava/lang/String;IILjava/lang/Runnable;)V")
    private static void mmcr$ownCapture(String path, int width, int height, Runnable renderer, Operation<Void> original) {
        Capture previous = mmcr$capture.get();
        if (!(renderer instanceof EmiRenderScope.ScreenshotRunnable)) {
            // A nested foreign screenshot must not replace the outer MMCR capture's target.
            mmcr$capture.remove();
            try { original.call(path, width, height, renderer); }
            finally { if (previous != null) mmcr$capture.set(previous); }
            return;
        }
        try (Capture capture = new Capture()) {
            mmcr$capture.set(capture);
            original.call(path, width, height, renderer);
        } finally {
            if (previous == null) mmcr$capture.remove();
            else mmcr$capture.set(previous);
        }
    }

    @WrapOperation(method = "saveScreenshotInner(Ljava/lang/String;IILjava/lang/Runnable;)V",
            at = @At(value = "NEW", target = "com/mojang/blaze3d/pipeline/TextureTarget"))
    private static TextureTarget mmcr$target(int width, int height, boolean depth, boolean osx,
                                            Operation<TextureTarget> original) {
        TextureTarget target = original.call(width, height, depth, osx);
        if (mmcr$capture.get() != null) mmcr$capture.get().target(target);
        return target;
    }

    @WrapOperation(method = "saveScreenshotInner(Ljava/lang/String;IILjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;pushMatrix()Lorg/joml/Matrix4fStack;"))
    private static Matrix4fStack mmcr$push(Matrix4fStack view, Operation<Matrix4fStack> original) {
        Matrix4fStack result = original.call(view);
        if (mmcr$capture.get() != null) mmcr$capture.get().pushed();
        return result;
    }

    @WrapOperation(method = "saveScreenshotInner(Ljava/lang/String;IILjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private static Matrix4fStack mmcr$pop(Matrix4fStack view, Operation<Matrix4fStack> original) {
        Matrix4fStack result = original.call(view);
        if (mmcr$capture.get() != null) mmcr$capture.get().popped();
        return result;
    }

    /** Public methods are called by the transformed EMI class, outside this package/nest.
     * @author howxu <dev@howxu.cn>
     */
    public static final class Capture implements AutoCloseable {
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int[] viewport = new int[4], scissor = new int[4];
        private final GlStateBackup state = new GlStateBackup();
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private TextureTarget target;
        private int pendingViewPops;

        public Capture() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            RenderSystem.backupGlState(state);
            RenderSystem.disableScissor();
        }

        public void target(TextureTarget target) { this.target = target; }
        public void pushed() { pendingViewPops++; }
        public void popped() { pendingViewPops--; }

        @Override public void close() {
            try { if (target != null) target.destroyBuffers(); }
            finally {
                while (pendingViewPops > 0) {
                    RenderSystem.getModelViewStack().popMatrix();
                    pendingViewPops--;
                }
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                RenderSystem.restoreGlState(state);
                RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                if (!state.scissorEnabled) RenderSystem.disableScissor();
                RenderSystem.activeTexture(activeTexture);
                RenderSystem.bindTexture(texture);
            }
        }
    }
}
