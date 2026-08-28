package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Reconciles independent data-storage ownership for a formed controller.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorageBindingCoordinator {
    public void reconcile(MachineControllerBlockEntity controller, Collection<DataStorageBlockEntity> found) {
        if (controller == null || found == null) return;
        Machine machine = controller.currentRuntimeSnapshot().structure().machine();
        if (machine == null) machine = controller.currentRuntimeSnapshot().structure().configuredMachine();
        if (machine == null) return;
        for (DataStorageBlockEntity storage : found) {
            if (storage.claimController(controller.getBlockPos(), machine.registryName())) {
                storage.linkControllerAppearance(controller.getBlockPos(), machine.appearance().formedPortBaseTexture());
            }
        }
    }

    public void unbind(MachineControllerBlockEntity controller, Collection<DataStorageBlockEntity> storages) {
        if (controller == null || storages == null) return;
        for (DataStorageBlockEntity storage : storages) storage.releaseController(controller.getBlockPos());
    }

    public void unbindMissing(MachineControllerBlockEntity controller, Collection<DataStorageBlockEntity> previous,
                              Collection<DataStorageBlockEntity> current) {
        if (previous == null) return;
        Set<DataStorageBlockEntity> currentSet = new HashSet<>(current == null ? Set.of() : current);
        unbind(controller, previous.stream().filter(storage -> !currentSet.contains(storage)).toList());
    }
}
