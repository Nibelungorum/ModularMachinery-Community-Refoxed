package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewBuilder;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.function.Predicate;

/**
 * Shared synchronous executor for multiblock build and demolish operations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MultiblockAssemblyService {

    public static final int MAX_BLOCKS_PER_OPERATION = 163_840;

    private MultiblockAssemblyService() {}

    public record Placement(BlockPos pos, BlockState state, ItemStack requirement, BlockPredicate predicate) {
        public Placement(BlockPos pos, BlockState state, ItemStack requirement) {
            this(pos, state, requirement, new BlockPredicate.OfBlockState(state));
        }

        public boolean matches(BlockState current) {
            return predicate.matches(current);
        }

        public boolean matches(BlockState current, boolean stateSensitive) {
            return predicate.matches(current, stateSensitive);
        }
    }

    public record Removal(BlockPos pos, BlockState state, ItemStack drop) {}

    public record Result(InteractionResult interactionResult, int changedBlocks, ComponentKey message) {}

    public record ComponentKey(String key, Object... args) {}

    public static final class BuildTask {
        private final BlockPos controllerKey;
        private final List<Placement> placements;
        private final int budget;
        private final boolean reservedMaterial;
        private final List<Placement> skippedPlacements = new ArrayList<>();
        private int nextIndex;
        private int placedCount;
        private boolean completionReported;

        private BuildTask(BlockPos controllerKey, List<Placement> placements, int budget, boolean reservedMaterial) {
            this.controllerKey = controllerKey.immutable();
            this.placements = placements.stream()
                    .map(placement -> new Placement(placement.pos(), placement.state(), placement.requirement().copy(), placement.predicate()))
                    .toList();
            this.budget = budget;
            this.reservedMaterial = reservedMaterial;
        }

        public static BuildTask create(BlockPos controllerKey, List<Placement> placements, int budget) {
            return create(controllerKey, placements, budget, true);
        }

        public static BuildTask create(BlockPos controllerKey, List<Placement> placements, int budget,
                                       boolean reservedMaterial) {
            if (budget < 1) throw new IllegalArgumentException("budget must be positive");
            return new BuildTask(controllerKey, placements, budget, reservedMaterial);
        }

        public BlockPos controllerKey() {
            return controllerKey;
        }

        public int advance(Predicate<Placement> placementAction) {
            int end = Math.min(nextIndex + budget, placements.size());
            int advanced = end - nextIndex;
            while (nextIndex < end) {
                Placement placement = placements.get(nextIndex++);
                if (placementAction.test(placement)) placedCount++;
                else skippedPlacements.add(placement);
            }
            return advanced;
        }

        public boolean isComplete() {
            return nextIndex >= placements.size();
        }

        public List<Placement> unconsumedPlacements() {
            return placements.subList(nextIndex, placements.size());
        }

        public List<ItemStack> unconsumedRequirements() {
            return unconsumedPlacements().stream().map(placement -> placement.requirement().copy()).toList();
        }

        public List<ItemStack> refundRequirements() {
            if (!reservedMaterial) return List.of();
            return Stream.concat(skippedPlacements.stream(), unconsumedPlacements().stream())
                    .map(placement -> placement.requirement().copy())
                    .toList();
        }

        public int placedCount() {
            return placedCount;
        }

        public Optional<Component> takeCompletionReport() {
            if (!isComplete() || completionReported) return Optional.empty();
            completionReported = true;
            return Optional.of(Component.translatable("message.mmcr.terminal.build.completed", placedCount));
        }
    }

    public static final class BuildTaskRegistry {
        private final Map<BlockPos, BuildTask> tasks = new HashMap<>();

        public boolean submit(BuildTask task) {
            return tasks.putIfAbsent(task.controllerKey(), task) == null;
        }

        public int advance(BlockPos controllerKey, Predicate<Placement> placementAction) {
            BuildTask task = tasks.get(controllerKey);
            return task == null ? 0 : task.advance(placementAction);
        }

        public int placedCount(BlockPos controllerKey) {
            BuildTask task = tasks.get(controllerKey);
            return task == null ? 0 : task.placedCount();
        }

        public BuildTask cancel(BlockPos controllerKey) {
            return tasks.remove(controllerKey);
        }

        public boolean hasActiveTask(BlockPos controllerKey) {
            return tasks.containsKey(controllerKey);
        }
    }

    public static List<Placement> createTemplatePlacements(BlockPos controllerPos, BlockArray rotatedPattern) {
        List<Placement> placements = new ArrayList<>();
        for (var entry : rotatedPattern.pattern().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO)) continue;
            BlockPredicate predicate = entry.getValue();
            preferredState(predicate).ifPresent(state -> addPlacement(placements, controllerPos.offset(entry.getKey()), state, predicate));
        }
        return placements;
    }

    public static <T> List<T> limitOperation(List<T> entries) {
        return entries.size() > MAX_BLOCKS_PER_OPERATION
                ? entries.subList(0, MAX_BLOCKS_PER_OPERATION)
                : entries;
    }

    public static List<ItemStack> aggregateRequirements(List<Placement> placements) {
        List<ItemStack> requirements = new ArrayList<>();
        for (Placement placement : placements) {
            merge(requirements, placement.requirement());
        }
        return requirements;
    }

    public static Result build(ServerPlayer player, MachineControllerBlockEntity controller, boolean creative) {
        int stage = controller.structureSnapshot().formed() ? controller.structureSnapshot().matchedStage() : 1;
        return build(player, controller, stage, new PlayerInventoryStructureItemSource(player), creative);
    }

    public static Result build(ServerPlayer player, MachineControllerBlockEntity controller, int stage,
                               StructureItemSource source, boolean freeBuild) {
        return build(player, controller, stage, source, freeBuild, Map.of());
    }

    public static Result build(ServerPlayer player, MachineControllerBlockEntity controller, int stage,
                               StructureItemSource source, boolean freeBuild,
                               Map<Identifier, Identifier> selectedLevels) {
        var machine = controller.boundMachine();
        if (machine.isEmpty()) {
            return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.no_machine"));
        }
        BlockArray pattern = controller.assemblyPattern(machine.get(), stage);
        List<Placement> placements = applySelectedLevels(machine.get(), stage, pattern, controller.getBlockPos(),
                createTemplatePlacements(controller.getBlockPos(), pattern), selectedLevels).stream()
                .filter(placement -> player.level().getBlockState(placement.pos()).isAir())
                .toList();
        placements = limitOperation(placements);
        if (placements.isEmpty()) {
            return new Result(InteractionResult.SUCCESS, 0, new ComponentKey("message.mmcr.terminal.build.none"));
        }
        if (controller.hasActiveBuildTask()) {
            return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.build.busy"));
        }
        if (!freeBuild) {
            placements = extractAvailablePlacements(placements, source);
            if (placements.isEmpty()) {
                return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.build.missing"));
            }
            source.extractAll(aggregateRequirements(placements));
        }
        BuildTask task = BuildTask.create(controller.getBlockPos(), placements,
                controller.buildBlocksPerTick(), !freeBuild);
        if (!controller.startBuildTask(task, player)) {
            return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.build.busy"));
        }
        return new Result(InteractionResult.SUCCESS, placements.size(),
                new ComponentKey("message.mmcr.terminal.build.accepted", placements.size()));
    }

    private static List<Placement> applySelectedLevels(Machine machine, int stage, BlockArray pattern,
            BlockPos controllerPos, List<Placement> placements, Map<Identifier, Identifier> selectedLevels) {
        Map<Character, Identifier> levelTypes = machine.structureStages().stream()
                .filter(structureStage -> structureStage.number() == stage)
                .findFirst().map(structureStage -> structureStage.requirements().levelSlots()).orElse(Map.of());
        if (levelTypes.isEmpty() || selectedLevels.isEmpty()) return placements;
        return placements.stream().map(placement -> {
            Character symbol = pattern.symbolsByPosition().get(placement.pos().subtract(controllerPos));
            Identifier levelId = symbol == null ? null : selectedLevels.get(levelTypes.get(symbol));
            MachineLevel level = levelId == null ? null : MachineLevelRegistry.getLevel(levelId);
            BlockState state = level == null ? null : level.statePredicate().preferredState().orElse(null);
            return state == null ? placement
                    : new Placement(placement.pos(), state, requirementFor(state), level.statePredicate());
        }).toList();
    }

    public static Result demolish(ServerPlayer player, MachineControllerBlockEntity controller,
                                  int maxBlocks, StructureItemSink sink) {
        int stage = controller.structureSnapshot().formed() ? controller.structureSnapshot().matchedStage() : 1;
        return demolish(player, controller, stage, maxBlocks, sink);
    }

    public static Result demolish(ServerPlayer player, MachineControllerBlockEntity controller, int stage,
                                  int maxBlocks, StructureItemSink sink) {
        var machine = controller.boundMachine();
        if (machine.isEmpty()) {
            return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.no_machine"));
        }
        BlockArray pattern = controller.assemblyPattern(machine.get(), stage);
        boolean stateSensitive = controller.assemblyStateSensitive(machine.get());
        List<Placement> template = createTemplatePlacements(controller.getBlockPos(), pattern);
        maxBlocks = Math.min(maxBlocks, MAX_BLOCKS_PER_OPERATION);
        List<Removal> removals = new ArrayList<>();
        for (Placement placement : template) {
            if (removals.size() >= maxBlocks) break;
            BlockState current = player.level().getBlockState(placement.pos());
            if (current.isAir()) continue;
            if (!placement.matches(current, stateSensitive)) continue;
            removals.add(new Removal(placement.pos(), current, current.getBlock().asItem().getDefaultInstance()));
        }
        int removed = removeAcceptedDrops(removals, sink, removal -> player.level().removeBlock(removal.pos(), false));
        if (removed == 0) {
            return new Result(InteractionResult.SUCCESS, 0, new ComponentKey("message.mmcr.terminal.demolish.none"));
        }
        if (removed >= maxBlocks) {
            return new Result(InteractionResult.SUCCESS, removed, new ComponentKey("message.mmcr.terminal.demolish.limit", removed, maxBlocks));
        }
        return new Result(InteractionResult.SUCCESS, removed, new ComponentKey("message.mmcr.terminal.demolish.success", removed));
    }

    static int removeAcceptedDrops(List<Removal> removals, StructureItemSink sink, Predicate<Removal> remove) {
        int removed = 0;
        for (Removal removal : removals) {
            if (!removal.drop().isEmpty() && !sink.accept(removal.drop().copy())) break;
            if (!remove.test(removal)) break;
            removed++;
        }
        return removed;
    }

    public static List<Placement> extractAvailablePlacements(List<Placement> placements, StructureItemSource source) {
        List<ItemStack> available = source.copyStacks();
        List<Placement> selected = new ArrayList<>();
        for (Placement placement : placements) {
            candidateStates(placement.predicate()).stream()
                    .sorted(Comparator.comparingInt(MultiblockAssemblyService::levelPriority).reversed())
                    .map(state -> new Placement(placement.pos(), state, requirementFor(state).copyWithCount(placement.requirement().getCount()), placement.predicate()))
                    .filter(candidate -> reserve(available, candidate.requirement()))
                    .findFirst()
                    .ifPresent(selected::add);
        }
        return selected.size() == placements.size() ? selected : List.of();
    }

    private static ItemStack requirementFor(BlockState state) {
        try {
            return state.getBlock().asItem().getDefaultInstance();
        } catch (NullPointerException ignored) {
            return new ItemStack(Holder.direct(state.getBlock().asItem(), DataComponentMap.EMPTY));
        }
    }

    private static boolean reserve(List<ItemStack> stacks, ItemStack requirement) {
        int remaining = requirement.getCount();
        for (ItemStack stack : stacks) {
            if (!ItemStack.isSameItemSameComponents(stack, requirement)) continue;
            remaining -= stack.getCount();
            if (remaining <= 0) break;
        }
        if (remaining > 0) return false;
        remaining = requirement.getCount();
        for (ItemStack stack : stacks) {
            if (!ItemStack.isSameItemSameComponents(stack, requirement)) continue;
            int reserved = Math.min(remaining, stack.getCount());
            stack.shrink(reserved);
            remaining -= reserved;
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static Optional<BlockState> preferredState(BlockPredicate predicate) {
        return predicate.preferredState().or(() -> MultiblockPreviewBuilder.previewState(predicate));
    }

    private static List<BlockState> candidateStates(BlockPredicate predicate) {
        List<BlockState> states = new ArrayList<>();
        collectCandidateStates(predicate, states);
        return states;
    }

    private static void collectCandidateStates(BlockPredicate predicate, List<BlockState> states) {
        switch (predicate) {
            case BlockPredicate.OfBlockState ofState -> states.add(ofState.state());
            case BlockPredicate.OfBlock ofBlock -> states.add(ofBlock.block().defaultBlockState());
            case BlockPredicate.DeferredBlock deferredBlock -> states.add(deferredBlock.supplier().get().defaultBlockState());
            case BlockPredicate.OfTag ofTag -> BlockPredicate.blocksInTag(ofTag.tag()).stream()
                    .map(Block::defaultBlockState)
                    .forEach(states::add);
            case BlockPredicate.AnyOf anyOf -> anyOf.children().forEach(child -> collectCandidateStates(child, states));
            default -> {}
        }
    }

    private static int levelPriority(BlockState state) {
        return MachineLevelRegistry.findLevel(state).map(level -> level.priority()).orElse(Integer.MIN_VALUE);
    }

    private static void addPlacement(List<Placement> placements, BlockPos pos, BlockState state, BlockPredicate predicate) {
        ItemStack requirement = requirementFor(state);
        if (!requirement.isEmpty()) {
            placements.add(new Placement(pos, state, requirement, predicate));
        }
    }

    private static void merge(List<ItemStack> requirements, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack existing : requirements) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        requirements.add(stack.copy());
    }
}
