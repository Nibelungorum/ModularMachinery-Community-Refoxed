package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.ArrayList;
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
    public static final int MAX_LINES = 1024;
    public static final int MAX_LINE_ID_LENGTH = 256;
    public static final int MAX_ENCODED_TEXT_BYTES = 64 * 1024;

    private final Map<Key, ControllerScreenTextSnapshot.Line> lines = new LinkedHashMap<>();
    private final Map<Identifier, Component> pendingReplacements = new LinkedHashMap<>();
    private long revision;
    private boolean dirty;

    @Override
    public void append(ControllerScreenTextScope scope, Identifier lineId, Component text) {
        Objects.requireNonNull(scope, "scope");
        requireNamespaced(lineId);
        appendInternal(new Key(scope, lineId), text);
    }

    @Override
    public void appendAfter(ControllerScreenTextScope scope, Identifier lineId, Identifier afterLineId, Component text) {
        Objects.requireNonNull(scope, "scope");
        requireNamespaced(lineId);
        requireNamespaced(afterLineId);
        Objects.requireNonNull(text, "text");
        Key key = new Key(scope, lineId);
        Key afterKey = new Key(scope, afterLineId);
        if (!lines.containsKey(afterKey)) return;
        if (key.equals(afterKey)) {
            throw new IllegalArgumentException("A controller screen text line cannot be appended after itself");
        }

        ControllerScreenTextSnapshot.Line replacement = new ControllerScreenTextSnapshot.Line(
                scope, lineId, text.copy());
        Map<Key, ControllerScreenTextSnapshot.Line> candidate = new LinkedHashMap<>(lines);
        candidate.remove(key);
        Map<Key, ControllerScreenTextSnapshot.Line> ordered = new LinkedHashMap<>();
        for (var entry : candidate.entrySet()) {
            ordered.put(entry.getKey(), entry.getValue());
            if (entry.getKey().equals(afterKey)) ordered.put(key, replacement);
        }
        validateCandidate(ordered);
        if (new ArrayList<>(lines.entrySet()).equals(new ArrayList<>(ordered.entrySet()))) return;

        lines.clear();
        lines.putAll(ordered);
        markChanged();
    }

    @Override
    public void replace(Identifier lineId, Component text) {
        requireNamespaced(lineId);
        pendingReplacements.put(lineId, Objects.requireNonNull(text, "text").copy());
    }

    @Override
    public void remove(ControllerScreenTextScope scope, Identifier lineId) {
        Objects.requireNonNull(scope, "scope");
        requireNamespaced(lineId);
        Key key = new Key(scope, lineId);
        if (lines.remove(key) != null) {
            markChanged();
        }
    }

    @Override
    public void clear(ControllerScreenTextScope scope) {
        Objects.requireNonNull(scope, "scope");
        boolean changed = lines.keySet().removeIf(key -> key.scope() == scope);
        if (changed) markChanged();
    }

    public ControllerScreenTextSnapshot snapshot() {
        return new ControllerScreenTextSnapshot(revision, lines.values().stream()
                .map(line -> new ControllerScreenTextSnapshot.Line(line.scope(), line.lineId(), line.text().copy()))
                .toList());
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

    public void flushReplacements() {
        if (pendingReplacements.isEmpty()) return;
        Map<Key, ControllerScreenTextSnapshot.Line> candidate = new LinkedHashMap<>(lines);
        for (var replacement : pendingReplacements.entrySet()) {
            Key key = findReplacementKey(candidate, replacement.getKey());
            if (key == null) continue;
            ControllerScreenTextSnapshot.Line old = candidate.get(key);
            candidate.put(key, new ControllerScreenTextSnapshot.Line(
                    old.scope(), old.lineId(), replacement.getValue().copy()));
        }
        validateCandidate(candidate);
        boolean changed = !new ArrayList<>(lines.entrySet()).equals(new ArrayList<>(candidate.entrySet()));
        pendingReplacements.clear();
        if (!changed) return;

        lines.clear();
        lines.putAll(candidate);
        markChanged();
    }

    private void markChanged() {
        revision++;
        dirty = true;
    }

    private void appendInternal(Key key, Component text) {
        Objects.requireNonNull(text, "text");

        ControllerScreenTextSnapshot.Line replacement = new ControllerScreenTextSnapshot.Line(
                key.scope(), key.lineId(), text.copy());
        Map<Key, ControllerScreenTextSnapshot.Line> candidate = new LinkedHashMap<>(lines);
        candidate.put(key, replacement);
        validateCandidate(candidate);
        boolean changed = !new ArrayList<>(lines.entrySet()).equals(new ArrayList<>(candidate.entrySet()));
        if (!changed) return;

        lines.clear();
        lines.putAll(candidate);
        markChanged();
    }

    private static Key findReplacementKey(Map<Key, ControllerScreenTextSnapshot.Line> candidate,
                                          Identifier lineId) {
        for (Key key : candidate.keySet()) {
            if (key.scope() == ControllerScreenTextScope.CONTROLLER && key.lineId().equals(lineId)) return key;
        }
        for (Key key : candidate.keySet()) {
            if (key.scope() == ControllerScreenTextScope.OPERATION && key.lineId().equals(lineId)) return key;
        }
        return null;
    }

    private static void requireNamespaced(Identifier lineId) {
        Objects.requireNonNull(lineId, "lineId");
        if (lineId.getNamespace().isBlank()) {
            throw new IllegalArgumentException("lineId must have a namespace");
        }
        if (lineId.toString().length() > MAX_LINE_ID_LENGTH) {
            throw new IllegalArgumentException("Controller screen text line ID is too long");
        }
    }

    private static void validateCandidate(Map<Key, ControllerScreenTextSnapshot.Line> candidate) {
        if (candidate.size() > MAX_LINES) {
            throw new IllegalArgumentException("Too many controller screen text lines");
        }

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            for (ControllerScreenTextSnapshot.Line line : candidate.values()) {
                buffer.writeVarInt(line.scope().ordinal());
                Identifier.STREAM_CODEC.encode(buffer, line.lineId());
                ComponentSerialization.STREAM_CODEC.encode(buffer, line.text());
                if (buffer.writerIndex() > MAX_ENCODED_TEXT_BYTES) {
                    throw new IllegalArgumentException("Encoded controller screen text is too large");
                }
            }
        } finally {
            buffer.release();
        }
    }

    private record Key(ControllerScreenTextScope scope, Identifier lineId) {
    }
}
