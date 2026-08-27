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

        ControllerScreenTextSnapshot.Line replacement = new ControllerScreenTextSnapshot.Line(scope, lineId, text.copy());
        Map<Key, ControllerScreenTextSnapshot.Line> candidate = new LinkedHashMap<>(lines);
        candidate.put(key, replacement);
        validateCandidate(candidate);

        lines.put(key, replacement);
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

    private void markChanged() {
        revision++;
        dirty = true;
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
