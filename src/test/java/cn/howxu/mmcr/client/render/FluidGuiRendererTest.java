package cn.howxu.mmcr.client.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the pure geometry helpers used by the fluid GUI renderer.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluidGuiRendererTest {

    @Test
    void fluidFillHeightUsesCeilingAndKeepsNonZeroContentVisible() {
        assertThat(FluidGuiRenderer.fillHeight(1, 8000, 61)).isEqualTo(1);
        assertThat(FluidGuiRenderer.fillHeight(4000, 8000, 61)).isEqualTo(31);
        assertThat(FluidGuiRenderer.fillHeight(8000, 8000, 61)).isEqualTo(61);
        assertThat(FluidGuiRenderer.fillHeight(0, 8000, 61)).isZero();
    }

    @Test
    void fluidTileLayoutMatchesLowDragLibTilingForPartialTiles() {
        assertThat(FluidGuiRenderer.tileWidths(20)).containsExactly(16, 4);
        assertThat(FluidGuiRenderer.tileHeights(18)).containsExactly(16, 2);
    }
}
