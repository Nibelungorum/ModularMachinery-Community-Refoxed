package cn.howxu.mmcr.client.preview;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Owner-only callback and release gate for one structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewOwnerLifecycle {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean releaseQueued = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Runnable> ownerQueue = new ConcurrentLinkedQueue<>();
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

    boolean queueRelease(Runnable release) {
        if (!releaseQueued.compareAndSet(false, true)) return false;
        generation.incrementAndGet();
        ownerQueue.add(release);
        return true;
    }

    void enqueueCallback(long token, Runnable callback) {
        ownerQueue.add(() -> {
            if (accepts(token)) callback.run();
        });
    }

    void drainOwnerQueue() {
        for (Runnable task; (task = ownerQueue.poll()) != null; ) task.run();
    }
}
