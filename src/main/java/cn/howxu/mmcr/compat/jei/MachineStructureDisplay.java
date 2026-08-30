package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * JEI display data and lazy default-stage structure material cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureDisplay {
    private final Machine machine;
    private volatile StructurePreviewSchema defaultSchema;
    private volatile StructureMaterialSummary materials;

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
        StructureMaterialSummary result = materials;
        if (result == null) {
            synchronized (this) {
                result = materials;
                if (result == null) {
                    result = StructureMaterialSummary.from(machine);
                    materials = result;
                }
            }
        }
        return result;
    }

    public List<ItemStack> ingredients() {
        return materials().transferStacks();
    }
}
