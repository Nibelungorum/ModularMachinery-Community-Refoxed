package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.publicapi.controller.JadeText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered, keyed runtime implementation of the custom Jade text API.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JadeTextState implements JadeText {
    private static final int MAX_LINE_ID_LENGTH = 256;

    private final Map<Identifier, Component> lines = new LinkedHashMap<>();

    @Override
    public void append(Identifier lineId, Component text) {
        requireLineId(lineId);
        if (!lines.containsKey(lineId) && lines.size() >= JadeTextSnapshot.MAX_LINES) {
            throw new IllegalArgumentException("Too many Jade text lines");
        }
        lines.put(lineId, copyText(text));
    }

    @Override
    public void appendAfter(Identifier lineId, Identifier afterLineId, Component text) {
        requireLineId(lineId);
        requireLineId(afterLineId);
        Component copiedText = copyText(text);
        if (lineId.equals(afterLineId)) {
            throw new IllegalArgumentException("A Jade text line cannot be appended after itself");
        }
        if (!lines.containsKey(afterLineId)) return;
        if (!lines.containsKey(lineId) && lines.size() >= JadeTextSnapshot.MAX_LINES) {
            throw new IllegalArgumentException("Too many Jade text lines");
        }

        lines.remove(lineId);
        Map<Identifier, Component> ordered = new LinkedHashMap<>();
        for (var entry : lines.entrySet()) {
            ordered.put(entry.getKey(), entry.getValue());
            if (entry.getKey().equals(afterLineId)) ordered.put(lineId, copiedText);
        }
        lines.clear();
        lines.putAll(ordered);
    }

    @Override
    public void replace(Identifier lineId, Component text) {
        requireLineId(lineId);
        Component copiedText = copyText(text);
        if (lines.containsKey(lineId)) lines.put(lineId, copiedText);
    }

    @Override
    public void remove(Identifier lineId) {
        requireLineId(lineId);
        lines.remove(lineId);
    }

    @Override
    public void clear() {
        lines.clear();
    }

    public JadeTextSnapshot snapshot() {
        List<JadeTextSnapshot.Line> snapshot = lines.entrySet().stream()
                .map(entry -> new JadeTextSnapshot.Line(entry.getKey(), entry.getValue()))
                .toList();
        return new JadeTextSnapshot(snapshot);
    }

    private static Component copyText(Component text) {
        return Objects.requireNonNull(text, "text").copy();
    }

    private static void requireLineId(Identifier lineId) {
        Objects.requireNonNull(lineId, "lineId");
        if (lineId.getNamespace().isBlank()) {
            throw new IllegalArgumentException("lineId must have a namespace");
        }
        if (lineId.toString().length() > MAX_LINE_ID_LENGTH) {
            throw new IllegalArgumentException("Jade text line ID is too long");
        }
    }
}
