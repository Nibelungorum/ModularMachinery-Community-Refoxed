package cn.howxu.mmcr.client.preview.world;

import java.util.Objects;

/**
 * Tracks the requested and published world preview mesh without owning compilation.
 *
 * <p>All methods are synchronized so a compiler may publish from another thread. A
 * compiler must retain the {@link Request} returned by {@link #requestToken} and
 * publish with that token; the token prevents results from an older request for the
 * same key from being accepted. {@link #current()} deliberately keeps returning the
 * old mesh during replacement; use {@link #current(WorldPreviewMeshKey)} to check
 * readiness for a particular key.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMeshCache implements AutoCloseable {
    private WorldPreviewMeshKey pendingKey;
    private long pendingGeneration;
    private AutoCloseable current;
    private WorldPreviewMeshKey currentKey;
    private boolean closed;

    public synchronized boolean request(WorldPreviewMeshKey key) {
        Objects.requireNonNull(key, "key");
        ensureOpen();
        if (key.equals(pendingKey)) return false;
        pendingKey = key;
        pendingGeneration++;
        return true;
    }

    /** Returns the current mesh, including an old mesh while its replacement compiles. */
    public synchronized AutoCloseable current() {
        return current;
    }

    /** Returns the current mesh only when it is ready for {@code key}. */
    public synchronized AutoCloseable current(WorldPreviewMeshKey key) {
        Objects.requireNonNull(key, "key");
        return key.equals(currentKey) ? current : null;
    }

    /**
     * Returns an immutable request token for an asynchronous compilation.
     * Repeating the pending key returns the same token.
     */
    public synchronized Request requestToken(WorldPreviewMeshKey key) {
        Objects.requireNonNull(key, "key");
        ensureOpen();
        if (!key.equals(pendingKey)) {
            pendingKey = key;
            pendingGeneration++;
        }
        return new Request(pendingKey, pendingGeneration);
    }

    /** Compatibility publication path for callers that do not need same-key generation checks. */
    public synchronized void publish(WorldPreviewMeshKey key, AutoCloseable mesh) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mesh, "mesh");
        publish(new Request(key, pendingGeneration), mesh);
    }

    public synchronized void publish(Request request, AutoCloseable mesh) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(mesh, "mesh");
        if (closed || !request.key().equals(pendingKey) || request.generation() != pendingGeneration) {
            close(mesh);
            return;
        }
        AutoCloseable previous = current;
        current = mesh;
        currentKey = request.key();
        if (previous != null) close(previous);
    }

    public synchronized void clear() {
        pendingKey = null;
        pendingGeneration++;
        AutoCloseable previous = current;
        current = null;
        currentKey = null;
        if (previous != null) close(previous);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        clear();
        closed = true;
    }

    public record Request(WorldPreviewMeshKey key, long generation) {
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("world preview mesh cache is closed");
    }

    private static void close(AutoCloseable mesh) {
        try {
            mesh.close();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot close world preview mesh", exception);
        }
    }
}
