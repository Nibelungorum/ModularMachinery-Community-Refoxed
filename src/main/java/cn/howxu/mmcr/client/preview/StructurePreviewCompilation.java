package cn.howxu.mmcr.client.preview;

import java.util.concurrent.atomic.AtomicBoolean;
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
    private volatile @Nullable StructurePreviewSchema schema;
    private volatile @Nullable Throwable failure;

    StructurePreviewCompilation(Runnable starter) { this.starter = starter; }
    public void start() { if (started.compareAndSet(false, true)) starter.run(); }
    void complete(@Nullable StructurePreviewSchema schema, @Nullable Throwable failure) {
        this.schema = schema;
        this.failure = failure;
        complete.set(true);
    }
    public @Nullable StructurePreviewSchema schema() { return schema; }
    public @Nullable Throwable failure() { return failure; }
    public boolean complete() { return complete.get(); }
    @Override public void close() { }
}
