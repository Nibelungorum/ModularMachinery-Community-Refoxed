package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies shared structure-preview panel state helpers.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewPanelTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void highlights_toggle_before_ready_and_survive_other_controls() {
        try (var panel = new StructurePreviewPanel(machine("panel_highlight_toggle"))) {
            assertThat(panel.isReady()).isFalse();
            assertThat(panel.positionHighlightsEnabled()).isFalse();

            panel.togglePositionHighlights();
            panel.reset();
            panel.selectNextLayer();
            panel.selectPreviousLayer();
            panel.showAllLayers();

            assertThat(panel.isReady()).isFalse();
            assertThat(panel.positionHighlightsEnabled()).isTrue();
            panel.togglePositionHighlights();
            assertThat(panel.positionHighlightsEnabled()).isFalse();
        }
    }

    @Test
    void highlights_are_panel_local_even_when_compilation_is_shared() {
        DynamicMachine machine = machine("panel_highlight_shared");
        var cache = StructurePreviewCompilationCache.instance();
        assertThat(cache.acquire(machine)).isSameAs(cache.acquire(machine));

        try (var first = new StructurePreviewPanel(machine);
                var second = new StructurePreviewPanel(machine)) {
            first.togglePositionHighlights();

            assertThat(first.positionHighlightsEnabled()).isTrue();
            assertThat(second.positionHighlightsEnabled()).isFalse();
            second.togglePositionHighlights();
            first.togglePositionHighlights();
            assertThat(first.positionHighlightsEnabled()).isFalse();
            assertThat(second.positionHighlightsEnabled()).isTrue();
        }
        try (var recreated = new StructurePreviewPanel(machine)) {
            assertThat(recreated.positionHighlightsEnabled()).isFalse();
        }
    }

    @Test
    void closed_panel_ignores_highlight_toggles() {
        var panel = new StructurePreviewPanel(machine("panel_highlight_closed"));
        panel.togglePositionHighlights();
        panel.close();
        panel.togglePositionHighlights();

        assertThat(panel.positionHighlightsEnabled()).isTrue();
    }

    private static DynamicMachine machine(String name) {
        return new DynamicMachine(MMCR.id(name), "machine.preview",
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));
    }

    @Test
    void candidateWindowRotatesEverySecond() {
        assertThat(StructurePreviewPanel.candidateIndex(0, 0L, 4)).isZero();
        assertThat(StructurePreviewPanel.candidateIndex(0, 999L, 4)).isZero();
        assertThat(StructurePreviewPanel.candidateIndex(0, 1_000L, 4)).isOne();
        assertThat(StructurePreviewPanel.candidateIndex(7, 0L, 4)).isEqualTo(-1);
    }

    @Test
    void materialPagesContainEightEntriesAndRotateEveryEightSeconds() {
        assertThat(StructurePreviewPanel.materialPage(0L, 16)).isZero();
        assertThat(StructurePreviewPanel.materialPage(7_999L, 16)).isZero();
        assertThat(StructurePreviewPanel.materialPage(8_000L, 16)).isOne();
    }

    @Test
    void singleStageDoesNotAdvance() {
        assertThat(StructurePreviewPanel.stageIndexAfter(0, 1)).isZero();
    }
}
