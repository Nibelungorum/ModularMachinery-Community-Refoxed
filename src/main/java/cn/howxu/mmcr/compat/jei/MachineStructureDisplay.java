package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * JEI display data and lazy structure material summaries in preview stage order.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureDisplay {
    private final Machine machine;
    private volatile StructurePreviewSchema defaultSchema;
    private SequencedMap<Integer, StructureMaterialSummary> materialsByStage;

    private MachineStructureDisplay(Machine machine) {
        this.machine = Objects.requireNonNull(machine, "machine");
    }

    public static MachineStructureDisplay from(Machine machine) {
        return new MachineStructureDisplay(machine);
    }

    public Machine machine() {
        return machine;
    }

    public StructurePreviewSchema defaultSchema() {
        StructurePreviewSchema result = defaultSchema;
        if (result == null) {
            synchronized (this) {
                result = defaultSchema;
                if (result == null) {
                    result = new StructurePreviewSchemaFactory().create(machine);
                    defaultSchema = result;
                }
            }
        }
        return result;
    }

    public StructureMaterialSummary materials() {
        return materialsByStage().firstEntry().getValue();
    }

    synchronized SequencedMap<Integer, StructureMaterialSummary> materialsByStage() {
        if (materialsByStage == null) {
            var summaries = new LinkedHashMap<Integer, StructureMaterialSummary>();
            for (var stage : machine.structureStages()) {
                summaries.put(stage.number(), StructureMaterialSummary.from(stage));
            }
            if (summaries.isEmpty()) throw new IllegalArgumentException("machine structure stages empty");
            materialsByStage = Collections.unmodifiableSequencedMap(summaries);
        }
        return materialsByStage;
    }

    public List<ItemStack> ingredients() {
        return materials().transferStacks();
    }
}
