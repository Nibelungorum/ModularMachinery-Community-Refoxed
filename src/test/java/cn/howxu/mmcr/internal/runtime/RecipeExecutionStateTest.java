package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Receipt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirmed receipt ordering and identity regression tests.
 * @author howxu <dev@howxu.cn>
 */
class RecipeExecutionStateTest {
    private static final ExecutionIdentity FIRST = new ExecutionIdentity(1L, 2L, "base", 3L);

    @Test
    void installed_sequence_zero_is_consumed_and_replays_are_rejected() {
        var state = new RecipeExecutionState(FIRST);
        assertThat(state.consumedSequence()).isEqualTo(-1L);
        assertThat(state.accept(new Receipt(FIRST, 1L, 0L, 0, 0L, false, null))).isTrue();
        assertThat(state.accept(new Receipt(FIRST, 1L, 0L, 8, 8L, false, null))).isFalse();
        assertThat(state.confirmedTick()).isZero();
    }

    @Test
    void actual_backward_progress_is_not_reconstructed_from_paid_attempts() {
        var state = new RecipeExecutionState(FIRST);
        assertThat(state.accept(new Receipt(FIRST, 1L, 1L, 4, 4L, false, null))).isTrue();
        assertThat(state.accept(new Receipt(FIRST, 1L, 2L, 2, 5L, false, null))).isTrue();
        assertThat(state.confirmedTick()).isEqualTo(2);
        assertThat(state.confirmedPaidAttempts()).isEqualTo(5L);
    }

    @Test
    void another_identity_older_revision_and_nonterminal_after_tombstone_are_rejected() {
        var state = new RecipeExecutionState(FIRST);
        assertThat(state.accept(new Receipt(FIRST, 2L, 1L, 3, 3L, false, null))).isTrue();
        assertThat(state.accept(new Receipt(new ExecutionIdentity(1L, 2L, "base", 4L),
                2L, 2L, 0, 0L, false, null))).isFalse();
        assertThat(state.accept(new Receipt(FIRST, 1L, 2L, 8, 8L, false, null))).isFalse();
        assertThat(state.accept(new Receipt(FIRST, 2L, 2L, 3, 3L, true, null))).isTrue();
        assertThat(state.accept(new Receipt(FIRST, 2L, 3L, 4, 4L, false, null))).isFalse();
        assertThat(state.terminal()).isTrue();
        assertThat(state.confirmedTick()).isEqualTo(3);
    }
}
