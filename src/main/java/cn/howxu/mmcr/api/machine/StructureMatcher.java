package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StructureMatcher {

    private StructureMatcher() {}

    public static ScanState beginScan(BlockArray pattern,
                                      Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                      boolean stateSensitive, ScanOptions options) {
        return beginScan(pattern, replacements, stateSensitive, options, null);
    }

    public static ScanState beginScan(BlockArray pattern,
                                      Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                      boolean stateSensitive, ScanOptions options, @Nullable Mismatch previousMismatch) {
        return beginScan(0L, Direction.SOUTH, Direction.SOUTH, 0, pattern, pattern, replacements,
                stateSensitive, options, previousMismatch);
    }

    public static ScanState beginScan(long structureVersion, Direction frontFacing, Direction rollFacing,
                                      int stageNumber, Object patternIdentity, BlockArray pattern,
                                      Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                      boolean stateSensitive, ScanOptions options, @Nullable Mismatch previousMismatch) {
        return beginScan(structureVersion, frontFacing, rollFacing, stageNumber, patternIdentity, pattern,
                replacements, stateSensitive, options, previousMismatch, null, Long.MIN_VALUE);
    }

    public static ScanState beginScan(long structureVersion, Direction frontFacing, Direction rollFacing,
                                      int stageNumber, Object patternIdentity, BlockArray pattern,
                                      Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                      boolean stateSensitive, ScanOptions options, @Nullable Mismatch previousMismatch,
                                      @Nullable CompiledMachinePattern.ScanPlan scanPlan, long chunkStateEpoch) {
        int batchSize = pattern.isEmpty() ? 0
                : (pattern.pattern().size() + options.batchCount() - 1) / options.batchCount();
        CompiledMachinePattern.ScanPlan effectivePlan = scanPlan == null
                ? CompiledMachinePattern.ScanPlan.forPattern(pattern, Math.min(options.sentinelCount(), batchSize)) : scanPlan;
        return new ScanState(structureVersion, frontFacing, rollFacing, stageNumber, patternIdentity,
                effectivePlan, replacements == null ? Map.of() : replacements, stateSensitive, options, previousMismatch,
                chunkStateEpoch);
    }

    public static Optional<Mismatch> firstSentinelMismatch(long structureVersion, Direction frontFacing,
                                                            Direction rollFacing, int stageNumber,
                                                            Object patternIdentity, CompiledMachinePattern.ScanPlan plan,
                                                            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                                            boolean stateSensitive, Level level, BlockPos ctrlPos) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> effectiveReplacements = replacements == null
                ? Map.of() : replacements;
        for (int index = 0; index < plan.sentinelCount(); index++) {
            int entryIndex = plan.sentinelAt(index);
            BlockPos relativePos = plan.entryPositions().get(entryIndex);
            BlockPredicate expected = plan.entryPredicates().get(entryIndex);
            Mismatch mismatch = new Mismatch(relativePos, ctrlPos.offset(relativePos), expected,
                    level.getBlockState(ctrlPos.offset(relativePos)), structureVersion, frontFacing, rollFacing,
                    stageNumber, patternIdentity);
            if (!matchesEntry(expected, mismatch.actualState(), effectiveReplacements.getOrDefault(relativePos, List.of()), stateSensitive)) {
                return Optional.of(mismatch);
            }
        }
        return Optional.empty();
    }

    public enum ScanStatus { IN_PROGRESS, VALID, MISMATCH, INVALIDATED }

    public enum InvalidationReason { VERSION, ORIENTATION, ROLL, STAGE, PATTERN, UNLOADED, REMOVED, TIMEOUT }

    public record ScanOptions(int batchCount, boolean sentinelEnabled, int sentinelCount) {
        public ScanOptions {
            if (batchCount < 1) throw new IllegalArgumentException("batchCount must be positive");
            if (sentinelCount < 0) throw new IllegalArgumentException("sentinelCount must not be negative");
        }

        public static ScanOptions of(int batchCount, boolean sentinelEnabled, int sentinelCount) {
            return new ScanOptions(batchCount, sentinelEnabled, sentinelCount);
        }
    }

    public record ScanResult(ScanStatus status, int checkedEntries, @Nullable Mismatch mismatchValue,
                             @Nullable InvalidationReason invalidation) {
        public boolean inProgress() { return status == ScanStatus.IN_PROGRESS; }
        public Optional<Mismatch> mismatch() { return Optional.ofNullable(mismatchValue); }
    }

    /** Immutable identity captured with one server-thread structure batch. */
    public record ScanIdentity(long structureVersion, Direction frontFacing, Direction rollFacing, int stageNumber,
                               Object patternIdentity, long chunkStateEpoch, int cursor) {
    }

    /** One immutable block-state observation that can be matched without a level. */
    public record ScanEntry(Mismatch mismatch, BlockPredicate matchingExpected,
                            List<BlockPredicate> replacementPredicates) {
        public ScanEntry {
            replacementPredicates = List.copyOf(replacementPredicates);
        }

        private boolean matches(boolean stateSensitive) {
            if (matchingExpected.matches(mismatch.actualState(), stateSensitive)) return true;
            for (BlockPredicate replacement : replacementPredicates) {
                if (replacement.matches(mismatch.actualState(), stateSensitive)) return true;
            }
            return mismatch.expected() instanceof BlockPredicate.Air && mismatch.actualState().isAir();
        }
    }

    /** Immutable server-thread capture of one bounded scan batch. */
    public record ScanBatch(ScanIdentity identity, List<ScanEntry> entries, int nextSentinelCursor,
                            boolean sentinelsChecked, int nextCursor, ScanStatus completionStatus,
                            @Nullable InvalidationReason invalidation, boolean stateSensitive) {
        public ScanBatch {
            entries = List.copyOf(entries);
        }

        public ScanResult match() {
            if (invalidation != null) {
                return new ScanResult(ScanStatus.INVALIDATED, 0, null, invalidation);
            }
            int checked = 0;
            for (ScanEntry entry : entries) {
                checked++;
                if (!entry.matches(stateSensitive)) {
                    return new ScanResult(ScanStatus.MISMATCH, checked, entry.mismatch(), null);
                }
            }
            return new ScanResult(completionStatus, checked, null, null);
        }
    }

    public static final class ScanState {
        private final long structureVersion;
        private final Direction frontFacing;
        private final Direction rollFacing;
        private final int stageNumber;
        private final Object patternIdentity;
        private final CompiledMachinePattern.ScanPlan scanPlan;
        private final Map<BlockPos, List<SingleBlockModifierReplacement>> replacements;
        private final boolean stateSensitive;
        private final ScanOptions options;
        private final int activeSentinelCount;
        private int sentinelCursor;
        private boolean sentinelsChecked;
        private int scanIndex;
        private @Nullable ScanResult result;
        private @Nullable Mismatch previousMismatch;
        private @Nullable InvalidationReason invalidated;
        private @Nullable ScanBatch pendingBatch;

        private ScanState(long structureVersion, Direction frontFacing, Direction rollFacing, int stageNumber,
                          Object patternIdentity, CompiledMachinePattern.ScanPlan scanPlan,
                          Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                          boolean stateSensitive, ScanOptions options, @Nullable Mismatch previousMismatch,
                          long chunkStateEpoch) {
            this.structureVersion = structureVersion;
            this.frontFacing = frontFacing;
            this.rollFacing = rollFacing;
            this.stageNumber = stageNumber;
            this.patternIdentity = patternIdentity;
            this.scanPlan = scanPlan;
            this.replacements = Map.copyOf(replacements);
            this.stateSensitive = stateSensitive;
            this.options = options;
            this.activeSentinelCount = Math.min(scanPlan.sentinelCount(), batchSize());
            this.previousMismatch = previousMismatch != null && previousMismatch.matchesIdentity(
                    structureVersion, frontFacing, rollFacing, stageNumber, patternIdentity)
                    ? previousMismatch : null;
            this.chunkStateEpoch = chunkStateEpoch;
        }

        private final long chunkStateEpoch;

        public int batchSize() {
            return scanPlan.entryCount() == 0 ? 0 : (scanPlan.entryCount() + options.batchCount() - 1) / options.batchCount();
        }

        public int cursor() { return scanIndex; }
        public long structureVersion() { return structureVersion; }
        public Direction frontFacing() { return frontFacing; }
        public Direction rollFacing() { return rollFacing; }
        public int stageNumber() { return stageNumber; }
        public Object patternIdentity() { return patternIdentity; }
        public List<Map.Entry<BlockPos, BlockPredicate>> entries() { return scanPlan.entries(); }
        public int entryCount() { return scanPlan.entryCount(); }
        public long chunkStateEpoch() { return chunkStateEpoch; }
        public @Nullable Mismatch previousMismatch() { return previousMismatch; }
        public @Nullable InvalidationReason invalidated() { return invalidated; }

        public void invalidate(InvalidationReason reason) {
            if (invalidated == null) invalidated = reason;
            pendingBatch = null;
        }

        public ScanResult step(Level level, BlockPos ctrlPos) {
            ScanBatch batch = capture(level, ctrlPos);
            ScanResult scanResult = batch.match();
            apply(batch.identity(), scanResult);
            return scanResult;
        }

        /** Captures the next bounded batch on the server thread without evaluating predicates. */
        public ScanBatch capture(Level level, BlockPos ctrlPos) {
            if (pendingBatch != null) throw new IllegalStateException("Structure scan batch is already pending");
            ScanIdentity identity = new ScanIdentity(structureVersion, frontFacing, rollFacing, stageNumber,
                    patternIdentity, chunkStateEpoch, scanIndex);
            if (invalidated != null) {
                return pendingBatch = new ScanBatch(identity, List.of(), sentinelCursor, sentinelsChecked, scanIndex,
                        ScanStatus.INVALIDATED, invalidated, stateSensitive);
            }
            List<ScanEntry> entries = new java.util.ArrayList<>();
            CapturePredicates memo = new CapturePredicates();
            int budget = batchSize();
            int nextSentinelCursor = sentinelCursor;
            boolean nextSentinelsChecked = sentinelsChecked;
            int nextScanIndex = scanIndex;
            if (previousMismatch != null) {
                entries.add(entryAt(previousMismatch.relativePos(), previousMismatch.expected(), level, ctrlPos, memo));
            }
            if (options.sentinelEnabled() && !nextSentinelsChecked) {
                while (nextSentinelCursor < activeSentinelCount && entries.size() < budget) {
                    int index = scanPlan.sentinelAt(nextSentinelCursor++);
                    BlockPos relativePos = scanPlan.entryPositions().get(index);
                    BlockPredicate expected = scanPlan.entryPredicates().get(index);
                    entries.add(entryAt(relativePos, expected, level, ctrlPos, memo));
                }
                nextSentinelsChecked = nextSentinelCursor == activeSentinelCount;
            }
            while (nextScanIndex < scanPlan.entryCount() && entries.size() < budget) {
                if (sentinelWasChecked(nextScanIndex, nextSentinelCursor, nextSentinelsChecked)) {
                    nextScanIndex++;
                    continue;
                }
                int index = nextScanIndex++;
                BlockPos relativePos = scanPlan.entryPositions().get(index);
                BlockPredicate expected = scanPlan.entryPredicates().get(index);
                entries.add(entryAt(relativePos, expected, level, ctrlPos, memo));
            }
            ScanStatus status = nextScanIndex == scanPlan.entryCount() ? ScanStatus.VALID : ScanStatus.IN_PROGRESS;
            return pendingBatch = new ScanBatch(identity, entries, nextSentinelCursor, nextSentinelsChecked,
                    nextScanIndex, status, null, stateSensitive);
        }

        /** Publishes a completed batch after its result has been accepted on the server thread. */
        public void apply(ScanIdentity identity, ScanResult scanResult) {
            if (pendingBatch == null || pendingBatch.identity() != identity) {
                throw new IllegalStateException("Structure scan result does not match the pending batch");
            }
            ScanBatch batch = pendingBatch;
            pendingBatch = null;
            result = scanResult;
            if (scanResult.status() == ScanStatus.INVALIDATED) return;
            if (scanResult.status() == ScanStatus.MISMATCH) {
                previousMismatch = scanResult.mismatchValue();
                return;
            }
            sentinelCursor = batch.nextSentinelCursor();
            sentinelsChecked = batch.sentinelsChecked();
            scanIndex = batch.nextCursor();
            previousMismatch = null;
        }

        private ScanEntry entryAt(BlockPos relativePos, BlockPredicate expected, Level level, BlockPos ctrlPos,
                                  CapturePredicates memo) {
            Mismatch mismatch = mismatchAt(relativePos, expected, level, ctrlPos,
                    structureVersion, frontFacing, rollFacing, stageNumber, patternIdentity);
            List<SingleBlockModifierReplacement> entryReplacements = replacements.getOrDefault(relativePos, List.of());
            List<BlockPredicate> replacementPredicates = entryReplacements.isEmpty() ? List.of() : entryReplacements.stream()
                    .map(SingleBlockModifierReplacement::getReplacement)
                    .map(predicate -> snapshotPredicate(predicate, memo))
                    .toList();
            return new ScanEntry(mismatch, snapshotPredicate(expected, memo), replacementPredicates);
        }

        private static BlockPredicate snapshotPredicate(BlockPredicate predicate,
                                                        CapturePredicates memo) {
            if (!(predicate instanceof BlockPredicate.AnyOf)
                    && !(predicate instanceof BlockPredicate.DeferredBlock deferred && !deferred.networkInterface())) {
                return predicate;
            }
            BlockPredicate cached = memo.get(predicate);
            if (cached != null) return cached;
            BlockPredicate snapshot = switch (predicate) {
                case BlockPredicate.DeferredBlock deferred when !deferred.networkInterface() ->
                        new BlockPredicate.OfBlock(deferred.supplier().get());
                case BlockPredicate.AnyOf anyOf -> {
                    List<BlockPredicate> children = anyOf.children();
                    List<BlockPredicate> snapshots = null;
                    for (int index = 0; index < children.size(); index++) {
                        BlockPredicate child = children.get(index);
                        BlockPredicate childSnapshot = snapshotPredicate(child, memo);
                        if (childSnapshot != child && snapshots == null) snapshots = new ArrayList<>(children);
                        if (snapshots != null) snapshots.set(index, childSnapshot);
                    }
                    if (snapshots != null) yield new BlockPredicate.AnyOf(List.copyOf(snapshots));
                    List<BlockPredicate> immutableChildren = List.copyOf(children);
                    yield immutableChildren == children ? anyOf : new BlockPredicate.AnyOf(immutableChildren);
                }
                default -> predicate;
            };
            memo.put(predicate, snapshot);
            return snapshot;
        }

        /** @author howxu <dev@howxu.cn> */
        private static final class CapturePredicates {
            private @Nullable IdentityHashMap<BlockPredicate, BlockPredicate> captured;

            @Nullable BlockPredicate get(BlockPredicate predicate) {
                return captured == null ? null : captured.get(predicate);
            }

            void put(BlockPredicate predicate, BlockPredicate snapshot) {
                if (captured == null) captured = new IdentityHashMap<>();
                captured.put(predicate, snapshot);
            }
        }

        private boolean sentinelWasChecked(int index, int cursor, boolean checked) {
            if (!options.sentinelEnabled() || cursor == 0 || !scanPlan.isSentinel(index)) return false;
            return checked || cursor == activeSentinelCount || index < scanPlan.sentinelAt(cursor);
        }

        private static Mismatch mismatchAt(BlockPos relativePos, BlockPredicate expected, Level level, BlockPos ctrlPos,
                                            long structureVersion, Direction frontFacing, Direction rollFacing,
                                            int stageNumber, Object patternIdentity) {
            BlockPos worldPos = ctrlPos.offset(relativePos);
            return new Mismatch(relativePos, worldPos, expected, level.getBlockState(worldPos),
                    structureVersion, frontFacing, rollFacing, stageNumber, patternIdentity);
        }
    }


    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing) {
        return matches(pattern, level, ctrlPos, ctrlFacing, Map.of());
    }

    public static boolean matches(BlockArray pattern, Level level, BlockPos ctrlPos, Direction ctrlFacing,
                                   Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        return matchesRotated(BlockArrayCache.get(pattern, ctrlFacing), level, ctrlPos,
                rotateReplacements(replacements, ctrlFacing));
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> rotateReplacements(
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements, Direction facing) {
        if (replacements == null || replacements.isEmpty()) return Map.of();
        Map<BlockPos, List<SingleBlockModifierReplacement>> rotated = new LinkedHashMap<>();
        for (var entry : replacements.entrySet()) {
            BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing);
            rotated.put(rotatedPos, List.copyOf(entry.getValue()));
        }
        return Map.copyOf(rotated);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Level level, BlockPos ctrlPos) {
        return matchesCompiled(compiled, facing, Direction.SOUTH, level, ctrlPos);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Level level,
                                          BlockPos ctrlPos, boolean stateSensitive) {
        return matchesCompiled(compiled, facing, Direction.SOUTH, level, ctrlPos, stateSensitive);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Direction rollFacing, Level level, BlockPos ctrlPos) {
        return matchesCompiled(compiled, facing, rollFacing, level, ctrlPos, true);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Direction rollFacing,
                                          Level level, BlockPos ctrlPos, boolean stateSensitive) {
        return matchesCompiledLoaded(compiled, facing, rollFacing, level, ctrlPos, Map.of(), stateSensitive);
    }

    public static boolean matchesCompiled(CompiledMachinePattern compiled, Direction facing, Direction rollFacing,
                                          Level level, BlockPos ctrlPos,
                                          Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                          boolean stateSensitive) {
        return matchesCompiledLoaded(compiled, facing, rollFacing, level, ctrlPos, replacements, stateSensitive);
    }

    public static boolean matchesCompiledLoaded(CompiledMachinePattern compiled, Direction facing, Direction rollFacing,
                                                Level level, BlockPos ctrlPos,
                                                Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                                boolean stateSensitive) {
        if (compiled.rotatedPattern(facing) == null) return false;
        Map<BlockPos, List<SingleBlockModifierReplacement>> effectiveReplacements =
                replacements.isEmpty() ? compiled.modifierReplacements(facing, rollFacing) : replacements;
        return matchesRotated(compiled.rotatedPattern(facing), level, ctrlPos,
                effectiveReplacements, stateSensitive);
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos) {
        return matchesRotated(pattern, level, ctrlPos, Map.of());
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos,
                                         Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        return matchesRotated(pattern, level, ctrlPos, replacements, true);
    }

    public static boolean matchesRotated(BlockArray pattern, Level level, BlockPos ctrlPos,
                                         Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                         boolean stateSensitive) {
        if (pattern.isEmpty()) return false;

        return firstMismatch(pattern, level, ctrlPos, replacements, stateSensitive).isEmpty();
    }

    public static Optional<Mismatch> firstMismatch(BlockArray pattern, Level level, BlockPos ctrlPos) {
        return firstMismatch(pattern, level, ctrlPos, Map.of());
    }

    public static Optional<Mismatch> firstMismatch(BlockArray pattern, Level level, BlockPos ctrlPos,
                                                   Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) {
        return firstMismatch(pattern, level, ctrlPos, replacements, true);
    }

    public static Optional<Mismatch> firstMismatch(BlockArray pattern, Level level, BlockPos ctrlPos,
                                                   Map<BlockPos, List<SingleBlockModifierReplacement>> replacements,
                                                   boolean stateSensitive) {
        for (var entry : pattern.pattern().entrySet()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            BlockState actualState = level.getBlockState(worldPos);
            if (!matchesEntry(entry.getValue(), actualState, replacements.getOrDefault(entry.getKey(), List.of()), stateSensitive)) {
                return Optional.of(new Mismatch(entry.getKey(), worldPos, entry.getValue(), actualState,
                        0L, Direction.SOUTH, Direction.SOUTH, 0, pattern));
            }
        }
        return Optional.empty();
    }

    public static LevelResolution resolveLevels(Map<BlockPos, ResourceLocation> levelSlots, Level level, BlockPos ctrlPos) {
        Map<ResourceLocation, MachineLevel> foundLevels = new LinkedHashMap<>();
        for (var entry : levelSlots.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<BlockPos, ResourceLocation> entry) -> entry.getKey().getX())
                        .thenComparingInt(entry -> entry.getKey().getY())
                        .thenComparingInt(entry -> entry.getKey().getZ()))
                .toList()) {
            BlockPos worldPos = ctrlPos.offset(entry.getKey());
            Optional<MachineLevel> actualLevel = MachineLevelRegistry.findLevel(entry.getValue(), level.getBlockState(worldPos));
            if (actualLevel.isEmpty()) {
                return new LevelResolution(Map.of(), new LevelMismatch(entry.getValue(), foundLevels.get(entry.getValue()), null, worldPos));
            }
            MachineLevel actual = actualLevel.get();
            MachineLevel expected = foundLevels.putIfAbsent(entry.getValue(), actual);
            if (expected != null && !expected.equals(actual)) {
                return new LevelResolution(Map.of(), new LevelMismatch(entry.getValue(), expected, actual, worldPos));
            }
        }
        return new LevelResolution(Map.copyOf(foundLevels), null);
    }

    private static boolean matchesEntry(
            BlockPredicate expected,
            BlockState actual,
            List<SingleBlockModifierReplacement> replacements,
            boolean stateSensitive) {
        if (expected.matches(actual, stateSensitive)) return true;
        for (SingleBlockModifierReplacement replacement : replacements) {
            if (replacement.getReplacement() instanceof BlockPredicate.Air && actual.isAir()) return true;
            if (replacement.getReplacement().matches(actual, stateSensitive)) return true;
        }
        return expected instanceof BlockPredicate.Air && actual.isAir();
    }

    public static boolean isAreaLoaded(CompiledMachinePattern compiled, Direction facing, Level level, BlockPos ctrlPos) {
        BoundingBox box = compiled.boundingBox(facing);
        if (box == null) return false;
        int minChunkX = (ctrlPos.getX() + box.minX()) >> 4;
        int maxChunkX = (ctrlPos.getX() + box.maxX()) >> 4;
        int minChunkZ = (ctrlPos.getZ() + box.minZ()) >> 4;
        int maxChunkZ = (ctrlPos.getZ() + box.maxZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    public record Mismatch(BlockPos relativePos, BlockPos worldPos, BlockPredicate expected, BlockState actualState,
                           long structureVersion, Direction frontFacing, Direction rollFacing, int stageNumber,
                           Object patternIdentity) {
        public Mismatch(BlockPos relativePos, BlockPos worldPos, BlockPredicate expected, BlockState actualState) {
            this(relativePos, worldPos, expected, actualState, 0L, Direction.SOUTH, Direction.SOUTH, 0, null);
        }

        private boolean matchesIdentity(long structureVersion, Direction frontFacing, Direction rollFacing,
                                        int stageNumber, Object patternIdentity) {
            return this.structureVersion == structureVersion
                    && this.frontFacing == frontFacing
                    && this.rollFacing == rollFacing
                    && this.stageNumber == stageNumber
                    && Objects.equals(this.patternIdentity, patternIdentity);
        }
    }

    public record LevelResolution(Map<ResourceLocation, MachineLevel> foundLevels, LevelMismatch mismatch) {
        public LevelResolution {
            foundLevels = Map.copyOf(foundLevels);
        }
    }
}
