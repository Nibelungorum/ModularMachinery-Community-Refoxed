package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewBuilder;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
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

    private MultiblockAssemblyService() {}

    public record Placement(BlockPos pos, BlockState state, ItemStack requirement, BlockPredicate predicate) {
        public Placement(BlockPos pos, BlockState state, ItemStack requirement) {
            this(pos, state, requirement, new BlockPredicate.OfBlockState(state));
        }

        public boolean matches(BlockState current) {
            return predicate.matches(current);
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
        if (placements.isEmpty()) {
            return new Result(InteractionResult.SUCCESS, 0, new ComponentKey("message.mmcr.terminal.build.none"));
        }
        if (!creative) {
            StructureItemSource source = new PlayerInventoryStructureItemSource(player);
            List<ItemStack> requirements = aggregateRequirements(placements);
            if (!source.canExtractAll(requirements)) {
                return new Result(InteractionResult.FAIL, 0, new ComponentKey("message.mmcr.terminal.build.missing"));
            }
            source.extractAll(requirements);
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
        List<Placement> template = createTemplatePlacements(controller.getBlockPos(), pattern);
        int removed = 0;
        for (Placement placement : template) {
            if (removed >= maxBlocks) break;
            BlockState current = player.level().getBlockState(placement.pos());
            if (current.isAir()) continue;
            if (!placement.matches(current)) continue;
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

    private static ItemStack requirementFor(BlockState state) {
        try {
            return state.getBlock().asItem().getDefaultInstance();
        } catch (NullPointerException ignored) {
            return new ItemStack(Holder.direct(state.getBlock().asItem(), DataComponentMap.EMPTY));
        }
    }

    private static Optional<BlockState> preferredState(BlockPredicate predicate) {
        List<BlockState> candidates = candidateStates(predicate).stream()
                .sorted(Comparator.comparingInt(MultiblockAssemblyService::levelPriority).reversed())
                .toList();
        if (!candidates.isEmpty()) return Optional.of(candidates.getFirst());
        return MultiblockPreviewBuilder.previewState(predicate);
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
            case BlockPredicate.AnyOf anyOf -> anyOf.children().forEach(child -> collectCandidateStates(child, states));
            default -> {}
        }
    }

    private static int levelPriority(BlockState state) {
        return cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.findLevel(state)
                .map(level -> level.priority())
                .orElse(Integer.MIN_VALUE);
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
