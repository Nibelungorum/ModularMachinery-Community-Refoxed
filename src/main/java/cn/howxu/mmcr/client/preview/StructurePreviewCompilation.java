package cn.howxu.mmcr.client.preview;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe state of one lazy structure preview compilation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewCompilation implements AutoCloseable {
    private final Runnable starter;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean complete = new AtomicBoolean();
    private final AtomicInteger progressPercent = new AtomicInteger();
    private volatile @Nullable StructurePreviewSchema schema;
    private volatile @Nullable Throwable failure;

    StructurePreviewCompilation(Runnable starter) { this.starter = starter; }
    public void start() { if (started.compareAndSet(false, true)) starter.run(); }
    void complete(@Nullable StructurePreviewSchema schema, @Nullable Throwable failure) {
        this.schema = schema;
        this.failure = failure;
        progressPercent.set(100);
        complete.set(true);
    }
    public int progressPercent() { return progressPercent.get(); }
    public @Nullable StructurePreviewSchema schema() { return schema; }
    public @Nullable Throwable failure() { return failure; }
    public boolean complete() { return complete.get(); }
    @Override public void close() { }
}
