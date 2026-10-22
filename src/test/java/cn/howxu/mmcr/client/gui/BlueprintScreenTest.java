package cn.howxu.mmcr.client.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies blueprint screen layout and coordinate mapping.
 * @author howxu <dev@howxu.cn>
 */
class BlueprintScreenTest {
    @ParameterizedTest
    @CsvSource({"640, 480, false", "640, 480, true", "220, 140, false", "220, 140, true"})
    void highlightButtonSpansTheLastControlRowWithoutOverlappingExistingAreas(
            int screenWidth, int screenHeight, boolean multipleStages) {
        BlueprintLayout layout = BlueprintScreen.layoutFor(screenWidth, screenHeight, multipleStages);
        BlueprintRect highlight = layout.highlightButton();
        BlueprintRect controls = layout.controls();
        int controlsTop = multipleStages ? 208 : 256;

        // Independent logical bounds: two 60-wide columns, a 6-wide gap, final row at Y=304.
        assertThat(highlight.x()).isEqualTo(layout.left() + Math.round(286 * layout.scale()));
        assertThat(highlight.y()).isEqualTo(layout.top() + Math.round(304 * layout.scale()));
        assertThat(highlight.x() + highlight.width())
                .isEqualTo(layout.left() + Math.round(412 * layout.scale()));
        assertThat(highlight.y() + highlight.height())
                .isEqualTo(layout.top() + Math.round(322 * layout.scale()));
        assertThat(controls.y()).isEqualTo(layout.top() + Math.round(controlsTop * layout.scale()));
        assertThat(controls.y() + controls.height())
                .isEqualTo(layout.top() + Math.round(322 * layout.scale()));
        assertThat(highlight.x()).isGreaterThanOrEqualTo(controls.x());
        assertThat(highlight.y()).isGreaterThanOrEqualTo(controls.y());
        assertThat(highlight.x() + highlight.width()).isLessThanOrEqualTo(controls.x() + controls.width());
        assertThat(highlight.y() + highlight.height()).isEqualTo(controls.y() + controls.height());

        List<BlueprintRect> oldButtons = new ArrayList<>(List.of(layout.layerButtons()));
        if (multipleStages) {
            oldButtons.add(layout.nextStageButton());
            oldButtons.add(layout.previousStageButton());
            assertThat(layout.nextStageButton().width()).isEqualTo(highlight.width());
            assertThat(layout.previousStageButton().width()).isEqualTo(highlight.width());
            assertThat(layout.layerButtons()[2].y()).isLessThan(layout.nextStageButton().y());
            assertThat(layout.nextStageButton().y()).isLessThan(layout.previousStageButton().y());
        } else {
            assertThat(layout.nextStageButton()).isNull();
            assertThat(layout.previousStageButton()).isNull();
        }
        for (BlueprintRect button : oldButtons) {
            assertThat(button.y() + button.height()).isLessThan(highlight.y());
            assertThat(intersects(highlight, button)).isFalse();
        }
        assertThat(intersects(highlight, layout.candidates())).isFalse();
        assertThat(controls.y()).isGreaterThan(layout.candidates().y() + layout.candidates().height());
        if (layout.scale() == 1.0F) {
            assertThat(highlight.width()).isEqualTo(layout.layerButtons()[0].width() * 2 + 6);
        }
    }

    private static boolean intersects(BlueprintRect first, BlueprintRect second) {
        return first.x() < second.x() + second.width() && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height() && first.y() + first.height() > second.y();
    }

    @Test
    void layoutKeepsThePreviewOnTheLeftOfTheInformationColumn() {
        BlueprintLayout layout = BlueprintScreen.layoutFor(640, 360, false);

        assertThat(layout.preview().x()).isLessThan(layout.title().x());
        assertThat(layout.preview().width()).isGreaterThan(layout.title().width());
        assertThat(layout.materials().y()).isGreaterThan(layout.preview().y());
        assertThat(layout.nextStageButton()).isNull();
    }

    @Test
    void layoutUsesSquareMaterialSlotsAndKeepsCandidatesAbovePreviewBottom() {
        BlueprintLayout layout = BlueprintScreen.layoutFor(640, 480, true);

        assertThat(layout.materialSlotSize()).isEqualTo(25);
        assertThat(layout.materialColumns()).isEqualTo(9);
        assertThat(layout.materialSlotGap()).isEqualTo(2);
        assertThat(layout.candidates().y() + layout.candidates().height())
                .isLessThanOrEqualTo(layout.preview().y() + layout.preview().height());
        assertThat(layout.controls().y() + layout.controls().height())
                .isEqualTo(layout.top() + Math.round(BlueprintScreen.baseHeight() * layout.scale()) - 8);
    }

    @Test
    void scrollbarDragMapsToClampedRowOffsets() {
        assertThat(BlueprintScreen.clampScrollOffset(8, 6, 3)).isEqualTo(3);
        assertThat(BlueprintScreen.scrollbarHandleY(3, 6, 3, 10, 60, 12)).isEqualTo(58);
        assertThat(BlueprintScreen.scrollOffsetFromScrollbarY(58, 6, 3, 10, 60, 12, 0)).isEqualTo(3);
    }

    @Test
    void candidateSlotsStartAtTheCandidateRectangle() {
        BlueprintLayout layout = BlueprintScreen.layoutFor(640, 480, true);

        BlueprintRect candidateSlot = BlueprintScreen.slotRect(layout, layout.candidates(), 0);

        assertThat(candidateSlot.x()).isEqualTo(layout.candidates().x());
        assertThat(candidateSlot.y()).isEqualTo(layout.candidates().y());
    }

    @Test
    void nextStageButtonOnlyExistsForMultipleStagesAndSpansTwoColumns() {
        BlueprintLayout layout = BlueprintScreen.layoutFor(640, 360, true);

        assertThat(layout.nextStageButton()).isNotNull();
        assertThat(layout.nextStageButton().width())
                .isEqualTo(layout.layerButtons()[0].width() * 2 + 6);
    }

    @Test
    void localMouseInvertsTheLayoutScale() {
        assertThat(BlueprintScreen.localMouse(130.0, 100, 0.5F)).isEqualTo(60.0);
    }

    @Test
    void previewMouseUsesTheActualScaledViewportCoordinates() {
        BlueprintLayout layout = BlueprintScreen.layoutFor(220, 140, false);

        assertThat(BlueprintScreen.previewMouse(
                layout.preview().x() + layout.preview().width() - 1.0,
                layout.preview().x()))
                .isEqualTo(layout.preview().width() - 1.0);
        assertThat(BlueprintScreen.previewMouse(
                layout.preview().y() + layout.preview().height() - 1.0,
                layout.preview().y()))
                .isEqualTo(layout.preview().height() - 1.0);
    }
}
