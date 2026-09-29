package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Machine state snapshot construction tests for the matched-stage field.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineStateSnapshotTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    private static MachineStateSnapshot baseSnapshot(int matchedStage, int stageCount) {
        return new MachineStateSnapshot(
                false, true, false, "", List.<String>of(),
                "", 0, 0, false, "", CraftingStatus.Status.IDLE, "",
                null, 0, 0, 0L, 1L, false, false, 0, 0, 0, 0L,
                List.of(), List.of(),
                matchedStage, stageCount);
    }

    @Test
    void matched_stage_and_stage_count_are_exposed() {
        MachineStateSnapshot snapshot = baseSnapshot(4, 10);

        assertThat(snapshot.matchedStage()).isEqualTo(4);
        assertThat(snapshot.stageCount()).isEqualTo(10);
    }

    @Test
    void matched_stage_defaults_to_zero_for_unformed_snapshot() {
        MachineStateSnapshot snapshot = baseSnapshot(0, 1);

        assertThat(snapshot.matchedStage()).isZero();
        assertThat(snapshot.stageCount()).isEqualTo(1);
    }

    @Test
    void negative_matched_stage_is_rejected() {
        assertThatThrownBy(() -> baseSnapshot(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero_stage_count_is_rejected() {
        assertThatThrownBy(() -> baseSnapshot(4, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
