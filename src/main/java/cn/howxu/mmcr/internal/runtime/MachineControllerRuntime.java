package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Coordinates structure, component, and future recipe/factory runtime state for one controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRuntime {
    private final MachineControllerBlockEntity controller;
    private final StructureRuntime structure;
    private final ComponentRuntime components;
    private CraftingContext craftingContext = new CraftingContext(new CapabilitySnapshot(List.of()));
    private long craftingCapabilityVersion = Long.MIN_VALUE;

    public MachineControllerRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.structure = new StructureRuntime(controller);
        this.components = new ComponentRuntime();
    }

    public void serverTick(ServerLevel level, BlockPos controllerPos) {
        controller.serverTickFromRuntime();
    }

    public ControllerRuntimeSnapshot snapshot() {
        syncComponentState();
        components.refreshModuleConnectionState(controller);
        if (craftingCapabilityVersion != components.capabilityVersion()) {
            craftingContext = new CraftingContext(new CapabilitySnapshot(components.capabilities()), components.modifierList());
            craftingCapabilityVersion = components.capabilityVersion();
        }
        return new ControllerRuntimeSnapshot(controller.structureSnapshotFromRuntime(),
                components.components(), components.capabilities(), components.capabilityVersion(),
                components.foundModifiers(), components.foundLevels(), components.linkedPortPositions(),
                FactorySnapshot.empty());
    }

    public StructureRuntime structure() {
        return structure;
    }

    public ComponentRuntime components() {
        syncComponentState();
        return components;
    }

    public CraftingContext craftingContext() {
        snapshot();
        return craftingContext;
    }

    private void syncComponentState() {
        components.replaceComponents(controller.legacyComponentsForRuntime());
        components.replaceModifiers(controller.legacyModifiersForRuntime());
        components.replaceLevels(controller.legacyLevelsForRuntime());
        components.replaceLinkedPortPositions(controller.legacyLinkedPortPositionsForRuntime());
    }
}
