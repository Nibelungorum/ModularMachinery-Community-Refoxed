package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds reusable structure lookup data from machine definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachinePatternCompiler {

    private MachinePatternCompiler() {
    }

    public static CompiledMachinePattern compile(Machine machine) {
        return compileStage(machine, machine.structureStages().getFirst(), null);
    }

    static CompiledMachinePattern compile(Machine machine, Map<BlockArrayCache.Key, BlockArray> cache) {
        return compileStage(machine, machine.structureStages().getFirst(), cache);
    }

    public static List<CompiledMachinePattern> compileStages(Machine machine, Map<BlockArrayCache.Key, BlockArray> cache) {
        List<CompiledMachinePattern> compiled = new ArrayList<>();
        for (MachineStructureStage stage : machine.structureStages()) {
            compiled.add(compileStage(stage.number() == 1 ? machine : new StageMachine(machine, stage), stage, cache));
        }
        return List.copyOf(compiled);
    }

    private static CompiledMachinePattern compileStage(Machine machine, int stageNumber, BlockArray pattern,
                                                       Map<BlockArrayCache.Key, BlockArray> cache) {
        return compileStage(machine, new MachineStructureStage(stageNumber, pattern, machine.portRequirements(),
                machine.portTierRequirements(), machine.dynamicPatterns(), Map.of(), Map.of()), cache);
    }

    private static CompiledMachinePattern compileStage(Machine parent, MachineStructureStage stage,
                                                       Map<BlockArrayCache.Key, BlockArray> cache) {
        Machine machine = new StageMachine(parent, stage);
        BlockArray pattern = stage.pattern();
        EnumMap<Direction, BlockArray> rotatedPatterns = new EnumMap<>(Direction.class);
        EnumMap<Direction, BoundingBox> boundingBoxes = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> componentPositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> portPositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements = new EnumMap<>(Direction.class);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockArray rotated = cache == null ? BlockArrayCache.get(pattern, facing)
                    : BlockArrayCache.get(cache, pattern, facing);
            rotatedPatterns.put(facing, rotated);
            boundingBoxes.put(facing, boundingBox(rotated));
            componentPositions.put(facing, componentPositions(rotated));
            portPositions.put(facing, portPositions(rotated));
            modifierReplacements.put(facing, rotatedModifierReplacements(stage, facing));
        }

        return new CompiledMachinePattern(stage.number() == 1 ? parent : machine, stage.number(), rotatedPatterns, boundingBoxes, componentPositions, portPositions,
                dynamicPatterns(machine.dynamicPatterns(), cache), modifierReplacements);
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> rotatedModifierReplacements(
            MachineStructureStage stage, Direction facing) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = new java.util.LinkedHashMap<>();
        for (var entry : stage.modifierReplacements().entrySet()) {
            BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing, Direction.SOUTH);
            replacements.put(rotatedPos, entry.getValue().stream().map(replacement -> replacement.copyAt(rotatedPos)).toList());
        }
        return Map.copyOf(replacements);
    }

    private record StageMachine(Machine parent, MachineStructureStage stage) implements Machine {
        @Override public net.minecraft.resources.Identifier registryName() { return parent.registryName(); }
        @Override public BlockArray pattern() { return stage.pattern(); }
        @Override public MachineControllerSpec controller() { return parent.controller(); }
        @Override public MachineAppearanceSpec appearance() { return parent.appearance(); }
        @Override public PortRequirementSpec portRequirements() { return stage.portRequirements(); }
        @Override public PortTierRequirementSpec portTierRequirements() { return stage.portTierRequirements(); }
        @Override public List<DynamicPatternSpec> dynamicPatterns() { return stage.dynamicPatterns(); }
        @Override public List<MachineStructureStage> structureStages() { return List.of(stage); }
    }

    public static Map<net.minecraft.resources.Identifier, CompiledMachinePattern> compileAll(Collection<Machine> machines) {
        java.util.LinkedHashMap<net.minecraft.resources.Identifier, CompiledMachinePattern> compiled = new java.util.LinkedHashMap<>();
        for (Machine machine : machines) {
            compiled.put(machine.registryName(), compile(machine));
        }
        return Map.copyOf(compiled);
    }

    private static BoundingBox boundingBox(BlockArray pattern) {
        if (pattern.isEmpty()) return new BoundingBox(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : pattern.pattern().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<BlockPos> componentPositions(BlockArray pattern) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (var entry : pattern.pattern().entrySet()) {
            if (couldBeComponent(entry.getValue())) positions.add(entry.getKey());
        }
        return List.copyOf(positions);
    }

    private static List<BlockPos> portPositions(BlockArray pattern) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (var entry : pattern.pattern().entrySet()) {
            if (couldBePort(entry.getValue())) positions.add(entry.getKey());
        }
        return List.copyOf(positions);
    }

    private static boolean couldBeComponent(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> of.block() instanceof IOPortBlock
                    || of.block() instanceof ParallelControllerBlock
                    || of.block() instanceof FactorySchedulerBlock;
            case BlockPredicate.AnyOf ignored -> true;
            default -> true;
        };
    }

    private static boolean couldBePort(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> of.block() instanceof IOPortBlock;
            case BlockPredicate.AnyOf ignored -> true;
            default -> true;
        };
    }

    private static List<CompiledDynamicPattern> dynamicPatterns(List<DynamicPatternSpec> specs,
                                                                 Map<BlockArrayCache.Key, BlockArray> cache) {
        ArrayList<CompiledDynamicPattern> compiled = new ArrayList<>();
        for (DynamicPatternSpec spec : specs) compiled.add(dynamicPattern(spec, cache));
        return List.copyOf(compiled);
    }

    private static CompiledDynamicPattern dynamicPattern(DynamicPatternSpec spec,
                                                         Map<BlockArrayCache.Key, BlockArray> cache) {
        EnumMap<Direction, BlockArray> startPatterns = new EnumMap<>(Direction.class);
        EnumMap<Direction, BlockArray> endPatterns = new EnumMap<>(Direction.class);
        EnumMap<Direction, BlockPos> offsetStarts = new EnumMap<>(Direction.class);
        EnumMap<Direction, BlockPos> structureSizeOffsets = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<Direction>> allowedFaces = new EnumMap<>(Direction.class);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            startPatterns.put(facing, cache == null ? BlockArrayCache.get(spec.startPattern(), facing)
                    : BlockArrayCache.get(cache, spec.startPattern(), facing));
            if (spec.endPattern() != null) {
                endPatterns.put(facing, cache == null ? BlockArrayCache.get(spec.endPattern(), facing)
                        : BlockArrayCache.get(cache, spec.endPattern(), facing));
            }
            offsetStarts.put(facing, BlockRotator.rotateSouthTo(spec.offsetStart(), facing));
            structureSizeOffsets.put(facing, BlockRotator.rotateSouthTo(spec.structureSizeOffset(), facing));
            allowedFaces.put(facing, rotatedFaces(spec.allowedFaces(), facing));
        }

        return new CompiledDynamicPattern(spec, startPatterns, endPatterns, offsetStarts, structureSizeOffsets, allowedFaces);
    }

    private static List<Direction> rotatedFaces(java.util.Set<Direction> faces, Direction facing) {
        ArrayList<Direction> rotated = new ArrayList<>();
        for (Direction face : faces) rotated.add(rotateFaceSouthTo(face, facing));
        return List.copyOf(rotated);
    }

    private static Direction rotateFaceSouthTo(Direction face, Direction target) {
        if (face.getAxis().isVertical()) return face;
        Direction current = Direction.SOUTH;
        Direction rotated = face;
        while (current != target) {
            current = current.getCounterClockWise();
            rotated = rotated.getCounterClockWise();
        }
        return rotated;
    }
}
