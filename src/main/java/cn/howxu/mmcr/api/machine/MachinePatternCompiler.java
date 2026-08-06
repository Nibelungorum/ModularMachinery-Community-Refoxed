package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.block.IOPortBlock;
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
        EnumMap<Direction, BlockArray> rotatedPatterns = new EnumMap<>(Direction.class);
        EnumMap<Direction, BoundingBox> boundingBoxes = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> componentPositions = new EnumMap<>(Direction.class);
        EnumMap<Direction, List<BlockPos>> portPositions = new EnumMap<>(Direction.class);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockArray rotated = BlockArrayCache.get(machine.pattern(), facing);
            rotatedPatterns.put(facing, rotated);
            boundingBoxes.put(facing, boundingBox(rotated));
            componentPositions.put(facing, componentPositions(rotated));
            portPositions.put(facing, portPositions(rotated));
        }

        return new CompiledMachinePattern(machine, rotatedPatterns, boundingBoxes, componentPositions, portPositions);
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
            case BlockPredicate.OfBlock of -> of.block() instanceof IOPortBlock;
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
}
