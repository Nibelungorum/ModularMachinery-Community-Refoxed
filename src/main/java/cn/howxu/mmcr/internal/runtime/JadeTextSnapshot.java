package cn.howxu.mmcr.internal.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of custom Jade text lines.
 *
 * @author howxu <dev@howxu.cn>
 */
public record JadeTextSnapshot(List<Line> lines) {
    public static final int MAX_LINES = 128;

    public JadeTextSnapshot {
        Objects.requireNonNull(lines, "lines");
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Too many Jade text lines");
        }
        lines = List.copyOf(lines.stream()
                .map(line -> Objects.requireNonNull(line, "line"))
                .toList());
    }

    public static JadeTextSnapshot empty() {
        return new JadeTextSnapshot(List.of());
    }

    public record Line(Identifier lineId, Component text) {
        public Line {
            lineId = Objects.requireNonNull(lineId, "lineId");
            text = Objects.requireNonNull(text, "text").copy();
        }

        @Override
        public Component text() {
            return text.copy();
        }
    }
}
