package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.StructureRuntime;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the final controller-owned structure runtime contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureRuntimeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void newRuntimeStartsUnformedAndDirty() throws Exception {
        MachineControllerBlockEntity controller = controller();

        assertThat(controller.structureSnapshot().formed()).isFalse();
        assertThat(controller.structureSnapshot().version()).isEqualTo(1L);
        assertThat(controller.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void invalidationRequestKeepsThePublishedStructureSnapshotImmutable() throws Exception {
        MachineControllerBlockEntity controller = controller();

        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));
        StructureSnapshot snapshot = controller.structureSnapshot();

        assertThat(snapshot.dirty()).isTrue();
        assertThat(snapshot.criticalChunks()).isUnmodifiable();
        assertThat(snapshot.criticalChunks()).containsExactlyInAnyOrderElementsOf(Set.of());
    }

    @Test
    void structureBoundaryRequiresLevelAndControllerPosition() throws Exception {
        MachineControllerBlockEntity controller = controller();

        assertThatThrownBy(() -> controller.tickStructure(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.handleStructureChunkChanged(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedCheckRequestsDoNotInventAFormationVersion() throws Exception {
        MachineControllerBlockEntity controller = controller();
        long configuredVersion = controller.structureSnapshot().version();

        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));
        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));

        assertThat(controller.structureSnapshot().version()).isEqualTo(configuredVersion);
    }

    @Test
    void displaySnapshotIsReusedUntilStructureStateChanges() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        runtime.reset();
        StructureSnapshot first = runtime.snapshot();

        assertThat(runtime.snapshot()).isSameAs(first);
        runtime.requestCheck();
        StructureSnapshot dirty = runtime.snapshot();
        assertThat(dirty).isNotSameAs(first);
        assertThat(first.dirty()).isFalse();
        assertThat(dirty.dirty()).isTrue();
        runtime.requestCheck();
        assertThat(runtime.snapshot()).isSameAs(dirty);
    }

    @Test
    void diagnosticSettersInvalidateOnlyWhenPublishedValuesChange() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        StructureSnapshot first = runtime.snapshot();
        PortRequirementSpec.Failure failure = missingPort();

        invoke(runtime, "setFormationFailure", failure);
        StructureSnapshot failed = runtime.snapshot();
        assertThat(failed).isNotSameAs(first);
        assertThat(failed.lastFormationFailure()).isEqualTo(failure);
        assertThat(first.lastFormationFailure()).isNull();
        invoke(runtime, "setFormationFailure", missingPort());
        assertThat(runtime.snapshot()).isSameAs(failed);

        invoke(runtime, "setMismatchDiagnostic", "missing port");
        StructureSnapshot diagnosed = runtime.snapshot();
        assertThat(diagnosed).isNotSameAs(failed);
        assertThat(diagnosed.structureMismatchDiagnostic()).isEqualTo("missing port");
        assertThat(failed.structureMismatchDiagnostic()).isNull();
        invoke(runtime, "setMismatchDiagnostic", "missing port");
        assertThat(runtime.snapshot()).isSameAs(diagnosed);

        Object error = new Object();
        invoke(runtime, "setLastStructureError", error);
        StructureSnapshot errored = runtime.snapshot();
        assertThat(errored).isNotSameAs(diagnosed);
        assertThat(errored.lastStructureError()).isSameAs(error);
        assertThat(diagnosed.lastStructureError()).isNull();
        invoke(runtime, "setLastStructureError", error);
        assertThat(runtime.snapshot()).isSameAs(errored);

        invoke(runtime, "setFormationFailure", (Object) null);
        StructureSnapshot clearedFailure = runtime.snapshot();
        assertThat(clearedFailure).isNotSameAs(errored);
        assertThat(clearedFailure.lastFormationFailure()).isNull();
        invoke(runtime, "setMismatchDiagnostic", (Object) null);
        StructureSnapshot clearedDiagnostic = runtime.snapshot();
        assertThat(clearedDiagnostic).isNotSameAs(clearedFailure);
        assertThat(clearedDiagnostic.structureMismatchDiagnostic()).isNull();
        invoke(runtime, "setLastStructureError", (Object) null);
        assertThat(runtime.snapshot()).isNotSameAs(clearedDiagnostic);
        assertThat(runtime.snapshot().lastStructureError()).isNull();
        assertThat(errored.lastFormationFailure()).isEqualTo(failure);
        assertThat(errored.structureMismatchDiagnostic()).isEqualTo("missing port");
        assertThat(errored.lastStructureError()).isSameAs(error);
    }

    @Test
    void publishedWorkInvalidatesEachDisplayFieldWithoutChangingFormationVersion() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        runtime.reset();
        StructureSnapshot first = runtime.snapshot();
        Object work = invoke(runtime, "workSnapshot");

        work = invoke(work, "withDirty", true);
        invoke(runtime, "publishWork", work);
        StructureSnapshot dirty = runtime.snapshot();
        assertThat(dirty).isNotSameAs(first);
        assertThat(dirty.dirty()).isTrue();
        assertThat(first.dirty()).isFalse();

        work = invoke(work, "withFormationFailure", missingPort());
        invoke(runtime, "publishWork", work);
        StructureSnapshot failed = runtime.snapshot();
        assertThat(failed).isNotSameAs(dirty);
        assertThat(failed.lastFormationFailure()).isEqualTo(missingPort());
        assertThat(dirty.lastFormationFailure()).isNull();

        work = invoke(work, "withMismatchDiagnostic", "missing port");
        invoke(runtime, "publishWork", work);
        StructureSnapshot diagnosed = runtime.snapshot();
        assertThat(diagnosed).isNotSameAs(failed);
        assertThat(diagnosed.structureMismatchDiagnostic()).isEqualTo("missing port");
        assertThat(failed.structureMismatchDiagnostic()).isNull();

        work = invoke(work, "withLastStructureError", "error");
        invoke(runtime, "publishWork", work);
        StructureSnapshot errored = runtime.snapshot();
        assertThat(errored).isNotSameAs(diagnosed);
        assertThat(errored.lastStructureError()).isEqualTo("error");
        assertThat(diagnosed.lastStructureError()).isNull();
        assertThat(errored.version()).isEqualTo(first.version());
        invoke(runtime, "publishWork", work);
        assertThat(runtime.snapshot()).isSameAs(errored);
    }

    @Test
    void unloadingAndCriticalChunkChangesKeepOldSnapshotsAndInputSetsImmutable() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        StructureSnapshot first = runtime.snapshot();
        ChunkPos chunk = new ChunkPos(1, 2);
        Set<ChunkPos> chunks = new HashSet<>(Set.of(chunk));

        invoke(runtime, "setCriticalChunks", chunks);
        StructureSnapshot indexed = runtime.snapshot();
        assertThat(indexed).isNotSameAs(first);
        chunks.clear();
        assertThat(runtime.snapshot()).isSameAs(indexed);
        assertThat(indexed.criticalChunks()).containsExactly(chunk).isUnmodifiable();
        assertThat(first.criticalChunks()).isEmpty();
        invoke(runtime, "setCriticalChunks", Set.of(chunk));
        assertThat(runtime.snapshot()).isSameAs(indexed);

        invoke(runtime, "setStructureAreaLoaded", false);
        StructureSnapshot unloaded = runtime.snapshot();
        assertThat(unloaded).isNotSameAs(indexed);
        assertThat(unloaded.structureAreaLoaded()).isFalse();
        assertThat(indexed.structureAreaLoaded()).isTrue();
        invoke(runtime, "setStructureAreaLoaded", false);
        assertThat(runtime.snapshot()).isSameAs(unloaded);
        invoke(runtime, "setStructureAreaLoaded", true);
        assertThat(runtime.snapshot()).isNotSameAs(unloaded);
        assertThat(runtime.snapshot().structureAreaLoaded()).isTrue();
        invoke(runtime, "setCriticalChunks", Set.of());
        assertThat(runtime.snapshot().criticalChunks()).isEmpty();
        assertThat(unloaded.criticalChunks()).containsExactly(chunk);
    }

    @Test
    void formationIdentityChangesInvalidateMachinePatternsOrientationRollAndStage() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()));
        Machine machine = new DynamicMachine(MMCR.id("snapshot_formation"), "snapshot formation", pattern);
        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);
        StructureSnapshot first = runtime.snapshot();

        publishFormation(runtime, machine, pattern, compiled, Direction.UP, Direction.SOUTH, 1);
        StructureSnapshot formed = runtime.snapshot();
        assertThat(formed).isNotSameAs(first);
        assertThat(formed.configuredMachine()).isSameAs(machine);
        assertThat(formed.machine()).isSameAs(machine);
        assertThat(formed.pattern()).isSameAs(pattern);
        assertThat(formed.compiledPattern()).isSameAs(compiled);
        assertThat(formed.formed()).isTrue();
        assertThat(first.formed()).isFalse();
        publishFormation(runtime, machine, pattern, compiled, Direction.UP, Direction.SOUTH, 1);
        assertThat(runtime.snapshot()).isSameAs(formed);

        publishFormation(runtime, machine, pattern, compiled, Direction.DOWN, Direction.SOUTH, 1);
        StructureSnapshot oriented = runtime.snapshot();
        assertThat(oriented).isNotSameAs(formed);
        assertThat(oriented.facing()).isEqualTo(Direction.DOWN);
        assertThat(formed.facing()).isEqualTo(Direction.UP);
        publishFormation(runtime, machine, pattern, compiled, Direction.DOWN, Direction.WEST, 1);
        StructureSnapshot rolled = runtime.snapshot();
        assertThat(rolled).isNotSameAs(oriented);
        assertThat(rolled.rollFacing()).isEqualTo(Direction.WEST);
        assertThat(oriented.rollFacing()).isEqualTo(Direction.SOUTH);
        publishFormation(runtime, machine, pattern, compiled, Direction.DOWN, Direction.WEST, 2);
        StructureSnapshot staged = runtime.snapshot();
        assertThat(staged).isNotSameAs(rolled);
        assertThat(staged.matchedStage()).isEqualTo(2);
        assertThat(rolled.matchedStage()).isEqualTo(1);

        BlockArray replacementPattern = new BlockArray(Map.of());
        publishFormation(runtime, machine, replacementPattern, compiled, Direction.DOWN, Direction.WEST, 2);
        StructureSnapshot repatterned = runtime.snapshot();
        assertThat(repatterned).isNotSameAs(staged);
        assertThat(repatterned.pattern()).isSameAs(replacementPattern);
        assertThat(staged.pattern()).isSameAs(pattern);
        publishFormation(runtime, machine, replacementPattern, null, Direction.DOWN, Direction.WEST, 2);
        assertThat(runtime.snapshot()).isNotSameAs(repatterned);
        assertThat(runtime.snapshot().compiledPattern()).isNull();
        assertThat(repatterned.compiledPattern()).isSameAs(compiled);
    }

    @Test
    void formationPublicationRefreshesSameIdMachineReferencesWithoutInventingAVersion() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        BlockArray pattern = new BlockArray(Map.of());
        Machine machine = new DynamicMachine(MMCR.id("snapshot_machine"), "first", pattern);
        Machine replacement = new DynamicMachine(machine.registryName(), "replacement", pattern);
        publishFormation(runtime, machine, pattern, null, Direction.SOUTH, Direction.SOUTH, 1);
        StructureSnapshot first = runtime.snapshot();

        publishFormation(runtime, replacement, pattern, null, Direction.SOUTH, Direction.SOUTH, 1);
        StructureSnapshot replaced = runtime.snapshot();
        assertThat(replaced).isNotSameAs(first);
        assertThat(replaced.configuredMachine()).isSameAs(replacement);
        assertThat(replaced.machine()).isSameAs(replacement);
        assertThat(replaced.version()).isEqualTo(first.version());
        assertThat(first.configuredMachine()).isSameAs(machine);
        assertThat(first.machine()).isSameAs(machine);

        invoke(runtime, "setMachine", machine);
        StructureSnapshot configured = runtime.snapshot();
        assertThat(configured.configuredMachine()).isSameAs(machine);
        assertThat(configured.machine()).isSameAs(replacement);
        publishFormation(runtime, machine, pattern, null, Direction.SOUTH, Direction.SOUTH, 1);
        assertThat(runtime.snapshot()).isNotSameAs(configured);
        assertThat(runtime.snapshot().machine()).isSameAs(machine);
        assertThat(runtime.snapshot().version()).isEqualTo(configured.version());
    }

    @Test
    void equalButDistinctFormationReferencesInvalidateSnapshotsWithoutChangingFormationIdentity() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        BlockArray pattern = new BlockArray(Map.of());
        Machine machine = new DynamicMachine(MMCR.id("snapshot_equal_references"), "equal references", pattern);
        CompiledMachinePattern compiled = new CompiledMachinePattern(machine, Map.of(), Map.of(), Map.of(), Map.of());
        assertThat(invoke(runtime, "publishFormationState", machine, pattern, compiled,
                Direction.SOUTH, Direction.SOUTH, 1, Map.of())).isEqualTo(true);
        StructureSnapshot first = runtime.snapshot();

        BlockArray replacementPattern = new BlockArray(Map.of());
        assertThat(replacementPattern).isEqualTo(pattern).isNotSameAs(pattern);
        assertThat(invoke(runtime, "publishFormationState", machine, replacementPattern, compiled,
                Direction.SOUTH, Direction.SOUTH, 1, Map.of())).isEqualTo(false);
        StructureSnapshot repatterned = runtime.snapshot();
        assertThat(repatterned).isNotSameAs(first);
        assertThat(repatterned.pattern()).isSameAs(replacementPattern);
        assertThat(repatterned.version()).isEqualTo(first.version());
        assertThat(first.pattern()).isSameAs(pattern);

        CompiledMachinePattern replacementCompiled = new CompiledMachinePattern(machine, Map.of(), Map.of(), Map.of(), Map.of());
        assertThat(replacementCompiled).isEqualTo(compiled).isNotSameAs(compiled);
        assertThat(invoke(runtime, "publishFormationState", machine, replacementPattern, replacementCompiled,
                Direction.SOUTH, Direction.SOUTH, 1, Map.of())).isEqualTo(false);
        StructureSnapshot recompiled = runtime.snapshot();
        assertThat(recompiled).isNotSameAs(repatterned);
        assertThat(recompiled.compiledPattern()).isSameAs(replacementCompiled);
        assertThat(recompiled.version()).isEqualTo(first.version());
        assertThat(repatterned.compiledPattern()).isSameAs(compiled);

        Machine replacementMachine = new DynamicMachine(machine.registryName(), machine.displayNameKey(), pattern);
        assertThat(replacementMachine).isEqualTo(machine).isNotSameAs(machine);
        assertThat(invoke(runtime, "publishFormationState", replacementMachine, replacementPattern, replacementCompiled,
                Direction.SOUTH, Direction.SOUTH, 1, Map.of())).isEqualTo(false);
        StructureSnapshot remachined = runtime.snapshot();
        assertThat(remachined).isNotSameAs(recompiled);
        assertThat(remachined.configuredMachine()).isSameAs(replacementMachine);
        assertThat(remachined.machine()).isSameAs(replacementMachine);
        assertThat(remachined.version()).isEqualTo(first.version());
        assertThat(recompiled.configuredMachine()).isSameAs(machine);
        assertThat(recompiled.machine()).isSameAs(machine);

        assertThat(invoke(runtime, "publishFormationState", replacementMachine, replacementPattern, replacementCompiled,
                Direction.SOUTH, Direction.SOUTH, 1, Map.of())).isEqualTo(false);
        assertThat(runtime.snapshot()).isSameAs(remachined);
    }

    @Test
    void equalButDistinctClientMachinesInvalidateDisplayWithoutChangingFormationVersion() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        BlockArray pattern = new BlockArray(Map.of());
        Machine machine = new DynamicMachine(MMCR.id("snapshot_client_equal_references"), "equal references", pattern);
        Machine replacement = new DynamicMachine(machine.registryName(), machine.displayNameKey(), pattern);
        assertThat(replacement).isEqualTo(machine).isNotSameAs(machine);
        assertThat(invoke(runtime, "publishClientState", machine, false, true)).isEqualTo(true);
        StructureSnapshot first = runtime.snapshot();

        assertThat(invoke(runtime, "publishClientState", replacement, false, true)).isEqualTo(false);
        StructureSnapshot replaced = runtime.snapshot();
        assertThat(replaced).isNotSameAs(first);
        assertThat(replaced.configuredMachine()).isSameAs(replacement);
        assertThat(replaced.machine()).isSameAs(replacement);
        assertThat(replaced.version()).isEqualTo(first.version());
        assertThat(first.configuredMachine()).isSameAs(machine);
        assertThat(first.machine()).isSameAs(machine);
        assertThat(invoke(runtime, "publishClientState", replacement, false, true)).isEqualTo(false);
        assertThat(runtime.snapshot()).isSameAs(replaced);
    }

    @Test
    void directDisplaySettersAndRestoredVersionInvalidateButNoOpsReuse() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        runtime.reset();
        StructureSnapshot first = runtime.snapshot();
        Machine machine = new DynamicMachine(MMCR.id("snapshot_configured"), "configured", new BlockArray(Map.of()));

        invoke(runtime, "setMachine", machine);
        StructureSnapshot configured = runtime.snapshot();
        assertThat(configured).isNotSameAs(first);
        assertThat(first.configuredMachine()).isNull();
        assertThat(configured.configuredMachine()).isSameAs(machine);
        invoke(runtime, "setMachine", machine);
        assertThat(runtime.snapshot()).isSameAs(configured);
        invoke(runtime, "setFormed", true);
        StructureSnapshot formed = runtime.snapshot();
        assertThat(formed).isNotSameAs(configured);
        assertThat(formed.formed()).isTrue();
        assertThat(configured.formed()).isFalse();
        invoke(runtime, "setFormed", true);
        assertThat(runtime.snapshot()).isSameAs(formed);
        invoke(runtime, "setMatchedStructureStage", 2);
        StructureSnapshot staged = runtime.snapshot();
        assertThat(staged).isNotSameAs(formed);
        assertThat(staged.matchedStage()).isEqualTo(2);
        assertThat(formed.matchedStage()).isZero();
        invoke(runtime, "setMatchedStructureStage", 2);
        assertThat(runtime.snapshot()).isSameAs(staged);
        invoke(runtime, "setDirty", true);
        StructureSnapshot dirty = runtime.snapshot();
        assertThat(dirty).isNotSameAs(staged);
        assertThat(dirty.dirty()).isTrue();
        assertThat(staged.dirty()).isFalse();
        invoke(runtime, "setDirty", true);
        assertThat(runtime.snapshot()).isSameAs(dirty);
        invoke(runtime, "restoreVersion", dirty.version() + 10L);
        StructureSnapshot restored = runtime.snapshot();
        assertThat(restored).isNotSameAs(dirty);
        assertThat(restored.version()).isEqualTo(dirty.version() + 10L);
        invoke(runtime, "restoreVersion", restored.version());
        assertThat(runtime.snapshot()).isSameAs(restored);
    }

    @Test
    void clientPublicationInvalidatesDirtyOnlyChangesAndReusesRepeatedState() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        Machine machine = runtime.snapshot().configuredMachine();
        invoke(runtime, "publishClientState", machine, false, true);
        StructureSnapshot clean = runtime.snapshot();
        invoke(runtime, "setDirty", true);
        StructureSnapshot dirty = runtime.snapshot();

        invoke(runtime, "publishClientState", machine, false, true);
        StructureSnapshot published = runtime.snapshot();
        assertThat(published).isNotSameAs(dirty);
        assertThat(published.dirty()).isFalse();
        assertThat(dirty.dirty()).isTrue();
        assertThat(clean.dirty()).isFalse();
        invoke(runtime, "publishClientState", machine, false, true);
        assertThat(runtime.snapshot()).isSameAs(published);
    }

    @Test
    void resetClearsDisplayStateAndForcedResetInvalidatesVersion() throws Exception {
        StructureRuntime runtime = runtimeOf(controller());
        Machine machine = runtime.snapshot().configuredMachine();
        BlockArray pattern = new BlockArray(Map.of());
        publishFormation(runtime, machine, pattern, null, Direction.UP, Direction.WEST, 2);
        invoke(runtime, "setFormationFailure", missingPort());
        invoke(runtime, "setMismatchDiagnostic", "diagnostic");
        invoke(runtime, "setLastStructureError", "error");
        invoke(runtime, "setCriticalChunks", Set.of(new ChunkPos(1, 2)));
        invoke(runtime, "setStructureAreaLoaded", false);
        StructureSnapshot first = runtime.snapshot();

        runtime.reset();
        StructureSnapshot reset = runtime.snapshot();
        assertThat(reset).isNotSameAs(first);
        assertThat(reset.configuredMachine()).isNull();
        assertThat(reset.machine()).isNull();
        assertThat(reset.pattern()).isNull();
        assertThat(reset.compiledPattern()).isNull();
        assertThat(reset.facing()).isNull();
        assertThat(reset.rollFacing()).isEqualTo(Direction.SOUTH);
        assertThat(reset.matchedStage()).isZero();
        assertThat(reset.formed()).isFalse();
        assertThat(reset.dirty()).isFalse();
        assertThat(reset.structureAreaLoaded()).isTrue();
        assertThat(reset.criticalChunks()).isEmpty();
        assertThat(reset.lastFormationFailure()).isNull();
        assertThat(reset.structureMismatchDiagnostic()).isNull();
        assertThat(reset.lastStructureError()).isNull();
        assertThat(first.machine()).isSameAs(machine);
        assertThat(first.pattern()).isSameAs(pattern);
        assertThat(first.rollFacing()).isEqualTo(Direction.WEST);
        assertThat(first.lastFormationFailure()).isEqualTo(missingPort());
        assertThat(first.structureAreaLoaded()).isFalse();
        assertThat(first.criticalChunks()).containsExactly(new ChunkPos(1, 2));
        runtime.reset();
        assertThat(runtime.snapshot()).isSameAs(reset);
        invoke(runtime, "reset", null, true);
        assertThat(runtime.snapshot()).isNotSameAs(reset);
        assertThat(runtime.snapshot().version()).isGreaterThan(reset.version());
    }

    @Test
    void workSnapshotReadsLiveScanCursorAndSchedulingWithoutInvalidatingDisplay() throws Exception {
        MachineControllerBlockEntity controller = controller();
        StructureRuntime runtime = runtimeOf(controller);
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any(),
                new BlockPos(1, 0, 0), new BlockPredicate.Any()));
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(2, false, 0));
        invoke(runtime, "setScan", scan);
        StructureSnapshot display = runtime.snapshot();
        Object before = invoke(runtime, "workSnapshot");
        Object beforeScan = invoke(before, "scan");
        assertThat(invoke(beforeScan, "cursor")).isEqualTo(0);

        scan.step(controller.getLevel(), controller.getBlockPos());
        Object work = invoke(before, "withCheckCounter", 7);
        work = invoke(work, "withNextCheckTick", 20L);
        work = invoke(work, "withPendingInvalidation", true);
        invoke(runtime, "publishWork", work);
        invoke(runtime, "markChunkStateChanged");
        Object after = invoke(runtime, "workSnapshot");

        assertThat(after).isNotSameAs(before);
        assertThat(invoke(invoke(after, "scan"), "cursor")).isEqualTo(scan.cursor()).isEqualTo(1);
        assertThat(invoke(beforeScan, "cursor")).isEqualTo(0);
        assertThat(invoke(after, "checkCounter")).isEqualTo(7);
        assertThat(invoke(after, "nextCheckTick")).isEqualTo(20L);
        assertThat(invoke(after, "pendingInvalidation")).isEqualTo(true);
        assertThat(invoke(after, "chunkStateEpoch")).isEqualTo((long) invoke(before, "chunkStateEpoch") + 1L);
        assertThat(invoke(before, "checkCounter")).isEqualTo(0);
        assertThat(runtime.snapshot()).isSameAs(display);
        invoke(runtime, "clearScan");
        assertThat(invoke(invoke(runtime, "workSnapshot"), "scan")).isNull();
        assertThat(runtime.snapshot()).isSameAs(display);
    }

    @Test
    void clearScanResetsScanSteppedTick() throws Exception {
        MachineControllerBlockEntity controller = controller();
        StructureRuntime runtime = runtimeOf(controller);
        invoke(runtime, "setScanSteppedTick", 123L);

        long steppedBefore = readScanSteppedTick(controller);
        assertThat(steppedBefore).isEqualTo(123L);

        invoke(runtime, "clearScan");

        assertThat(readScan(controller)).isNull();
        assertThat(readScanSteppedTick(controller)).isEqualTo(Long.MIN_VALUE);
    }

    private static StructureRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        java.lang.reflect.Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        java.lang.reflect.Field structureField = MachineControllerRuntime.class.getDeclaredField("structure");
        structureField.setAccessible(true);
        return (StructureRuntime) structureField.get(runtime);
    }

    private static Object invoke(Object target, String methodName, Object... args) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
            method.setAccessible(true);
            return method.invoke(target, args);
        }
        throw new NoSuchMethodException(methodName);
    }

    private static void publishFormation(StructureRuntime runtime, Machine machine, BlockArray pattern,
                                         CompiledMachinePattern compiled, Direction facing, Direction roll, int stage) throws Exception {
        invoke(runtime, "publishFormationState", machine, pattern, compiled, facing, roll, stage, Map.of());
    }

    private static PortRequirementSpec.Failure missingPort() {
        return PortRequirementSpec.builder().min("item_input", 1).build()
                .validate(PortRequirementSpec.PortCounts.empty()).orElseThrow();
    }

    private static Object readScan(MachineControllerBlockEntity controller) throws Exception {
        Object work = workSnapshot(controller);
        java.lang.reflect.Method scanMethod = work.getClass().getDeclaredMethod("scan");
        scanMethod.setAccessible(true);
        return scanMethod.invoke(work);
    }

    private static long readScanSteppedTick(MachineControllerBlockEntity controller) throws Exception {
        Object work = workSnapshot(controller);
        java.lang.reflect.Method tickMethod = work.getClass().getDeclaredMethod("scanSteppedTick");
        tickMethod.setAccessible(true);
        return (long) tickMethod.invoke(work);
    }

    private static Object workSnapshot(MachineControllerBlockEntity controller) throws Exception {
        java.lang.reflect.Method m = MachineControllerBlockEntity.class
                .getDeclaredMethod("structureWorkSnapshotForTesting");
        m.setAccessible(true);
        return m.invoke(controller);
    }

    private static MachineControllerBlockEntity controller() {
        return RuntimeTestFixtures.controller(MMCR.id("test_cube"));
    }
}
