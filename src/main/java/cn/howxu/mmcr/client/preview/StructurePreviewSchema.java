package cn.howxu.mmcr.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable block-state snapshot used by the client-side structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewSchema {
    private final Identifier machineId;
    private final Map<BlockPos, BlockState> states;
    private final Map<BlockPos, Identifier> levelSlots;
    private final Map<BlockPos, List<ItemStack>> candidates;
    private final BlockPos min;
    private final BlockPos max;
    private final List<Float> center;
    private final List<Integer> layers;

    public StructurePreviewSchema(Identifier machineId, Map<BlockPos, BlockState> states,
            Map<BlockPos, Identifier> levelSlots) {
        this(machineId, states, levelSlots, Map.of());
    }

    public StructurePreviewSchema(Identifier machineId, Map<BlockPos, BlockState> states,
            Map<BlockPos, Identifier> levelSlots, Map<BlockPos, List<ItemStack>> candidates) {
        this.machineId = Objects.requireNonNull(machineId, "machineId");
        this.states = copyPositions(states);
        this.levelSlots = copyPositions(levelSlots);
        this.candidates = copyCandidates(candidates);
        if (!this.states.keySet().containsAll(this.levelSlots.keySet())) {
            throw new IllegalArgumentException("level slot position is not in preview schema");
        }

        if (this.states.isEmpty()) {
            min = BlockPos.ZERO;
            max = BlockPos.ZERO;
            center = List.of(0.5F, 0.5F, 0.5F);
            layers = List.of();
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<Integer> yLayers = new ArrayList<>();
        for (BlockPos position : this.states.keySet()) {
            minX = Math.min(minX, position.getX());
            minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ());
            maxX = Math.max(maxX, position.getX());
            maxY = Math.max(maxY, position.getY());
            maxZ = Math.max(maxZ, position.getZ());
            if (!yLayers.contains(position.getY())) yLayers.add(position.getY());
        }
        yLayers.sort(Integer::compareTo);
        min = new BlockPos(minX, minY, minZ);
        max = new BlockPos(maxX, maxY, maxZ);
        center = List.of((minX + maxX + 1) / 2.0F, (minY + maxY) / 2.0F, (minZ + maxZ + 1) / 2.0F);
        layers = List.copyOf(yLayers);
    }

    public Identifier machineId() {
        return machineId;
    }

    public BlockState stateAt(BlockPos position) {
        return states.get(position);
    }

    public Map<BlockPos, BlockState> states() {
        return states;
    }

    public Identifier levelSlotAt(BlockPos position) {
        return levelSlots.get(position);
    }

    public Map<BlockPos, Identifier> levelSlots() {
        return levelSlots;
    }

    public List<ItemStack> candidatesAt(BlockPos position) {
        return candidates.getOrDefault(position, List.of()).stream().map(ItemStack::copy).toList();
    }

    public Map<BlockPos, List<ItemStack>> candidates() {
        return copyCandidates(candidates);
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public List<Float> center() {
        return center;
    }

    public List<Integer> layers() {
        return layers;
    }

    private static <T> Map<BlockPos, T> copyPositions(Map<BlockPos, T> source) {
        Objects.requireNonNull(source, "source");
        Map<BlockPos, T> copy = new LinkedHashMap<>();
        source.forEach((position, value) -> copy.put(
                Objects.requireNonNull(position, "position").immutable(),
                Objects.requireNonNull(value, "value")));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<BlockPos, List<ItemStack>> copyCandidates(Map<BlockPos, List<ItemStack>> source) {
        Map<BlockPos, List<ItemStack>> copy = new LinkedHashMap<>();
        source.forEach((position, stacks) -> copy.put(position.immutable(),
                List.copyOf(stacks.stream().map(ItemStack::copy).toList())));
        return Collections.unmodifiableMap(copy);
    }

}
