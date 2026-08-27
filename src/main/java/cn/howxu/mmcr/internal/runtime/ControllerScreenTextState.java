package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-only ordered controller screen text state.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerScreenTextState implements ControllerScreenText {
    private final Map<Key, ControllerScreenTextSnapshot.Line> lines = new LinkedHashMap<>();
    private long revision;
    private boolean dirty;

    @Override
    public void append(ControllerScreenTextScope scope, Identifier lineId, Component text) {
        Objects.requireNonNull(scope, "scope");
        requireNamespaced(lineId);
        Objects.requireNonNull(text, "text");

        Key key = new Key(scope, lineId);
        ControllerScreenTextSnapshot.Line current = lines.get(key);
        if (current != null && current.text().equals(text)) return;

        lines.put(key, new ControllerScreenTextSnapshot.Line(scope, lineId, text));
        markChanged();
    }

    @Override
    public void remove(ControllerScreenTextScope scope, Identifier lineId) {
        Objects.requireNonNull(scope, "scope");
        requireNamespaced(lineId);
        if (lines.remove(new Key(scope, lineId)) != null) markChanged();
    }

    @Override
    public void clear(ControllerScreenTextScope scope) {
        Objects.requireNonNull(scope, "scope");
        boolean changed = lines.keySet().removeIf(key -> key.scope() == scope);
        if (changed) markChanged();
    }

    public ControllerScreenTextSnapshot snapshot() {
        return new ControllerScreenTextSnapshot(revision, List.copyOf(lines.values()));
    }

    public long revision() {
        return revision;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    private void markChanged() {
        revision++;
        dirty = true;
    }

    private static void requireNamespaced(Identifier lineId) {
        Objects.requireNonNull(lineId, "lineId");
        if (lineId.getNamespace().isBlank()) {
            throw new IllegalArgumentException("lineId must have a namespace");
        }
    }

    private record Key(ControllerScreenTextScope scope, Identifier lineId) {
    }
}
