package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.MachineCapability;

import java.util.List;

/**
 * Immutable machine state exposed to presentation and synchronization boundaries.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStateSnapshot(
        StructureSnapshot structure,
        CraftingStateSnapshot crafting,
        List<MachineCapability> capabilities,
        int installedModuleCount,
        boolean moduleConnected) {
    public MachineStateSnapshot {
        structure = structure == null ? StructureSnapshot.empty() : structure;
        crafting = crafting == null ? CraftingStateSnapshot.empty(structure.version(), 0L, 0L) : crafting;
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        if (installedModuleCount < 0) throw new IllegalArgumentException("installedModuleCount must not be negative");
    }
}
