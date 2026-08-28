package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reconciles smart-interface bindings against one formed controller structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceBindingCoordinator {
    private final Map<String, SmartInterfaceType> types;
    private final boolean shared;

    public SmartInterfaceBindingCoordinator(Map<String, SmartInterfaceType> types) {
        this(types, false);
    }

    public SmartInterfaceBindingCoordinator(Map<String, SmartInterfaceType> types, boolean shared) {
        this.types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
        this.shared = shared;
    }

    public void reconcile(MachineControllerBlockEntity controller, Collection<SmartInterfaceBlockEntity> interfaces) {
        BlockPos controllerPos = controller.getBlockPos();
        StructureSnapshot structure = controller.currentStructureSnapshot();
        Machine machine = structure.machine();
        if (machine == null) return;
        var controllerAppearance = machine.appearance().formedPortBaseTexture();
        var ordered = interfaces.stream().sorted(Comparator.comparing(SmartInterfaceBlockEntity::getBlockPos)).toList();
        for (SmartInterfaceBlockEntity smartInterface : ordered) {
            if (smartInterface.machineId().isPresent() && !smartInterface.machineId().orElseThrow().equals(machine.registryName())) {
                continue;
            }
            if (smartInterface.claimController(controllerPos, machine.registryName(), types, shared)) {
                smartInterface.linkControllerAppearance(controllerPos, controllerAppearance);
                return;
            }
        }
    }

    public void unbindAll(MachineControllerBlockEntity controller, Collection<SmartInterfaceBlockEntity> interfaces) {
        for (SmartInterfaceBlockEntity smartInterface : interfaces) {
            smartInterface.releaseController(controller.getBlockPos());
        }
    }
}
