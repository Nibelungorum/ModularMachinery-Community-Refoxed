package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.structure.PatternRows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;
import cn.howxu.mmcr.api.machine.definition.ModifierUse;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public record BlockArray(Map<BlockPos, BlockPredicate> pattern, Map<BlockPos, List<String>> tagsByPosition,
                         Map<BlockPos, Character> symbolsByPosition) {

    public BlockArray(Map<BlockPos, BlockPredicate> pattern) {
        this(pattern, Map.of(), Map.of());
    }

    public BlockArray(Map<BlockPos, BlockPredicate> pattern, Map<BlockPos, List<String>> tagsByPosition) {
        this(pattern, tagsByPosition, Map.of());
    }

    public BlockArray {
        Objects.requireNonNull(pattern, "pattern");
        var positions = PackedAirPositions.copy(pattern, (pos, predicate) -> packableAir(predicate));
        pattern = positions;
        if (tagsByPosition == null || tagsByPosition.isEmpty()) tagsByPosition = Map.of();
        else {
            Map<BlockPos, List<String>> tags = new LinkedHashMap<>();
            tagsByPosition.forEach((pos, values) -> tags.put(pos.immutable(), List.copyOf(values)));
            tagsByPosition = Collections.unmodifiableMap(tags);
        }
        symbolsByPosition = PackedAirPositions.copy(symbolsByPosition,
                (pos, symbol) -> symbol != null && packableAir(positions.get(pos)));
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

    public int width() { return isEmpty() ? 0 : bounds().getXSpan(); }
    public int height() { return isEmpty() ? 0 : bounds().getYSpan(); }
    public int length() { return isEmpty() ? 0 : bounds().getZSpan(); }

    // AnyOf's children can be mutable. Two equal alternatives today need not still agree tomorrow.
    private static boolean packableAir(BlockPredicate predicate) {
        return predicate != null && !(predicate instanceof BlockPredicate.AnyOf) && predicate.onlyAir();
    }

    PositionTable<BlockPredicate> positions() { return (PositionTable<BlockPredicate>) pattern; }
    PositionTable<Character> glyphs() { return (PositionTable<Character>) symbolsByPosition; }

    BoundingBox bounds() {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        if (isEmpty()) return new BoundingBox(0, 0, 0, 0, 0, 0);
        positions().visitBounds(p -> {
            b[0] = Math.min(b[0], p.getX()); b[3] = Math.max(b[3], p.getX());
            b[1] = Math.min(b[1], p.getY()); b[4] = Math.max(b[4], p.getY());
            b[2] = Math.min(b[2], p.getZ()); b[5] = Math.max(b[5], p.getZ());
        });
        return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public void forSymbols(Predicate<Character> select, BiConsumer<BlockPos, Character> consumer) {
        glyphs().forValues(select, consumer);
    }

    /** Receives one straight run of equal predicates. {@code length == 1} means a single cell. */
    @FunctionalInterface
    public interface RunVisitor {
        void accept(BlockPos start, Direction step, int length, BlockPredicate value);
    }

    /** Receives one straight run of equal symbols. */
    @FunctionalInterface
    public interface SymbolRunVisitor {
        void accept(BlockPos start, Direction step, int length, char symbol);
    }

    /**
     * Walks the predicate table as runs instead of cells. Wire encoding uses this to send one entry per
     * run plus a palette; the order matches {@link #pattern()} iteration exactly.
     */
    public void forRuns(RunVisitor visitor) {
        positions().visit((start, step, length, value, air) -> visitor.accept(start, step, length, value));
    }

    /** Walks the symbol table in the same orientation as the predicates. */
    public void forSymbolRuns(SymbolRunVisitor visitor) {
        glyphs().visit((start, step, length, symbol, air) -> visitor.accept(start, step, length, symbol == null ? ' ' : symbol));
    }

    /** Applies display-only level choices in encounter order without rebuilding the underlying geometry. */
    public Iterable<Map.Entry<BlockPos, BlockPredicate>> entriesWithOverrides(Map<BlockPos, BlockPredicate> overrides) {
        if (overrides.isEmpty()) return pattern.entrySet();
        return () -> Stream.concat(pattern.entrySet().stream().map(entry -> {
            BlockPredicate replacement = overrides.get(entry.getKey());
            return replacement == null ? entry : Map.entry(entry.getKey(), replacement);
        }), overrides.entrySet().stream().filter(entry -> !pattern.containsKey(entry.getKey())).map(Map.Entry::copyOf)).iterator();
    }

    /** Streaming input for the wire format: predicates precede the independent symbol table.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    public static final class PositionInput {
        private final PackedAirPositions.Writer<BlockPredicate> predicates = new PackedAirPositions.Writer<>();
        private final PackedAirPositions.Writer<Character> symbols = new PackedAirPositions.Writer<>();
        private PositionTable<BlockPredicate> positions;

        public void predicate(BlockPos pos, BlockPredicate predicate) {
            predicates.put(pos, predicate, packableAir(predicate));
        }

        /** Adds a whole straight run in one call; the wire format sends runs rather than cells. */
        public void predicateRun(BlockPos start, Direction step, int length, BlockPredicate predicate) {
            if (length <= 0) throw new IllegalArgumentException("run length must be positive");
            predicates.add(start.immutable(), step, length, predicate, length > 1 && packableAir(predicate));
        }

        private PositionTable<BlockPredicate> positions() {
            if (positions == null) positions = predicates.finish();
            return positions;
        }

        public void symbol(BlockPos pos, char symbol) {
            BlockPredicate predicate = positions().get(pos);
            symbols.put(pos, symbol, packableAir(predicate));
        }

        /** Adds a whole straight symbol run in one call. */
        public void symbolRun(BlockPos start, Direction step, int length, char symbol) {
            if (length <= 0) throw new IllegalArgumentException("run length must be positive");
            BlockPredicate predicate = positions().get(start);
            symbols.add(start.immutable(), step, length, symbol, length > 1 && packableAir(predicate));
        }

        public BlockArray build(Map<BlockPos, List<String>> tags) {
            return new BlockArray(positions(), tags, symbols.finish());
        }
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
        return new BlockArray(pattern, merged, symbolsByPosition);
    }

    public BlockArray withPattern(Map<BlockPos, BlockPredicate> rotated) {
        return new BlockArray(rotated, rotateTags(rotated.keySet()), retainSymbols(rotated.keySet()));
    }

    private Map<BlockPos, Character> retainSymbols(Set<BlockPos> newPositions) {
        if (symbolsByPosition.isEmpty()) return Map.of();
        Map<BlockPos, Character> retained = new LinkedHashMap<>();
        for (BlockPos newPos : newPositions) {
            Character symbol = symbolsByPosition.get(newPos);
            if (symbol != null) retained.put(newPos, symbol);
        }
        return retained;
    }

    private Map<BlockPos, List<String>> rotateTags(Set<BlockPos> newPositions) {
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

    public static BlockArray fromRows(PatternRows rows, Map<Character, BlockPredicate> symbols, Character controller) {
        BlockPos origin = rows.origin(controller != null && symbols.get(controller) != null ? controller : null);
        var pattern = new PackedAirPositions.Writer<BlockPredicate>();
        var glyphs = new PackedAirPositions.Writer<Character>();
        rows.visit(symbols.get(' ') != null, (x, y, z, length, symbol) -> {
            BlockPredicate predicate = symbols.get(symbol);
            if (predicate == null) return;
            BlockPos start = new BlockPos(x - origin.getX(), y - origin.getY(), z - origin.getZ());
            Character glyph = symbol;
            if (packableAir(predicate)) {
                pattern.add(start, Direction.EAST, length, predicate, true);
                glyphs.add(start, Direction.EAST, length, glyph, true);
            } else for (int i = 0; i < length; i++) {
                BlockPos pos = start.relative(Direction.EAST, i);
                pattern.add(pos, Direction.EAST, 1, predicate, false);
                glyphs.add(pos, Direction.EAST, 1, glyph, false);
            }
        });
        return new BlockArray(pattern.finish(), Map.of(), glyphs.finish());
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
     *     .air(new BlockPredicate.Air())
     *     .build();
     * </pre>
     * 每次 {@code pattern(...)} 调用表示一个 z 切片;切片内 row 映射到 y,字符列映射到 x。
     * 所有切片必须拥有相同宽度和高度。
     * 默认跳过空格和未绑定的符号；{@code air(...)} 可以给空格显式绑定匹配条件。
     */
    public static final class Builder {
        private final PatternRows.Builder rows = new PatternRows.Builder();
        private final Map<Character, BlockPredicate> symbols = new LinkedHashMap<>();
        private final MachineStructureRequirements.Builder requirements = MachineStructureRequirements.builder();
        private Character controllerSymbol = 'C';

        public Builder pattern(String... slice) {
            rows.add(slice);
            return this;
        }

        public boolean containsSymbol(char symbol) {
            return rows.contains(symbol);
        }

        public Builder set(char symbol, BlockPredicate predicate) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("Use clear() or a dedicated air predicate for air, ' ' is reserved as default skip");
            }
            symbols.put(symbol, predicate);
            return this;
        }

        public Builder set(char symbol, BlockPredicate predicate, SingleBlockModifierReplacement... modifiers) {
            set(symbol, predicate);
            if (modifiers != null) {
                for (SingleBlockModifierReplacement modifier : modifiers) {
                    requirements.modifier(symbol, modifier);
                }
            }
            return this;
        }

        public Builder set(char symbol, BlockPredicate predicate, ModifierUse... modifiers) {
            set(symbol, predicate);
            if (modifiers != null) {
                for (ModifierUse modifier : modifiers) {
                    requirements.modifier(symbol, modifier.modifierId(), toBlockPredicate(modifier.replacement()));
                }
            }
            return this;
        }

        public MachineStructureRequirements requirements() {
            return requirements(build());
        }

        public MachineStructureRequirements requirements(BlockArray pattern) {
            return requirements.build(pattern);
        }

        public Builder air(BlockPredicate airPredicate) {
            symbols.put(' ', airPredicate);
            return this;
        }

        public Builder controller(char symbol) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("Controller symbol must not be air");
            }
            controllerSymbol = symbol;
            return this;
        }

        public Builder noController() {
            controllerSymbol = null;
            return this;
        }

        public BlockArray build() {
            PatternRows template = rows.freeze();
            if (template.isEmpty()) throw new IllegalStateException("pattern(...) must be provided before build()");
            return fromRows(template, symbols, controllerSymbol);
        }

        private static BlockPredicate toBlockPredicate(
                cn.howxu.mmcr.api.machine.definition.BlockPredicate predicate) {
            if (predicate.isMachineCoupler()) return BlockPredicate.machineCoupler();
            if (predicate.blockState().isPresent()) {
                return new BlockPredicate.OfBlockState(predicate.blockState().get());
            }
            if (predicate.block().isPresent()) {
                return new BlockPredicate.OfBlock(predicate.block().get());
            }
            if (predicate.blockSupplier().isPresent()) {
                return new BlockPredicate.DeferredBlock(predicate.blockSupplier().get());
            }
            if (predicate.tag().isPresent()) {
                return new BlockPredicate.OfTag(predicate.tag().get());
            }
            return new BlockPredicate.AnyOf(predicate.alternatives().stream()
                    .map(Builder::toBlockPredicate).toList());
        }
    }
}
