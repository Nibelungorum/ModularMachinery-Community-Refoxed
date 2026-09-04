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
import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Collectors;

/**
 * Immutable block-state snapshot used by the client-side structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewSchema {
    private final Identifier machineId;
    private final Map<BlockPos, BlockState> states;
    private final Map<BlockPos, Identifier> levelSlots;
    private final Map<BlockPos, List<Candidate>> candidates;
    private final CandidateResolver candidateResolver;
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
        this(machineId, states, levelSlots, candidates.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().stream().map(stack -> new Candidate(stack, false)).toList(),
                (left, right) -> left, LinkedHashMap::new)), true);
    }

    public StructurePreviewSchema(Identifier machineId, Map<BlockPos, BlockState> states,
            Map<BlockPos, Identifier> levelSlots, Map<BlockPos, List<Candidate>> candidates, boolean markedCandidates) {
        this(machineId, states, levelSlots, candidates, null);
    }

    private StructurePreviewSchema(Identifier machineId, Map<BlockPos, BlockState> states,
            Map<BlockPos, Identifier> levelSlots, Map<BlockPos, List<Candidate>> candidates,
            CandidateResolver candidateResolver) {
        this.machineId = Objects.requireNonNull(machineId, "machineId");
        this.states = copyPositions(states);
        this.levelSlots = copyPositions(levelSlots);
        this.candidates = new ConcurrentHashMap<>(copyCandidates(candidates));
        this.candidateResolver = candidateResolver;
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
        return previewCandidatesAt(position).stream().map(Candidate::stack).toList();
    }

    public Map<BlockPos, List<ItemStack>> candidates() {
        Map<BlockPos, List<ItemStack>> copy = new LinkedHashMap<>();
        candidates.forEach((position, values) -> copy.put(position, values.stream().map(Candidate::stack).toList()));
        return Collections.unmodifiableMap(copy);
    }

    public List<Candidate> previewCandidatesAt(BlockPos position) {
        List<Candidate> resolved = candidates.get(position);
        if (resolved == null && candidateResolver != null && states.containsKey(position)) {
            resolved = candidates.computeIfAbsent(position.immutable(), this::resolveCandidates);
        }
        return (resolved == null ? List.<Candidate>of() : resolved).stream().map(Candidate::copy).toList();
    }

    public Map<BlockPos, List<Candidate>> previewCandidates() {
        states.keySet().forEach(position -> candidates.computeIfAbsent(position, this::resolveCandidates));
        return copyCandidates(candidates);
    }

    static StructurePreviewSchema withCandidateResolver(Identifier machineId, Map<BlockPos, BlockState> states,
            Map<BlockPos, Identifier> levelSlots, CandidateResolver candidateResolver) {
        return new StructurePreviewSchema(machineId, states, levelSlots, Map.of(),
                Objects.requireNonNull(candidateResolver, "candidateResolver"));
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

    private List<Candidate> resolveCandidates(BlockPos position) {
        return List.copyOf(candidateResolver.resolve(position).stream().map(Candidate::copy).toList());
    }

    private static Map<BlockPos, List<Candidate>> copyCandidates(Map<BlockPos, List<Candidate>> source) {
        Map<BlockPos, List<Candidate>> copy = new LinkedHashMap<>();
        source.forEach((position, stacks) -> copy.put(position.immutable(),
                List.copyOf(stacks.stream().map(Candidate::copy).toList())));
        return Collections.unmodifiableMap(copy);
    }

    public record Candidate(ItemStack stack, boolean modifier) {
        public Candidate {
            stack = stack.copy();
        }

        private Candidate copy() {
            return new Candidate(stack, modifier);
        }
    }

    @FunctionalInterface
    interface CandidateResolver {
        List<Candidate> resolve(BlockPos position);
    }

}
