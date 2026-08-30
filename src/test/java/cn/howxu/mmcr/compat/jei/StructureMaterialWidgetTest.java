package cn.howxu.mmcr.compat.jei;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies structure material page timing.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureMaterialWidgetTest {
    @Test
    void changesPageEveryEightSeconds() {
        assertThat(StructureMaterialWidget.pageFor(0L, 10)).isZero();
        assertThat(StructureMaterialWidget.pageFor(7_999L, 10)).isZero();
        assertThat(StructureMaterialWidget.pageFor(8_000L, 10)).isOne();
        assertThat(StructureMaterialWidget.pageFor(16_000L, 10)).isZero();
    }

    @Test
    void onePageDoesNotRotate() {
        assertThat(StructureMaterialWidget.pageFor(0L, 9)).isZero();
        assertThat(StructureMaterialWidget.pageFor(80_000L, 9)).isZero();
    }
}
