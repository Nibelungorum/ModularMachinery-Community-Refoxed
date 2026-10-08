package cn.howxu.mmcr.compat.jei;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Pure slot geometry for machine recipes, independent of game state.
 * PS: 这是一个离散优化取局部最优解的算法(居然还在追着我杀T_T)
 * @author howxu <dev@howxu.cn>
 */
record RecipeSlotLayout(Orientation orientation, int inputColumns, int outputColumns,
                        List<Position> inputs, List<Position> outputs,
                        @Nullable Arrow arrow, int bottom) {
    private static final int LEFT = 12;
    private static final int TOP = 8;
    private static final int STEP = 18;
    private static final int FULL_COLUMNS = 8;
    private static final int SIDE_COLUMNS = 6;
    private static final int ARROW_WIDTH = 22;
    private static final int ARROW_HEIGHT = 16;
    private static final int ARROW_MARGIN = 2;

    static RecipeSlotLayout forCounts(int inputs, int outputs) {
        if (inputs == 0 && outputs == 0) {
            return new RecipeSlotLayout(Orientation.HORIZONTAL, 3, 3,
                    List.of(), List.of(), null, TOP + STEP);
        }
        if (inputs == 0 || outputs == 0) {
            return new RecipeSlotLayout(Orientation.VERTICAL, FULL_COLUMNS, FULL_COLUMNS,
                    positions(inputs, FULL_COLUMNS, LEFT, TOP, Alignment.CENTER),
                    positions(outputs, FULL_COLUMNS, LEFT, TOP, Alignment.CENTER),
                    null, TOP + rows(Math.max(inputs, outputs), FULL_COLUMNS) * STEP);
        }

        List<Candidate> candidates = IntStream.rangeClosed(1, SIDE_COLUMNS - 1)
                .mapToObj(columns -> new Candidate(columns, rows(inputs, columns),
                        rows(outputs, SIDE_COLUMNS - columns)))
                .toList();
        int height = candidates.stream().mapToInt(Candidate::height).min().orElseThrow();
        if (height >= 4) return vertical(inputs, outputs);

        Comparator<Candidate> preference = height <= 2
                ? Comparator.comparingInt(Candidate::shift).thenComparingInt(Candidate::difference)
                : Comparator.comparingInt(Candidate::difference).thenComparingInt(Candidate::shift);
        Candidate best = candidates.stream().filter(candidate -> candidate.height() == height)
                .min(preference.thenComparingInt(Candidate::columns)).orElseThrow();
        int inputY = TOP + (height - best.inputRows()) * STEP / 2;
        int outputY = TOP + (height - best.outputRows()) * STEP / 2;
        int outputX = LEFT + (best.columns() + 2) * STEP;
        Arrow arrow = new Arrow(LEFT + best.columns() * STEP + (2 * STEP - ARROW_WIDTH) / 2 - 1,
                TOP + (height * STEP - ARROW_HEIGHT) / 2 - 1, ARROW_WIDTH, ARROW_HEIGHT);
        return new RecipeSlotLayout(Orientation.HORIZONTAL, best.columns(), SIDE_COLUMNS - best.columns(),
                positions(inputs, best.columns(), LEFT, inputY, Alignment.RIGHT),
                positions(outputs, SIDE_COLUMNS - best.columns(), outputX, outputY, Alignment.LEFT),
                arrow, TOP + height * STEP);
    }

    private static RecipeSlotLayout vertical(int inputs, int outputs) {
        int inputRows = rows(inputs, FULL_COLUMNS);
        int bandHeight = ARROW_WIDTH + ARROW_MARGIN * 2;
        int outputY = TOP + inputRows * STEP + bandHeight;
        Arrow arrow = new Arrow(LEFT + (FULL_COLUMNS * STEP - ARROW_HEIGHT) / 2 - 1,
                TOP + inputRows * STEP - 1 + ARROW_MARGIN, ARROW_HEIGHT, ARROW_WIDTH);
        return new RecipeSlotLayout(Orientation.VERTICAL, FULL_COLUMNS, FULL_COLUMNS,
                positions(inputs, FULL_COLUMNS, LEFT, TOP, Alignment.CENTER),
                positions(outputs, FULL_COLUMNS, LEFT, outputY, Alignment.CENTER),
                arrow, outputY + rows(outputs, FULL_COLUMNS) * STEP);
    }

    private static List<Position> positions(int count, int columns, int x, int y, Alignment alignment) {
        List<Position> positions = new ArrayList<>(count);
        for (int first = 0; first < count; first += columns) {
            int rowSize = Math.min(columns, count - first);
            int inset = switch (alignment) {
                case LEFT -> 0;
                case RIGHT -> (columns - rowSize) * STEP;
                case CENTER -> (columns - rowSize) * STEP / 2;
            };
            for (int cell = 0; cell < rowSize; cell++) {
                positions.add(new Position(x + inset + cell * STEP, y + first / columns * STEP));
            }
        }
        return List.copyOf(positions);
    }

    private static int rows(int count, int columns) {
        return count / columns + (count % columns == 0 ? 0 : 1);
    }

    enum Orientation { HORIZONTAL, VERTICAL }
    private enum Alignment { LEFT, RIGHT, CENTER }
    record Position(int x, int y) {}
    record Arrow(int x, int y, int width, int height) {}

    private record Candidate(int columns, int inputRows, int outputRows) {
        int height() { return Math.max(inputRows, outputRows); }
        int difference() { return Math.abs(inputRows - outputRows); }
        int shift() { return Math.abs(columns - 3); }
    }
}
