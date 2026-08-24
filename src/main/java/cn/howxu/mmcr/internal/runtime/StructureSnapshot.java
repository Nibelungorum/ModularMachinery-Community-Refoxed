package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Immutable structure state published by a controller runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public record StructureSnapshot(
        @Nullable Machine configuredMachine,
        @Nullable Machine machine,
        @Nullable BlockArray pattern,
        @Nullable CompiledMachinePattern compiledPattern,
        @Nullable Direction facing,
        Direction rollFacing,
        int matchedStage,
        boolean formed,
        long version,
        @Nullable Object lastStructureError,
        @Nullable String structureMismatchDiagnostic,
        @Nullable PortRequirementSpec.Failure lastFormationFailure,
        boolean dirty,
        boolean structureAreaLoaded,
        Set<ChunkPos> criticalChunks) {

    public StructureSnapshot {
        rollFacing = rollFacing == null ? Direction.SOUTH : rollFacing;
        criticalChunks = Set.copyOf(criticalChunks == null ? Set.of() : criticalChunks);
    }

    public StructureSnapshot(@Nullable Machine machine, @Nullable BlockArray pattern,
                             @Nullable CompiledMachinePattern compiledPattern, @Nullable Direction facing,
                             Direction rollFacing, int matchedStage, boolean formed, long version,
                             @Nullable Object lastStructureError, @Nullable String structureMismatchDiagnostic,
                             @Nullable PortRequirementSpec.Failure lastFormationFailure, boolean dirty,
                             boolean structureAreaLoaded, Set<ChunkPos> criticalChunks) {
        this(null, machine, pattern, compiledPattern, facing, rollFacing, matchedStage, formed, version,
                lastStructureError, structureMismatchDiagnostic, lastFormationFailure, dirty,
                structureAreaLoaded, criticalChunks);
    }

    public static StructureSnapshot empty() {
        return new StructureSnapshot(null, null, null, null, null, Direction.SOUTH, 0,
                false, 0L, null, null, null, true, true, Set.of());
    }
}
