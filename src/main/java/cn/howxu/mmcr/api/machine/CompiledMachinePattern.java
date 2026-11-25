package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import com.google.common.collect.MapMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable per-machine structure data derived from a raw south-facing pattern.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CompiledMachinePattern(
        Machine machine,
        int stageNumber,
        Map<Direction, BlockArray> rotatedPatterns,
        Map<Direction, BoundingBox> boundingBoxes,
        Map<Direction, List<BlockPos>> componentPositions,
        Map<Direction, List<BlockPos>> portPositions,
        Map<Direction, List<BlockPos>> couplerPositions,
        Map<Direction, List<BlockPos>> interfacePositions,
        Map<Direction, List<BlockPos>> networkInterfacePositions,
        List<CompiledDynamicPattern> dynamicPatterns,
        Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements,
        boolean stateSensitive
) {
    // Weak keys use identity equality and let replaced or temporary patterns be reclaimed.
    private static final Map<BlockArray, Map<Integer, ScanPlan>> SCAN_PLAN_CACHE = new MapMaker().weakKeys().makeMap();

    public CompiledMachinePattern {
        if (machine == null) throw new IllegalArgumentException("machine null");
        if (stageNumber < 1) throw new IllegalArgumentException("stageNumber must be positive");
        rotatedPatterns = copyEnumMap(rotatedPatterns);
        boundingBoxes = copyEnumMap(boundingBoxes);
        componentPositions = copyListEnumMap(componentPositions);
        portPositions = copyListEnumMap(portPositions);
        couplerPositions = copyListEnumMap(couplerPositions);
        interfacePositions = copyListEnumMap(interfacePositions);
        networkInterfacePositions = copyListEnumMap(networkInterfacePositions);
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
        modifierReplacements = copyModifierReplacementEnumMap(modifierReplacements);
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions) {
        this(machine, 1, rotatedPatterns, boundingBoxes, componentPositions, portPositions, Map.of(), Map.of(),
                Map.of(), List.of(), Map.of(), false);
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions,
            List<CompiledDynamicPattern> dynamicPatterns) {
        this(machine, 1, rotatedPatterns, boundingBoxes, componentPositions, portPositions, Map.of(), Map.of(),
                Map.of(), dynamicPatterns, Map.of(), false);
    }

    public CompiledMachinePattern(
            Machine machine,
            int stageNumber,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions,
            Map<Direction, List<BlockPos>> couplerPositions,
            Map<Direction, List<BlockPos>> interfacePositions,
            List<CompiledDynamicPattern> dynamicPatterns,
            Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements,
            boolean stateSensitive) {
        this(machine, stageNumber, rotatedPatterns, boundingBoxes, componentPositions, portPositions,
                couplerPositions, interfacePositions, Map.of(), dynamicPatterns, modifierReplacements, stateSensitive);
    }

    public BlockArray rotatedPattern(Direction facing) {
        return rotatedPatterns.get(facing);
    }

    public BoundingBox boundingBox(Direction facing) {
        return boundingBoxes.get(facing);
    }

    public List<BlockPos> componentPositions(Direction facing) {
        return componentPositions.getOrDefault(facing, List.of());
    }

    public List<BlockPos> portPositions(Direction facing) {
        return portPositions.getOrDefault(facing, List.of());
    }

    public List<BlockPos> couplerPositions(Direction facing) {
        return couplerPositions.getOrDefault(facing, List.of());
    }

    public List<BlockPos> interfacePositions(Direction facing) {
        return interfacePositions.getOrDefault(facing, List.of());
    }

    public List<BlockPos> networkInterfacePositions(Direction facing) {
        return networkInterfacePositions.getOrDefault(facing, List.of());
    }

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements(Direction facing) {
        return modifierReplacements.getOrDefault(facing, Map.of());
    }

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements(
        Direction facing, Direction rollFacing) {
        if (!facing.getAxis().isVertical()) return modifierReplacements(facing);
        Map<BlockPos, List<SingleBlockModifierReplacement>> raw = modifierReplacements(Direction.SOUTH);
        Map<BlockPos, List<SingleBlockModifierReplacement>> rotated = new LinkedHashMap<>();
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        for (var entry : raw.entrySet()) {
            BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing, normalizedRoll);
            rotated.put(rotatedPos, List.copyOf(entry.getValue()));
        }
        return Map.copyOf(rotated);
    }

    public ScanPlan scanPlan(Direction facing, int sentinelCount) {
        BlockArray pattern = rotatedPattern(facing);
        if (pattern == null) return ScanPlan.empty();
        return ScanPlan.forPattern(pattern, sentinelCount);
    }

    static void clearScanPlanCache() {
        SCAN_PLAN_CACHE.clear();
    }

    /**
     * Immutable logical lookup order for one rotated pattern. Air positions are decoded on demand.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class ScanPlan {
        private final PositionTable<BlockPredicate> positions;
        private final List<BlockPos> entryPositions;
        private final List<BlockPredicate> entryPredicates;
        private final List<Map.Entry<BlockPos, BlockPredicate>> entries;
        private final int[] sentinelIndexes;
        private final BoundingBox bounds;

        private ScanPlan(BlockArray pattern, int sentinelCount) {
            positions = pattern.positions();
            this.entryPositions = positions.positions();
            this.entryPredicates = positions.orderedValues();
            this.entries = positions.entries();
            this.bounds = pattern.bounds();
            this.sentinelIndexes = sentinelIndexes(positions.size(), sentinelCount);
        }

        private static ScanPlan empty() { return new ScanPlan(new BlockArray(Map.of()), 0); }

        public static ScanPlan forPattern(BlockArray pattern, int sentinelCount) {
            int count = Math.max(0, sentinelCount);
            return SCAN_PLAN_CACHE.computeIfAbsent(pattern, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(count, ignored -> new ScanPlan(pattern, count));
        }

        /** Returns a defensive copy of the geometry retained with this pattern's scan plan. */
        public BoundingBox boundingBox() { return bounds.moved(0, 0, 0); }

        public List<BlockPos> entryPositions() { return entryPositions; }

        public List<BlockPredicate> entryPredicates() { return entryPredicates; }

        public int[] entryOrder() {
            int[] order = new int[entryCount()];
            Arrays.setAll(order, index -> index);
            return order;
        }

        public int[] sentinelIndexes() { return sentinelIndexes.clone(); }

        public boolean[] sentinelMembership() {
            boolean[] membership = new boolean[entryCount()];
            for (int index : sentinelIndexes) membership[index] = true;
            return membership;
        }

        PositionCursor<BlockPredicate> cursor() { return positions.cursor(); }

        int entryCount() { return entries.size(); }

        List<Map.Entry<BlockPos, BlockPredicate>> entries() { return entries; }

        int sentinelCount() { return sentinelIndexes.length; }

        int sentinelAt(int index) { return sentinelIndexes[index]; }

        int firstSentinelAtOrAfter(int index) {
            int found = Arrays.binarySearch(sentinelIndexes, index);
            return found < 0 ? -1 - found : found;
        }

        private static int[] sentinelIndexes(int size, int count) {
            if (size == 0 || count == 0) return new int[0];
            int limit = Math.min(size, count);
            int[] result = new int[limit];
            int middle = size / 2;
            int slot = 0;
            for (int i = 0; i < limit; i++) {
                int regular = (int) ((long) slot * (size - 1) / Math.max(1, limit - 1));
                if (middle < regular) {
                    result[i] = middle;
                    middle = Integer.MAX_VALUE;
                } else {
                    result[i] = regular;
                    if (middle == regular) middle = Integer.MAX_VALUE;
                    slot++;
                }
            }
            return result;
        }
    }

    private static <T> Map<Direction, T> copyEnumMap(Map<Direction, T> source) {
        EnumMap<Direction, T> copy = new EnumMap<>(Direction.class);
        if (source != null) copy.putAll(source);
        return Map.copyOf(copy);
    }

    private static Map<Direction, List<BlockPos>> copyListEnumMap(Map<Direction, List<BlockPos>> source) {
        EnumMap<Direction, List<BlockPos>> copy = new EnumMap<>(Direction.class);
        if (source != null) {
            for (var entry : source.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(copy);
    }

    private static Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> copyModifierReplacementEnumMap(
            Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> source) {
        EnumMap<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> copy = new EnumMap<>(Direction.class);
        if (source != null) {
            for (var entry : source.entrySet()) {
                LinkedHashMap<BlockPos, List<SingleBlockModifierReplacement>> positionCopy = new LinkedHashMap<>();
                for (var positionEntry : entry.getValue().entrySet()) {
                    positionCopy.put(positionEntry.getKey(), List.copyOf(positionEntry.getValue()));
                }
                copy.put(entry.getKey(), Collections.unmodifiableMap(positionCopy));
            }
        }
        return Map.copyOf(copy);
    }
}
