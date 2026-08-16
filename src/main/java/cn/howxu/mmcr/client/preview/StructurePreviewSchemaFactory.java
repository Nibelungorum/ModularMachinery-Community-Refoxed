package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

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
        for (var entry : stage.pattern().pattern().entrySet()) {
            entry.getValue().preferredState().ifPresent(state -> {
                BlockPos position = entry.getKey().immutable();
                states.put(position, state);
                Identifier levelSlot = stage.levelSlots().get(entry.getKey());
                if (levelSlot != null) levelSlots.put(position, levelSlot);
            });
        }
        return new StructurePreviewSchema(RESOLVED_STAGE_ID, states, levelSlots);
    }
}
