package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewBuilder;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        var machine = controller.boundMachine();
        if (machine.isEmpty()) {
            return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.no_machine"));
        }
        BlockArray pattern = controller.assemblyPattern(machine.get());
        List<Placement> placements = createTemplatePlacements(controller.getBlockPos(), pattern).stream()
                .filter(placement -> player.level().getBlockState(placement.pos()).isAir())
                .toList();
        placements = limitOperation(placements);
        if (placements.isEmpty()) {
            return new Result(InteractionResult.SUCCESS, 0, new ComponentKey("message.mmcr.terminal.build.none"));
        }
        if (!creative) {
            StructureItemSource source = new PlayerInventoryStructureItemSource(player);
            placements = extractAvailablePlacements(placements, source);
            if (placements.isEmpty()) {
                return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.build.missing"));
            }
            source.extractAll(aggregateRequirements(placements));
        }
        int placed = 0;
        for (Placement placement : placements) {
            player.level().setBlock(placement.pos(), placement.state(), 3);
            placed++;
        }
        controller.serverTick();
        return new Result(InteractionResult.SUCCESS, placed, new ComponentKey("message.mmcr.terminal.build.success", placed));
    }

    public static Result demolish(ServerPlayer player, MachineControllerBlockEntity controller,
                                  int maxBlocks, StructureItemSink sink) {
        var machine = controller.boundMachine();
        if (machine.isEmpty()) {
            return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.no_machine"));
        }
        BlockArray pattern = controller.assemblyPattern(machine.get());
        boolean stateSensitive = controller.assemblyStateSensitive(machine.get());
        List<Placement> template = createTemplatePlacements(controller.getBlockPos(), pattern);
        int removed = 0;
        maxBlocks = Math.min(maxBlocks, MAX_BLOCKS_PER_OPERATION);
        for (Placement placement : template) {
            if (removed >= maxBlocks) break;
            BlockState current = player.level().getBlockState(placement.pos());
            if (current.isAir()) continue;
            if (!placement.matches(current, stateSensitive)) continue;
            ItemStack drop = current.getBlock().asItem().getDefaultInstance();
            player.level().removeBlock(placement.pos(), false);
            if (!drop.isEmpty()) sink.accept(drop);
            removed++;
        }
        controller.serverTick();
        if (removed == 0) {
            return new Result(InteractionResult.SUCCESS, 0, new ComponentKey("message.mmcr.terminal.demolish.none"));
        }
        if (removed >= maxBlocks) {
            return new Result(InteractionResult.SUCCESS, removed, new ComponentKey("message.mmcr.terminal.demolish.limit", removed, maxBlocks));
        }
        return new Result(InteractionResult.SUCCESS, removed, new ComponentKey("message.mmcr.terminal.demolish.success", removed));
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
        return selected;
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
