package cn.howxu.mmcr.api.recipe;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that installed input sources cannot change with their capture buffers.
 * @author howxu <dev@howxu.cn>
 */
class InputScopeTest {
    @Test
    void frozen_sources_do_not_follow_mutable_positions_or_lists() {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(1, 2, 3);
        List<BlockPos> sources = new ArrayList<>(List.of(position));
        InputScope scope = new InputScope(InputScope.Kind.ORDINARY, sources);
        position.set(9, 8, 7);
        sources.clear();

        assertThat(scope.sources()).containsExactly(new BlockPos(1, 2, 3));
        assertThatThrownBy(() -> scope.sources().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void explicit_invalid_scopes_are_rejected_instead_of_becoming_legacy() {
        assertThatThrownBy(() -> new InputScope(InputScope.Kind.ISOLATED, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InputScope(InputScope.Kind.LEGACY, List.of(BlockPos.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
