package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public record BlockArray(Map<BlockPos, BlockPredicate> pattern, Map<BlockPos, List<String>> tags) {

    public BlockArray(Map<BlockPos, BlockPredicate> pattern) {
        this(pattern, Map.of());
    }

    public BlockArray {
        pattern = Map.copyOf(pattern);
        tags = copyTags(tags);
    }

    public @Nullable BlockPredicate get(BlockPos pos) {
        return pattern.get(pos);
    }

    public List<String> tags(BlockPos pos) {
        return tags.getOrDefault(pos, List.of());
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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 流式构造一个 {@link BlockArray},沿用参考项目的 CraftTweaker / KubeJS 风格:
     * <pre>
     * BlockArray.builder()
     *     .pattern(
     *         "XXX", "XIX", "XXX",   // arr1
     *         "XXX", "I I", "XXX",   // arr2
     *         "XXX", "XCX", "XXX")   // arr3
     *     .set('X', casing)
     *     .set('I', ioPort)
     *     .set('C', controller)
     *     .set(' ', new BlockPredicate.Air())
     *     .build();
     * </pre>
     * 每个字符串 3 个字符;每 3 行为一个俯视 arr,相同 row 的 arr1/arr2/arr3 组成一个 y 层。
     * 字符 ' ' (空格) 或未通过 {@code set(...)} 绑定的字符视作空气(不放入 pattern)。
     */
    public static final class Builder {
        private final LinkedHashMap<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        private final Map<BlockPos, List<String>> tags = new LinkedHashMap<>();
        private final Map<Character, BlockPredicate> symbols = new LinkedHashMap<>();
        private List<List<String>> layers = List.of();

        public Builder pattern(String... rows) {
            if (rows.length % 3 != 0) {
                throw new IllegalArgumentException("Rows must be a multiple of 3, got " + rows.length);
            }
            int layerCount = rows.length / 3;
            java.util.List<List<String>> built = new java.util.ArrayList<>(layerCount);
            for (int i = 0; i < layerCount; i++) {
                String[] triple = new String[3];
                System.arraycopy(rows, i * 3, triple, 0, 3);
                List<String> layerRows = List.of(triple[0], triple[1], triple[2]);
                for (int r = 0; r < 3; r++) {
                    if (layerRows.get(r).length() != 3) {
                        throw new IllegalArgumentException("Each row must be exactly 3 chars, got \"" + layerRows.get(r) + "\"");
                    }
                }
                built.add(layerRows);
            }
            this.layers = List.copyOf(built);
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

        public Builder tagged(BlockPos pos, String... tags) {
            this.tags.put(pos, normalizeTags(List.of(tags)));
            return this;
        }

        public BlockArray build() {
            entries.clear();
            if (layers.isEmpty()) {
                throw new IllegalStateException("pattern(...) must be provided before build()");
            }
            BlockPos controller = null;
            int arrCount = layers.size();
            for (int row = 0; row < 3; row++) {
                int y = row - 1;
                for (int arr = 0; arr < arrCount; arr++) {
                    int z = arr - (arrCount - 1) / 2;
                    String chars = layers.get(arr).get(row);
                    for (int col = 0; col < 3; col++) {
                        int x = col - 1;
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
                LinkedHashMap<BlockPos, List<String>> normalizedTags = new LinkedHashMap<>();
                for (var entry : entries.entrySet()) {
                    normalized.put(entry.getKey().subtract(controller), entry.getValue());
                }
                for (var entry : tags.entrySet()) {
                    normalizedTags.put(entry.getKey().subtract(controller), entry.getValue());
                }
                entries.clear();
                entries.putAll(normalized);
                tags.clear();
                tags.putAll(normalizedTags);
            }
            return new BlockArray(Map.copyOf(entries), tags);
        }
    }

    private static Map<BlockPos, List<String>> copyTags(Map<BlockPos, List<String>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<BlockPos, List<String>> copy = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            List<String> tags = normalizeTags(entry.getValue());
            if (!tags.isEmpty()) copy.put(entry.getKey(), tags);
        }
        return Map.copyOf(copy);
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        return tags.stream().filter(tag -> tag != null && !tag.isBlank()).distinct().toList();
    }
}
