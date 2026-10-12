package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Page-owned, frame-borrowed and screenshot-local preview resource lifetimes.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiPreviewSessions {
    private final Consumer<Runnable> disposal;
    private final Map<Key, Session<?>> pages = new HashMap<>();
    private Session<?> hover;
    private boolean hoverUsed;

    public EmiPreviewSessions() { this(Runnable::run); }

    EmiPreviewSessions(Consumer<Runnable> disposal) { this.disposal = disposal; }

    public static EmiPreviewSessions instance() { return Holder.INSTANCE; }

    private static final class Holder {
        private static final EmiPreviewSessions INSTANCE = new EmiPreviewSessions(EmiClientEvents::onRenderThread);
    }

    public <T extends AutoCloseable> Session<T> page(Object host, ResourceLocation structure,
                                                    Version version, Supplier<T> factory) {
        Key key = new Key(host, structure, version);
        @SuppressWarnings("unchecked") Session<T> existing = (Session<T>) pages.get(key);
        if (existing != null && existing.current(version)) return existing;
        // A group can rebuild with a new snapshot without retaining its obsolete resources.
        pages.values().stream().filter(session -> session.key.host == host
                && session.key.structure.equals(structure)).toList().forEach(Session::close);
        Session<T> created = new Session<>(key, factory.get());
        pages.put(key, created);
        return created;
    }

    public <T extends AutoCloseable> Session<T> hover(Object host, ResourceLocation structure,
                                                     Version version, Supplier<T> factory) {
        Key key = new Key(host, structure, version);
        if (hover == null || !hover.key.equals(key) || hover.closed) {
            if (hover != null) hover.close();
            hover = new Session<>(key, factory.get());
        }
        hoverUsed = true;
        @SuppressWarnings("unchecked") Session<T> result = (Session<T>) hover;
        return result;
    }

    public <T extends AutoCloseable> Session<T> screenshot(Object host, ResourceLocation structure,
                                                          Version version, Supplier<T> factory) {
        return new Session<>(new Key(host, structure, version), factory.get());
    }

    public void endFrame() {
        if (!hoverUsed && hover != null) hover.close();
        hoverUsed = false;
    }

    public void removeHost(Object host) {
        pages.values().stream().filter(session -> session.key.host == host).toList().forEach(Session::close);
        if (hover != null && hover.key.host == host) hover.close();
    }

    public void invalidate() {
        pages.values().stream().toList().forEach(Session::close);
        if (hover != null) hover.close();
        hoverUsed = false;
    }

    /** Closed before scheduling disposal, so late compilation callbacks cannot reinstall resources.
     * @author howxu <dev@howxu.cn>
     */
    public final class Session<T extends AutoCloseable> implements AutoCloseable {
        private final Key key;
        private final T resource;
        private volatile boolean closed;

        private Session(Key key, T resource) { this.key = key; this.resource = resource; }

        public boolean current(Version version) { return !closed && key.version.equals(version); }

        public T resource() {
            if (closed) throw new IllegalStateException("Preview session is closed");
            return resource;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            pages.remove(key, this);
            if (hover == this) hover = null;
            disposal.accept(() -> {
                try { resource.close(); }
                catch (RuntimeException exception) { throw exception; }
                catch (Exception exception) { throw new IllegalStateException("Cannot close EMI preview", exception); }
            });
        }
    }

    private record Key(Object host, ResourceLocation structure, Version version) {
        private Key {
            Objects.requireNonNull(host);
            Objects.requireNonNull(structure);
            Objects.requireNonNull(version);
        }
        @Override public boolean equals(Object object) {
            return object instanceof Key other && host == other.host
                    && structure.equals(other.structure) && version.equals(other.version);
        }
        @Override public int hashCode() {
            return 31 * (31 * System.identityHashCode(host) + structure.hashCode()) + version.hashCode();
        }
    }
}
