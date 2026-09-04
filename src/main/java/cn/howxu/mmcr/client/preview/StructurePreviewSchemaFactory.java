package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewPredicates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds immutable client preview schemas from machine structure stages.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewSchemaFactory implements StructurePreviewVariantSource {
    private static final Identifier RESOLVED_STAGE_ID = MMCR.id("resolved_stage");

    public StructurePreviewSchema create(Machine machine) {
        Objects.requireNonNull(machine, "machine");
        List<MachineStructureStage> stages = machine.structureStages();
        if (stages.isEmpty()) throw new IllegalArgumentException("machine structure stages empty");
        Direction facing = machine.controller().requireVerticalFacing() ? Direction.UP : Direction.SOUTH;
        return create(stages.getFirst(), machine.registryName(), facing);
    }

    public StructurePreviewSchema create(MachineStructureStage stage, Identifier machineId) {
        return create(stage, machineId, Direction.SOUTH);
    }

    public StructurePreviewSchema create(MachineStructureStage stage, Identifier machineId, Direction facing) {
        return create(stage, machineId, StructurePreviewVariantSelection.defaults(), facing);
    }

    public StructurePreviewSchema create(MachineStructureStage stage, Identifier machineId,
            StructurePreviewVariantSelection selection) {
        return create(stage, machineId, selection, Direction.SOUTH);
    }

    private StructurePreviewSchema create(MachineStructureStage stage, Identifier machineId,
            StructurePreviewVariantSelection selection, Direction facing) {
        Objects.requireNonNull(machineId, "machineId");
        return resolve(stage, machineId, selection, facing, true);
    }

    @Override
    public StructurePreviewSchema resolve(MachineStructureStage stage, StructurePreviewVariantSelection selection) {
        return resolve(stage, selection, Direction.SOUTH);
    }

    private StructurePreviewSchema resolve(MachineStructureStage stage, StructurePreviewVariantSelection selection,
            Direction facing) {
        return resolve(stage, RESOLVED_STAGE_ID, selection, facing, false);
    }

    private StructurePreviewSchema resolve(MachineStructureStage stage, Identifier machineId,
            StructurePreviewVariantSelection selection, Direction facing, boolean lazyCandidates) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(selection, "selection");
        BlockArray rotatedPattern = BlockArrayCache.get(stage.pattern(), facing);
        Map<BlockPos, Identifier> rotatedLevelSlots = new LinkedHashMap<>();
        stage.levelSlots().forEach((position, levelSlot) ->
                rotatedLevelSlots.put(BlockRotator.rotateSouthTo(position, facing), levelSlot));
        Map<BlockPos, List<SingleBlockModifierReplacement>> rotatedModifierReplacements = new LinkedHashMap<>();
        stage.modifierReplacements().forEach((position, replacements) ->
                rotatedModifierReplacements.put(BlockRotator.rotateSouthTo(position, facing), replacements));
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        Map<BlockPos, List<StructurePreviewSchema.Candidate>> candidates = new LinkedHashMap<>();
        int levelRank = highestSharedLevelRank(stage);
        Map<BlockPos, BlockPredicate> pattern = rotatedPattern.pattern();
        boolean hasController = hasController(pattern);
        Direction correctedFacing = hasController ? correctedControllerFacing(pattern) : null;
        for (var entry : pattern.entrySet()) {
            BlockPos position = entry.getKey().immutable();
            Identifier levelSlot = rotatedLevelSlots.get(entry.getKey());
            BlockState state = levelSlot == null
                    ? entry.getValue() instanceof BlockPredicate.MachineCoupler
                            ? MultiblockPreviewPredicates.machineCouplerState().orElse(null)
                            : entry.getValue().preferredState().orElse(null)
                            : levelState(levelSlot, levelRank).rotate(rotationFor(facing));
            if (state == null) continue;
            states.put(position, orientState(position, state, facing, hasController, correctedFacing));
            if (lazyCandidates) {
            } else {
                List<StructurePreviewSchema.Candidate> positionCandidates = candidates(entry.getValue(), rotatedModifierReplacements.get(position));
                if (!positionCandidates.isEmpty()) candidates.put(position, positionCandidates);
            }
            if (levelSlot != null) levelSlots.put(position, levelSlot);
        }
        if (lazyCandidates) {
            return StructurePreviewSchema.withCandidateResolver(machineId, states, levelSlots,
                    position -> candidates(pattern.get(position), rotatedModifierReplacements.get(position)));
        }
        return new StructurePreviewSchema(machineId, states, levelSlots, candidates, true);
    }

    private static int highestSharedLevelRank(MachineStructureStage stage) {
        return stage.levelSlots().values().stream()
                .mapToInt(typeId -> MachineLevelRegistry.levelsForType(typeId).stream()
                        .mapToInt(MachineLevel::priority)
                        .max().orElse(-1))
                .max().orElse(-1);
    }

    private static List<StructurePreviewSchema.Candidate> candidates(BlockPredicate predicate, List<SingleBlockModifierReplacement> replacements) {
        List<StructurePreviewSchema.Candidate> candidates = new ArrayList<>();
        collectCandidates(predicate, candidates, false);
        if (replacements != null) {
            replacements.forEach(replacement -> collectCandidates(replacement.getReplacement(), candidates, true));
        }
        return List.copyOf(candidates);
    }

    private static void collectCandidates(BlockPredicate predicate, List<StructurePreviewSchema.Candidate> candidates, boolean modifier) {
        switch (predicate) {
            case BlockPredicate.OfBlock block -> addCandidate(block.block(), candidates, modifier);
            case BlockPredicate.OfBlockState state -> addCandidate(state.state().getBlock(), candidates, modifier);
            case BlockPredicate.DeferredBlock deferred -> addCandidate(deferred.supplier().get(), candidates, modifier);
            case BlockPredicate.OfTag tag -> BlockPredicate.blocksInTag(tag.tag()).forEach(block -> addCandidate(block, candidates, modifier));
            case BlockPredicate.AnyOf anyOf -> anyOf.children().forEach(child -> collectCandidates(child, candidates, modifier));
            default -> { }
        }
    }

    private static void addCandidate(Block block, List<StructurePreviewSchema.Candidate> candidates, boolean modifier) {
        ItemStack stack = new ItemStack(block.asItem());
        if (!stack.isEmpty() && candidates.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing.stack(), stack))) {
            candidates.add(new StructurePreviewSchema.Candidate(stack, modifier));
        }
    }

    private static BlockState levelState(Identifier typeId, int levelRank) {
        return MachineLevelRegistry.levelsForType(typeId).stream()
                .filter(level -> level.priority() == levelRank)
                .findFirst()
                .map(MachineLevel::statePredicate)
                .filter(BlockPredicate.OfBlockState.class::isInstance)
                .map(BlockPredicate.OfBlockState.class::cast)
                .map(BlockPredicate.OfBlockState::state)
                .orElse(Blocks.AIR.defaultBlockState());
    }

    private static Rotation rotationFor(Direction facing) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(Direction.SOUTH) == facing) return rotation;
        }
        return Rotation.NONE;
    }

    private static BlockState orientState(BlockPos position, BlockState state, Direction facing,
                                          boolean hasController, Direction correctedFacing) {
        if (!hasController) return state;
        if (state.getBlock() instanceof MachineControllerBlock && position.equals(BlockPos.ZERO)) {
            if (facing.getAxis().isVertical()) {
                return state.setValue(MachineControllerBlock.FACING, facing);
            }
            if (correctedFacing == null) return state;
            Direction controllerFacing = correctedFacing;
            return state.setValue(MachineControllerBlock.FACING, controllerFacing);
        }
        if (correctedFacing == null || !facing.getAxis().isHorizontal()) return state;
        return state.rotate(rotationBetween(facing, correctedFacing));
    }

    private static boolean hasController(Map<BlockPos, BlockPredicate> pattern) {
        return isController(pattern.get(BlockPos.ZERO));
    }

    private static boolean isController(BlockPredicate predicate) {
        return switch (predicate) {
            case null -> false;
            case BlockPredicate.OfBlock block -> block.block() instanceof MachineControllerBlock;
            case BlockPredicate.OfBlockState state -> state.state().getBlock() instanceof MachineControllerBlock;
            case BlockPredicate.DeferredBlock deferred -> deferred.supplier().get() instanceof MachineControllerBlock;
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream().anyMatch(StructurePreviewSchemaFactory::isController);
            default -> false;
        };
    }

    private static Direction correctedControllerFacing(Map<BlockPos, BlockPredicate> pattern) {
        int x = 0;
        int z = 0;
        for (BlockPos other : pattern.keySet()) {
            x += other.getX();
            z += other.getZ();
        }
        if (Math.abs(x) == Math.abs(z) && x == 0) return null;
        Direction interior = Math.abs(x) > Math.abs(z)
                ? (x < 0 ? Direction.WEST : Direction.EAST)
                : (z < 0 ? Direction.NORTH : Direction.SOUTH);
        return interior.getOpposite();
    }

    private static Rotation rotationBetween(Direction source, Direction target) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(source) == target) return rotation;
        }
        return Rotation.NONE;
    }
}
