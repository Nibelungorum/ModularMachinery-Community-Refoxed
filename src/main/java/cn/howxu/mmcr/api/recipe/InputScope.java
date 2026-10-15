package cn.howxu.mmcr.api.recipe;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * Frozen material input sources selected when a recipe starts.
 *
 * @author howxu <dev@howxu.cn>
 */
public record InputScope(Kind kind, List<BlockPos> sources) {
    public enum Kind { LEGACY, ORDINARY, ISOLATED }

    public InputScope {
        Objects.requireNonNull(kind, "kind");
        sources = sources.stream().map(BlockPos::immutable).distinct().toList();
        if (kind == Kind.LEGACY && !sources.isEmpty()
                || kind == Kind.ISOLATED && sources.size() != 1) {
            throw new IllegalArgumentException("Invalid input scope sources");
        }
    }

    public static InputScope legacy() {
        return new InputScope(Kind.LEGACY, List.of());
    }
}
