package cn.howxu.mmcr.compat.jei;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static cn.howxu.mmcr.compat.jei.RecipeSlotLayout.Orientation.HORIZONTAL;
import static cn.howxu.mmcr.compat.jei.RecipeSlotLayout.Orientation.VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Behavioral examples and collision checks for adaptive recipe geometry.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeSlotLayoutTest {
    @ParameterizedTest
    @CsvSource({
            "1,1,HORIZONTAL,3,3", "2,1,HORIZONTAL,3,3", "3,1,HORIZONTAL,3,3",
            "4,1,HORIZONTAL,4,2", "5,1,HORIZONTAL,5,1", "4,3,HORIZONTAL,3,3",
            "2,3,HORIZONTAL,3,3", "4,6,HORIZONTAL,3,3", "7,1,HORIZONTAL,4,2",
            "9,10,VERTICAL,8,8", "12,13,VERTICAL,8,8", "24,1,VERTICAL,8,8",
            "24,7,VERTICAL,8,8", "24,2,VERTICAL,8,8", "1,24,VERTICAL,8,8",
            "4,19,VERTICAL,8,8", "2,12,HORIZONTAL,1,5", "5,3,HORIZONTAL,3,3",
            "5,4,HORIZONTAL,3,3", "5,5,HORIZONTAL,3,3", "6,6,HORIZONTAL,3,3",
            "8,1,HORIZONTAL,4,2", "9,1,HORIZONTAL,5,1", "10,1,HORIZONTAL,5,1",
            "8,8,HORIZONTAL,3,3", "12,12,VERTICAL,8,8", "12,7,VERTICAL,8,8",
            "12,3,HORIZONTAL,5,1", "4,2,HORIZONTAL,4,2", "1,64,VERTICAL,8,8"
    })
    void choosesTheAgreedDistribution(int inputCount, int outputCount,
            RecipeSlotLayout.Orientation orientation, int inputColumns, int outputColumns) {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(inputCount, outputCount);
        assertThat(layout.orientation()).isEqualTo(orientation);
        assertThat(layout.inputColumns()).isEqualTo(inputColumns);
        assertThat(layout.outputColumns()).isEqualTo(outputColumns);
        assertThat(layout.inputs()).hasSize(inputCount);
        assertThat(layout.outputs()).hasSize(outputCount);
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3"})
    void smallGroupsTouchTheArrowSideOfTheirRegions(int count) {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(count, count);
        assertThat(layout.inputs().getLast().x()).isEqualTo(48);
        assertThat(layout.outputs().getFirst().x()).isEqualTo(102);
        assertThat(layout.arrow()).isEqualTo(new RecipeSlotLayout.Arrow(72, 8, 22, 16));
    }

    @Test
    void shorterGroupAndArrowShareTheTwoRowCenter() {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(4, 3);
        assertThat(layout.inputs()).extracting(RecipeSlotLayout.Position::x, RecipeSlotLayout.Position::y)
                .containsExactly(tuple(12, 8), tuple(30, 8), tuple(48, 8), tuple(48, 26));
        assertThat(layout.outputs()).extracting(RecipeSlotLayout.Position::x, RecipeSlotLayout.Position::y)
                .containsExactly(tuple(102, 17), tuple(120, 17), tuple(138, 17));
        assertThat(layout.arrow()).isEqualTo(new RecipeSlotLayout.Arrow(72, 17, 22, 16));
    }

    @Test
    void asymmetricOneRowLayoutStartsOutputsNextToTheArrow() {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(4, 2);
        assertThat(layout.outputs()).extracting(RecipeSlotLayout.Position::x)
                .containsExactly(120, 138);
        assertThat(layout.arrow()).isEqualTo(new RecipeSlotLayout.Arrow(90, 8, 22, 16));
    }

    @Test
    void threeRowLayoutBalancesBothGroupsAndRightAlignsTheInputTail() {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(12, 3);
        assertThat(layout.inputs().subList(10, 12))
                .extracting(RecipeSlotLayout.Position::x, RecipeSlotLayout.Position::y)
                .containsExactly(tuple(66, 44), tuple(84, 44));
        assertThat(layout.outputs()).extracting(RecipeSlotLayout.Position::x, RecipeSlotLayout.Position::y)
                .containsExactly(tuple(138, 8), tuple(138, 26), tuple(138, 44));
        assertThat(layout.arrow()).isEqualTo(new RecipeSlotLayout.Arrow(108, 26, 22, 16));
    }

    @Test
    void verticalRowsAndSingleSlotTailsAreIndependentlyCentered() {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(24, 7);
        assertThat(layout.orientation()).isEqualTo(VERTICAL);
        assertThat(layout.outputs().getFirst()).isEqualTo(new RecipeSlotLayout.Position(21, 88));
        assertThat(layout.outputs().getLast()).isEqualTo(new RecipeSlotLayout.Position(129, 88));
        assertThat(layout.arrow()).isEqualTo(new RecipeSlotLayout.Arrow(75, 63, 16, 22));
        RecipeSlotLayout uneven = RecipeSlotLayout.forCounts(9, 10);
        assertThat(uneven.inputs().getLast()).isEqualTo(new RecipeSlotLayout.Position(75, 26));
        assertThat(uneven.outputs().subList(8, 10)).extracting(RecipeSlotLayout.Position::x)
                .containsExactly(66, 84);
    }

    @Test
    void sixtyFourOutputsRemainEightCompleteRows() {
        RecipeSlotLayout layout = RecipeSlotLayout.forCounts(1, 64);
        assertThat(layout.outputs()).hasSize(64);
        assertThat(layout.outputs()).extracting(RecipeSlotLayout.Position::y)
                .containsOnly(52, 70, 88, 106, 124, 142, 160, 178);
        assertThat(layout.outputs().getLast()).isEqualTo(new RecipeSlotLayout.Position(138, 178));
        assertThat(layout.bottom()).isEqualTo(196);
    }

    @Test
    void emptySidesDoNotConsumeAnArrowBand() {
        RecipeSlotLayout empty = RecipeSlotLayout.forCounts(0, 0);
        assertThat(empty.orientation()).isEqualTo(HORIZONTAL);
        assertThat(empty.inputs()).isEmpty();
        assertThat(empty.outputs()).isEmpty();
        assertThat(empty.arrow()).isNull();
        assertThat(empty.bottom()).isEqualTo(26);
        for (RecipeSlotLayout single : List.of(RecipeSlotLayout.forCounts(0, 9),
                RecipeSlotLayout.forCounts(9, 0))) {
            List<RecipeSlotLayout.Position> slots = single.inputs().isEmpty()
                    ? single.outputs() : single.inputs();
            assertThat(single.arrow()).isNull();
            assertThat(slots.getLast()).isEqualTo(new RecipeSlotLayout.Position(75, 26));
            assertThat(single.bottom()).isEqualTo(44);
        }
    }

    @Test
    void allCountsUpToSixtyFourHaveCompleteCollisionFreeGeometry() {
        for (int inputs = 0; inputs <= 64; inputs++) {
            for (int outputs = 0; outputs <= 64; outputs++) {
                RecipeSlotLayout layout = RecipeSlotLayout.forCounts(inputs, outputs);
                assertThat(layout.inputs()).hasSize(inputs);
                assertThat(layout.outputs()).hasSize(outputs);
                List<Box> boxes = new ArrayList<>();
                List<RecipeSlotLayout.Position> slots = new ArrayList<>(layout.inputs());
                slots.addAll(layout.outputs());
                for (RecipeSlotLayout.Position slot : slots) {
                    Box box = new Box(slot.x() - 1, slot.y() - 1, 18, 18);
                    assertThat(box.x()).isBetween(11, 137);
                    assertThat(box.y()).isGreaterThanOrEqualTo(7);
                    assertThat(box.y() + box.height()).isLessThanOrEqualTo(layout.bottom());
                    boxes.add(box);
                }
                if (layout.arrow() != null) {
                    RecipeSlotLayout.Arrow arrow = layout.arrow();
                    boxes.add(new Box(arrow.x(), arrow.y(), arrow.width(), arrow.height()));
                }
                for (int first = 0; first < boxes.size(); first++) {
                    for (int second = first + 1; second < boxes.size(); second++) {
                        assertThat(boxes.get(first).overlaps(boxes.get(second)))
                                .as("%s:%s rectangles %s and %s", inputs, outputs, first, second).isFalse();
                    }
                }
            }
        }
    }

    private record Box(int x, int y, int width, int height) {
        boolean overlaps(Box other) {
            return x < other.x + other.width && other.x < x + width
                    && y < other.y + other.height && other.y < y + height;
        }
    }
}
