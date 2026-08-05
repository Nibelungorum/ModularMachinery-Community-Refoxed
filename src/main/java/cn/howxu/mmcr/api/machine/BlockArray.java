package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public record BlockArray(Map<BlockPos, BlockPredicate> pattern, Map<BlockPos, List<String>> tagsByPosition) {

    public BlockArray(Map<BlockPos, BlockPredicate> pattern) {
        this(pattern, Map.of());
    }

    public BlockArray {
        tagsByPosition = tagsByPosition == null ? Map.of() : Map.copyOf(tagsByPosition);
    }

    public @Nullable BlockPredicate get(BlockPos pos) {
        return pattern.get(pos);
    }

    public List<String> tagsAt(BlockPos pos) {
        return tagsByPosition.getOrDefault(pos, List.of());
    }

    public boolean isEmpty() {
        return pattern.isEmpty();
    }

    public int width() {
        return extent(BlockPos::getX);
    }

    public int height() {
        return extent(BlockPos::getY);
    }

    public int length() {
        return extent(BlockPos::getZ);
    }

    private int extent(ToIntFunction<BlockPos> axis) {
        if (pattern.isEmpty()) return 0;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : pattern.keySet()) {
            int value = axis.applyAsInt(pos);
            if (value < min) min = value;
            if (value > max) max = value;
        }
        return max - min + 1;
    }

    public BlockArray tagged(BlockPos pos, String... tags) {
        if (tags == null || tags.length == 0) return this;
        Map<BlockPos, List<String>> merged = new LinkedHashMap<>(tagsByPosition);
        List<String> existing = merged.getOrDefault(pos, List.of());
        List<String> combined = new ArrayList<>(existing.size() + tags.length);
        combined.addAll(existing);
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) continue;
            if (!combined.contains(tag)) combined.add(tag);
        }
        if (combined.equals(existing)) return this;
        merged.put(pos, Collections.unmodifiableList(combined));
        return new BlockArray(pattern, merged);
    }

    public BlockArray withPattern(Map<BlockPos, BlockPredicate> rotated) {
        return new BlockArray(rotated, rotateTags(rotated.keySet()));
    }

    private Map<BlockPos, List<String>> rotateTags(java.util.Set<BlockPos> newPositions) {
        if (tagsByPosition.isEmpty()) return Map.of();
        Map<BlockPos, BlockPos> inverse = new LinkedHashMap<>();
        for (BlockPos newPos : newPositions) {
            if (tagsByPosition.containsKey(newPos)) inverse.put(newPos, newPos);
        }
        if (inverse.isEmpty()) return Map.of();
        Map<BlockPos, List<String>> rotated = new LinkedHashMap<>();
        for (var entry : inverse.entrySet()) {
            rotated.put(entry.getKey(), tagsByPosition.getOrDefault(entry.getValue(), List.of()));
        }
        return rotated;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 流式构造一个 {@link BlockArray},沿用参考项目的 CraftTweaker / KubeJS 风格:
     * <pre>
     * BlockArray.builder()
     *     .pattern("XXX", "XIX", "XXX") // arr1
     *     .pattern("XXX", "I I", "XXX") // arr2
     *     .pattern("XXX", "XCX", "XXX") // arr3
     *     .set('X', casing)
     *     .set('I', ioPort)
     *     .set('C', controller)
     *     .set(' ', new BlockPredicate.Air())
     *     .build();
     * </pre>
     * 每次 {@code pattern(...)} 调用表示一个 z 切片;切片内 row 映射到 y,字符列映射到 x。
     * 所有切片必须拥有相同宽度和高度。
     * 字符 ' ' (空格) 或未通过 {@code set(...)} 绑定的字符视作空气(不放入 pattern)。
     */
    public static final class Builder {
        private final LinkedHashMap<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        private final Map<Character, BlockPredicate> symbols = new LinkedHashMap<>();
        private List<List<String>> slices = List.of();
        private int width = -1;
        private int height = -1;

        public Builder pattern(String... rows) {
            if (rows.length == 0) {
                throw new IllegalArgumentException("pattern(...) must contain at least one row");
            }
            int sliceWidth = rows[0].length();
            if (sliceWidth == 0) {
                throw new IllegalArgumentException("pattern rows must not be empty");
            }
            for (String row : rows) {
                if (row.length() != sliceWidth) {
                    throw new IllegalArgumentException("All rows in a pattern slice must have the same width");
                }
            }
            if (width != -1 && sliceWidth != width) {
                throw new IllegalArgumentException("All pattern slices must have the same width");
            }
            if (height != -1 && rows.length != height) {
                throw new IllegalArgumentException("All pattern slices must have the same height");
            }

            width = sliceWidth;
            height = rows.length;
            java.util.List<List<String>> built = new java.util.ArrayList<>(this.slices.size() + 1);
            built.addAll(this.slices);
            built.add(List.of(rows));
            this.slices = List.copyOf(built);
            return this;
        }

        public Builder set(char symbol, BlockPredicate predicate) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("Use clear() or a dedicated air predicate for air, ' ' is reserved as default skip");
            }
            symbols.put(symbol, predicate);
            return this;
        }

        public Builder air(BlockPredicate airPredicate) {
            symbols.put(' ', airPredicate);
            return this;
        }

        public BlockArray build() {
            entries.clear();
            if (slices.isEmpty()) {
                throw new IllegalStateException("pattern(...) must be provided before build()");
            }
            BlockPos controller = null;
            int depth = slices.size();
            int xOrigin = width / 2;
            int yOrigin = height / 2;
            int zOrigin = depth / 2;
            for (int row = 0; row < height; row++) {
                int y = row - yOrigin;
                for (int slice = 0; slice < depth; slice++) {
                    int z = slice - zOrigin;
                    String chars = slices.get(slice).get(row);
                    for (int col = 0; col < width; col++) {
                        int x = col - xOrigin;
                        char c = chars.charAt(col);
                        BlockPredicate predicate = symbols.get(c);
                        if (predicate == null) continue; // 空格 / 未注册字符 = 空气,跳过
                        BlockPos pos = new BlockPos(x, y, z);
                        if (c == 'C') controller = pos;
                        entries.put(pos, predicate);
                    }
                }
            }
            if (controller != null && !controller.equals(BlockPos.ZERO)) {
                LinkedHashMap<BlockPos, BlockPredicate> normalized = new LinkedHashMap<>();
                for (var entry : entries.entrySet()) {
                    normalized.put(entry.getKey().subtract(controller), entry.getValue());
                }
                entries.clear();
                entries.putAll(normalized);
            }
            return new BlockArray(Map.copyOf(entries));
        }
    }
}
