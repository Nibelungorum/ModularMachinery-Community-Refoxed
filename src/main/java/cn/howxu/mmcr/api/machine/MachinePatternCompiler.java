package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.port.MachinePort;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.DataStorageBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

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
                machine.portTierRequirements(), machine.dynamicPatterns(), MachineStructureRequirements.EMPTY), cache);
    }

    private static CompiledMachinePattern compileStage(Machine parent, MachineStructureStage stage,
                                                       Map<BlockArrayCache.Key, BlockArray> cache) {
        Machine machine = new StageMachine(parent, stage);
        BlockArray pattern = stage.pattern();
        EnumMap<Direction, BlockArray> rotatedPatterns = new EnumMap<>(Direction.class);
        EnumMap<Direction, BoundingBox> boundingBoxes = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> componentPositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> portPositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> couplerPositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> interfacePositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> networkInterfacePositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements = new EnumMap<>(Direction.class);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockArray rotated = cache == null ? BlockArrayCache.get(pattern, facing)
                    : BlockArrayCache.get(cache, pattern, facing);
            rotatedPatterns.put(facing, rotated);
            boundingBoxes.put(facing, rotated.bounds());
            Set<BlockPos> exceptions = airOverrides(stage, parent, facing, Direction.SOUTH);
            componentPositions.put(facing, candidates(rotated, exceptions, MachinePatternCompiler::couldBeComponent));
            portPositions.put(facing, candidates(rotated, exceptions, MachinePatternCompiler::couldBePort));
            modifierReplacements.put(facing, rotatedModifierReplacements(stage, facing));
            couplerPositions.put(facing, couplerPositions(rotated));
            interfacePositions.put(facing, interfacePositions(rotated));
            networkInterfacePositions.put(facing, networkInterfacePositions(rotated));
        }

        return new CompiledMachinePattern(stage.number() == 1 ? parent : machine, stage.number(), rotatedPatterns, boundingBoxes, componentPositions, portPositions,
                couplerPositions, interfacePositions, networkInterfacePositions, dynamicPatterns(machine.dynamicPatterns(), cache), modifierReplacements,
                stage.stateSensitive());
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> rotatedModifierReplacements(
            MachineStructureStage stage, Direction facing) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> replacements = new LinkedHashMap<>();
        for (var entry : stage.modifierReplacements().entrySet()) {
            BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing, Direction.SOUTH);
            replacements.put(rotatedPos, List.copyOf(entry.getValue()));
        }
        return Map.copyOf(replacements);
    }

    private record StageMachine(Machine parent, MachineStructureStage stage) implements Machine {
        @Override public Identifier registryName() { return parent.registryName(); }
        @Override public BlockArray pattern() { return stage.pattern(); }
        @Override public MachineControllerSpec controller() { return parent.controller(); }
        @Override public MachineAppearanceSpec appearance() { return parent.appearance(); }
        @Override public PortRequirementSpec portRequirements() { return stage.portRequirements(); }
        @Override public PortTierRequirementSpec portTierRequirements() { return stage.portTierRequirements(); }
        @Override public List<DynamicPatternSpec> dynamicPatterns() { return stage.dynamicPatterns(); }
        @Override public List<MachineStructureStage> structureStages() { return List.of(stage); }
        @Override public MachineRole role() { return parent.role(); }
        @Override public Set<Identifier> acceptedModuleIds() { return parent.acceptedModuleIds(); }
        @Override public NetworkInterfaceSpec networkInterface() { return parent.networkInterface(); }
        @Override public MachineBehavior behavior() { return parent.behavior(); }
    }

    public static Map<Identifier, CompiledMachinePattern> compileAll(Collection<Machine> machines) {
        LinkedHashMap<Identifier, CompiledMachinePattern> compiled = new LinkedHashMap<>();
        for (Machine machine : machines) {
            compiled.put(machine.registryName(), compile(machine));
        }
        return Map.copyOf(compiled);
    }

    public static List<BlockPos> positionsExcludingNetworkInterfaces(BlockArray pattern) {
        if (pattern == null) return List.of();
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (var entry : pattern.pattern().entrySet()) {
            if (!couldBeNetworkInterface(entry.getValue())) positions.add(entry.getKey());
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> fallbackCandidates(BlockArray pattern, Machine machine, int stageNumber,
                                                     Direction facing, Direction roll) {
        if (pattern == null) return List.of();
        Set<BlockPos> exceptions = Set.of();
        if (machine != null) {
            for (MachineStructureStage stage : machine.structureStages()) {
                if (stage.number() == stageNumber) {
                    exceptions = airOverrides(stage, machine, facing, roll);
                    break;
                }
            }
        }
        return candidates(pattern, exceptions, predicate -> !couldBeNetworkInterface(predicate));
    }

    private static Set<BlockPos> airOverrides(MachineStructureStage stage, Machine machine, Direction facing, Direction roll) {
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos pos : stage.levelSlots().keySet()) {
            BlockPredicate base = stage.pattern().get(pos);
            if (base != null && base.onlyAir())
                positions.add(BlockRotator.rotateSouthTo(pos, facing, roll));
        }
        var replacements = stage.modifierReplacements();
        if (stage.number() == 1 && machine instanceof DynamicMachine dynamic) {
            addAirReplacements(positions, stage.pattern(), dynamic.modifierReplacements(), facing, roll);
        }
        addAirReplacements(positions, stage.pattern(), replacements, facing, roll);
        return positions;
    }

    private static void addAirReplacements(Set<BlockPos> positions, BlockArray pattern,
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements, Direction facing, Direction roll) {
        replacements.forEach((pos, options) -> {
            BlockPredicate base = pattern.get(pos);
            if (base != null && base.onlyAir() && options.stream().anyMatch(option -> !option.getReplacement().onlyAir()))
                positions.add(BlockRotator.rotateSouthTo(pos, facing, roll));
        });
    }

    private static List<BlockPos> candidates(BlockArray pattern, Set<BlockPos> exceptions, Predicate<BlockPredicate> accepts) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        pattern.positions().visitPointsAndExceptions(exceptions, (pos, predicate) -> {
            if (predicate.onlyAir() ? exceptions.contains(pos) : accepts.test(predicate)) positions.add(pos);
        });
        return List.copyOf(positions);
    }

    private static List<BlockPos> couplerPositions(BlockArray pattern) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        pattern.positions().visitPoints((pos, predicate) -> {
            if (couldBeCoupler(predicate)) positions.add(pos);
        });
        return List.copyOf(positions);
    }

    private static List<BlockPos> interfacePositions(BlockArray pattern) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        pattern.positions().visitPoints((pos, predicate) -> {
            if (couldBeInterface(predicate)) positions.add(pos);
        });
        return List.copyOf(positions);
    }

    private static List<BlockPos> networkInterfacePositions(BlockArray pattern) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        pattern.positions().visitPoints((pos, predicate) -> {
            if (couldBeNetworkInterface(predicate)) positions.add(pos);
        });
        return List.copyOf(positions);
    }

    private static boolean couldBeComponent(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> of.block() instanceof MachinePort
                    && !isNetworkInterfaceBlock(of.block())
                    || of.block() instanceof ParallelControllerBlock
                    || of.block() instanceof FactorySchedulerBlock
                    || of.block() instanceof DataStorageBlock
                    || of.block() instanceof UpgradeBusBlock;
            case BlockPredicate.OfBlockState ofState -> !isNetworkInterfaceBlock(ofState.state().getBlock());
            case BlockPredicate.DeferredBlock deferred -> !couldBeNetworkInterface(deferred);
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream()
                    .anyMatch(MachinePatternCompiler::couldBeComponent);
            default -> true;
        };
    }

    private static boolean couldBeCoupler(BlockPredicate predicate) {
        if (couldBeNetworkInterface(predicate)) return false;
        return switch (predicate) {
            case BlockPredicate.MachineCoupler ignored -> true;
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream().anyMatch(MachinePatternCompiler::couldBeCoupler);
            default -> false;
        };
    }

    private static boolean couldBePort(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> of.block() instanceof MachinePort && !isNetworkInterfaceBlock(of.block());
            case BlockPredicate.OfBlockState ofState -> !isNetworkInterfaceBlock(ofState.state().getBlock());
            case BlockPredicate.DeferredBlock deferred -> !couldBeNetworkInterface(deferred);
            case BlockPredicate.AnyOf anyOf -> !couldBeNetworkInterface(anyOf);
            default -> true;
        };
    }

    private static boolean couldBeNetworkInterface(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> isNetworkInterfaceBlock(of.block());
            case BlockPredicate.OfBlockState ofState -> isNetworkInterfaceBlock(ofState.state().getBlock());
            case BlockPredicate.DeferredBlock deferred -> deferred.networkInterface()
                    || isNetworkInterfaceBlock(deferred.supplier().get());
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream()
                    .anyMatch(MachinePatternCompiler::couldBeNetworkInterface);
            default -> false;
        };
    }

    private static boolean isNetworkInterfaceBlock(Block block) {
        return block != null && block.getClass().getName()
                .equals("cn.howxu.mmcr.internal.block.NetworkInterfaceBlock");
    }

    private static boolean couldBeInterface(BlockPredicate predicate) {
        if (couldBeNetworkInterface(predicate)) return false;
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> of.block() instanceof SmartInterfaceBlock;
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream().anyMatch(MachinePatternCompiler::couldBeInterface);
            default -> false;
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

    private static List<Direction> rotatedFaces(Set<Direction> faces, Direction facing) {
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
