package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumMap;
import java.util.ArrayList;
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
        List<CompiledDynamicPattern> dynamicPatterns,
        Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements,
        boolean stateSensitive
) {
    private static final Map<ScanPlanKey, ScanPlan> SCAN_PLAN_CACHE = new ConcurrentHashMap<>();

    public CompiledMachinePattern {
        if (machine == null) throw new IllegalArgumentException("machine null");
        if (stageNumber < 1) throw new IllegalArgumentException("stageNumber must be positive");
        rotatedPatterns = copyEnumMap(rotatedPatterns);
        boundingBoxes = copyEnumMap(boundingBoxes);
        componentPositions = copyListEnumMap(componentPositions);
        portPositions = copyListEnumMap(portPositions);
        couplerPositions = copyListEnumMap(couplerPositions);
        interfacePositions = copyListEnumMap(interfacePositions);
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
        modifierReplacements = copyModifierReplacementEnumMap(modifierReplacements);
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions) {
        this(machine, 1, rotatedPatterns, boundingBoxes, componentPositions, portPositions, Map.of(), Map.of(), List.of(), Map.of(), false);
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions,
            List<CompiledDynamicPattern> dynamicPatterns) {
        this(machine, 1, rotatedPatterns, boundingBoxes, componentPositions, portPositions, Map.of(), Map.of(), dynamicPatterns, Map.of(), false);
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
        return SCAN_PLAN_CACHE.computeIfAbsent(new ScanPlanKey(pattern, Math.max(0, sentinelCount)),
                key -> ScanPlan.create(key.pattern(), key.sentinelCount()));
    }

    private record ScanPlanKey(BlockArray pattern, int sentinelCount) { }

    /**
     * Immutable, allocation-free-per-scan lookup order for one rotated pattern.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class ScanPlan {
        private final List<BlockPos> entryPositions;
        private final List<BlockPredicate> entryPredicates;
        private final List<Map.Entry<BlockPos, BlockPredicate>> entries;
        private final int[] entryOrder;
        private final int[] sentinelIndexes;
        private final boolean[] sentinelMembership;
        private static final Map<PatternKey, ScanPlan> STANDALONE_CACHE = new ConcurrentHashMap<>();

        private ScanPlan(List<BlockPos> entryPositions, List<BlockPredicate> entryPredicates,
                         List<Map.Entry<BlockPos, BlockPredicate>> entries, int[] entryOrder,
                         int[] sentinelIndexes, boolean[] sentinelMembership) {
            this.entryPositions = entryPositions;
            this.entryPredicates = entryPredicates;
            this.entries = entries;
            this.entryOrder = entryOrder;
            this.sentinelIndexes = sentinelIndexes;
            this.sentinelMembership = sentinelMembership;
        }

        private static ScanPlan create(BlockArray pattern, int sentinelCount) {
            ArrayList<BlockPos> positions = new ArrayList<>(pattern.pattern().size());
            ArrayList<BlockPredicate> predicates = new ArrayList<>(pattern.pattern().size());
            ArrayList<Map.Entry<BlockPos, BlockPredicate>> entries = new ArrayList<>(pattern.pattern().size());
            for (var entry : pattern.pattern().entrySet()) {
                positions.add(entry.getKey());
                predicates.add(entry.getValue());
                entries.add(Map.entry(entry.getKey(), entry.getValue()));
            }
            List<BlockPos> immutablePositions = List.copyOf(positions);
            List<BlockPredicate> immutablePredicates = List.copyOf(predicates);
            List<Map.Entry<BlockPos, BlockPredicate>> immutableEntries = List.copyOf(entries);
            int[] order = new int[immutableEntries.size()];
            for (int index = 0; index < order.length; index++) order[index] = index;
            int[] sentinels = sentinelIndexes(order.length, sentinelCount);
            boolean[] membership = new boolean[order.length];
            for (int sentinel : sentinels) membership[sentinel] = true;
            return new ScanPlan(immutablePositions, immutablePredicates, immutableEntries, order, sentinels, membership);
        }

        private static ScanPlan empty() {
            return new ScanPlan(List.of(), List.of(), List.of(), new int[0], new int[0], new boolean[0]);
        }

        public static ScanPlan forPattern(BlockArray pattern, int sentinelCount) {
            int count = Math.max(0, sentinelCount);
            return STANDALONE_CACHE.computeIfAbsent(new PatternKey(pattern, count),
                    key -> create(key.pattern(), key.sentinelCount()));
        }

        private record PatternKey(BlockArray pattern, int sentinelCount) { }

        public List<BlockPos> entryPositions() { return entryPositions; }

        public List<BlockPredicate> entryPredicates() { return entryPredicates; }

        public int[] entryOrder() { return entryOrder.clone(); }

        public int[] sentinelIndexes() { return sentinelIndexes.clone(); }

        public boolean[] sentinelMembership() { return sentinelMembership.clone(); }

        int entryCount() { return entries.size(); }

        List<Map.Entry<BlockPos, BlockPredicate>> entries() { return entries; }

        int sentinelCount() { return sentinelIndexes.length; }

        int sentinelAt(int index) { return sentinelIndexes[index]; }

        boolean isSentinel(int index) { return sentinelMembership[index]; }

        private static int[] sentinelIndexes(int size, int count) {
            if (size == 0 || count == 0) return new int[0];
            int limit = Math.min(size, count);
            boolean[] candidates = new boolean[size];
            candidates[0] = true;
            candidates[size - 1] = true;
            candidates[size / 2] = true;
            int slots = Math.min(size, count);
            for (int slot = 0; slot < slots; slot++) {
                int index = (int) ((long) slot * (size - 1) / Math.max(1, slots - 1));
                candidates[index] = true;
            }
            int[] result = new int[limit];
            int length = 0;
            for (int index = 0; index < size && length < limit; index++) {
                if (candidates[index]) result[length++] = index;
            }
            if (length == result.length) return result;
            int[] trimmed = new int[length];
            System.arraycopy(result, 0, trimmed, 0, length);
            return trimmed;
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
