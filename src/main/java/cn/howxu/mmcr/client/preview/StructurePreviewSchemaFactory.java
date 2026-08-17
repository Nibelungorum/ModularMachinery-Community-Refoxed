package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
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
        return create(stages.getFirst(), machine.registryName(), StructurePreviewVariantSelection.defaults());
    }

    public StructurePreviewSchema create(MachineStructureStage stage, Identifier machineId,
            StructurePreviewVariantSelection selection) {
        Objects.requireNonNull(machineId, "machineId");
        StructurePreviewSchema resolved = resolve(stage, selection);
        return new StructurePreviewSchema(machineId, resolved.states(), resolved.levelSlots());
    }

    @Override
    public StructurePreviewSchema resolve(MachineStructureStage stage, StructurePreviewVariantSelection selection) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(selection, "selection");
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        Map<BlockPos, List<ItemStack>> candidates = new LinkedHashMap<>();
        int levelRank = highestSharedLevelRank(stage);
        for (var entry : stage.pattern().pattern().entrySet()) {
            BlockPos position = entry.getKey().immutable();
            Identifier levelSlot = stage.levelSlots().get(entry.getKey());
            BlockState state = levelSlot == null
                    ? entry.getValue().preferredState().orElse(null)
                    : levelState(levelSlot, levelRank);
            if (state == null) continue;
            states.put(position, orientController(position, state, stage.pattern().pattern()));
            if (!state.isAir()) candidates.put(position, List.of(new ItemStack(state.getBlock().asItem())));
            if (levelSlot != null) levelSlots.put(position, levelSlot);
        }
        return new StructurePreviewSchema(RESOLVED_STAGE_ID, states, levelSlots, candidates);
    }

    private static int highestSharedLevelRank(MachineStructureStage stage) {
        return stage.levelSlots().values().stream()
                .mapToInt(typeId -> MachineLevelRegistry.levelsForType(typeId).stream()
                        .mapToInt(MachineLevel::priority)
                        .max().orElse(-1))
                .max().orElse(-1);
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

    private static BlockState orientController(BlockPos position, BlockState state, Map<BlockPos, ?> pattern) {
        if (!(state.getBlock() instanceof MachineControllerBlock) || !position.equals(BlockPos.ZERO)) return state;
        int x = 0;
        int z = 0;
        for (BlockPos other : pattern.keySet()) {
            x += other.getX();
            z += other.getZ();
        }
        if (Math.abs(x) == Math.abs(z) && x == 0) return state;
        Direction interior = Math.abs(x) > Math.abs(z)
                ? (x < 0 ? Direction.WEST : Direction.EAST)
                : (z < 0 ? Direction.NORTH : Direction.SOUTH);
        return state.setValue(MachineControllerBlock.FACING, interior.getOpposite());
    }
}
