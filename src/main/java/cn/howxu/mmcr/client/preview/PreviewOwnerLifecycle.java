package cn.howxu.mmcr.client.preview;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owner-only callback and release gate for one structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewOwnerLifecycle {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean releaseQueued = new AtomicBoolean();
    private long lastReadAt = Long.MIN_VALUE;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    long nextReadback(int mouseX, int mouseY, long now) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastReadAt = now;
        return generation.incrementAndGet();
    }

    boolean shouldRead(int mouseX, int mouseY, long now) {
        return mouseX != lastMouseX || mouseY != lastMouseY || now - lastReadAt >= 50L;
    }

    boolean accepts(long token) {
        return !releaseQueued.get() && token == generation.get();
    }

    boolean queueRelease() {
        if (!releaseQueued.compareAndSet(false, true)) return false;
        generation.incrementAndGet();
        return true;
    }
}
