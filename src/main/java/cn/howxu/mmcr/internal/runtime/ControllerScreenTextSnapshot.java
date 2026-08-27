package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Immutable ordered view of controller screen text.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ControllerScreenTextSnapshot(long revision, List<Line> lines) {
    public ControllerScreenTextSnapshot {
        if (revision < 0L) throw new IllegalArgumentException("revision must not be negative");
        lines = List.copyOf(lines == null ? List.of() : lines);
    }

    /**
     * One immutable controller screen text line.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record Line(ControllerScreenTextScope scope, Identifier lineId, Component text) {
        public Line {
            Objects.requireNonNull(scope, "scope");
            requireNamespaced(lineId);
            Objects.requireNonNull(text, "text");
        }

        private static void requireNamespaced(Identifier lineId) {
            Objects.requireNonNull(lineId, "lineId");
            if (lineId.getNamespace().isBlank()) {
                throw new IllegalArgumentException("lineId must have a namespace");
            }
        }
    }
}
