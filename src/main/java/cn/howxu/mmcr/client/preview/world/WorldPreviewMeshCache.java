package cn.howxu.mmcr.client.preview.world;

import java.util.Objects;

/**
 * Tracks the requested and published world preview mesh without owning compilation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMeshCache implements AutoCloseable {
    private WorldPreviewMeshKey pendingKey;
    private AutoCloseable current;

    public boolean request(WorldPreviewMeshKey key) {
        Objects.requireNonNull(key, "key");
        if (key.equals(pendingKey)) return false;
        pendingKey = key;
        return true;
    }

    public AutoCloseable current() {
        return current;
    }

    public void publish(WorldPreviewMeshKey key, AutoCloseable mesh) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mesh, "mesh");
        if (!key.equals(pendingKey)) {
            close(mesh);
            return;
        }
        AutoCloseable previous = current;
        current = mesh;
        if (previous != null) close(previous);
    }

    public void clear() {
        pendingKey = null;
        AutoCloseable previous = current;
        current = null;
        if (previous != null) close(previous);
    }

    @Override
    public void close() {
        clear();
    }

    private static void close(AutoCloseable mesh) {
        try {
            mesh.close();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot close world preview mesh", exception);
        }
    }
}
