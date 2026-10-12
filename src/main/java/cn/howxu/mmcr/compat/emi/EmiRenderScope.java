package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import cn.howxu.mmcr.client.render.GuiPoseScope;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Stack-scoped render host context; screenshot resources are never borrowed from a page.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiRenderScope {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private EmiRenderScope() {}

    public static Scope screenshot() { return enter(new Object(), true); }
    public static Scope hover(Object host) { return enter(host, false); }
    public static Scope current() { return CURRENT.get(); }

    private static Scope enter(Object host, boolean screenshot) {
        Scope scope = new Scope(CURRENT.get(), host, screenshot);
        CURRENT.set(scope);
        return scope;
    }

    public static Runnable screenshot(Runnable original) {
        return new ScreenshotRunnable(original);
    }

    /** Marker for the recorder's MMCR-only exception cleanup; retains the original callback.
     * @author howxu <dev@howxu.cn>
     */
    public static final class ScreenshotRunnable implements Runnable {
        private final Runnable original;
        private ScreenshotRunnable(Runnable original) { this.original = original; }
        @Override public void run() {
            try (Scope scope = screenshot()) {
                original.run();
                scope.verifySuccess();
            }
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final Object host;
        private final boolean screenshot;
        private final List<AutoCloseable> temporary = new ArrayList<>();
        private GuiPoseScope pose;
        private Throwable renderFailure;
        private boolean closed;

        private Scope(Scope previous, Object host, boolean screenshot) {
            this.previous = previous;
            this.host = host;
            this.screenshot = screenshot;
        }

        public boolean isScreenshot() { return screenshot; }

        public <T extends AutoCloseable> EmiPreviewSessions.Session<T> acquire(EmiPreviewSessions sessions,
                ResourceLocation structure, Version version, Supplier<T> factory) {
            if (!screenshot) return sessions.hover(host, structure, version, factory);
            var session = sessions.screenshot(host, structure, version, factory);
            temporary.add(session);
            return session;
        }

        public void preservePose(PoseStack stack) { pose = new GuiPoseScope(stack); }

        public void failed(Throwable failure) {
            if (screenshot && renderFailure == null) renderFailure = failure;
        }

        public void verifySuccess() {
            if (renderFailure != null) throw new IllegalStateException("EMI screenshot render failed", renderFailure);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = null;
            try {
                if (pose != null) {
                    try { pose.close(); }
                    catch (RuntimeException exception) { failure = exception; }
                }
                for (int index = temporary.size() - 1; index >= 0; index--) {
                    try { temporary.get(index).close(); }
                    catch (Exception exception) {
                        RuntimeException cleanup = exception instanceof RuntimeException runtime
                                ? runtime : new IllegalStateException(exception);
                        if (failure == null) failure = cleanup;
                        else failure.addSuppressed(cleanup);
                    }
                }
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
            if (failure != null) throw failure;
        }
    }
}
