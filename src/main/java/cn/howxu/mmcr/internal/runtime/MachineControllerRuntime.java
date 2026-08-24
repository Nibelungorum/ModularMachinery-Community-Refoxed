package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
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
    private long craftingStateVersion = Long.MIN_VALUE;

    public MachineControllerRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.structure = new StructureRuntime(controller);
        this.components = new ComponentRuntime();
    }

    public void serverTick(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Controller runtime tick requires a level and controller position");
        }
        if (controller.getLevel() != null && controller.getLevel() != level) {
            throw new IllegalArgumentException("Controller runtime level does not match the controller");
        }
        if (controller.getBlockPos() != null && !controller.getBlockPos().equals(controllerPos)) {
            throw new IllegalArgumentException("Controller runtime position does not match the controller");
        }
        controller.serverTickFromRuntime(level, controllerPos);
    }

    public ControllerRuntimeSnapshot snapshot() {
        syncComponentState();
        components.refreshModuleConnectionState(controller);
        if (craftingStateVersion != components.craftingStateVersion()) {
            craftingContext = new CraftingContext(new CapabilitySnapshot(components.capabilities()), components.modifierList());
            craftingStateVersion = components.craftingStateVersion();
        }
        return new ControllerRuntimeSnapshot(structure.snapshot(),
                components.components(), components.capabilities(), components.capabilityVersion(),
                components.foundModifiers(), components.foundLevels(), components.linkedPortPositions(),
                components.moduleConnectionStatus(), components.installedModuleCount(),
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

    public long craftingStateVersion() {
        return components.craftingStateVersion();
    }

    public ModuleConnectionStatus moduleConnectionStatus() {
        components.refreshModuleConnectionState(controller);
        return components.moduleConnectionStatus();
    }

    public int installedModuleCount() {
        components.refreshModuleConnectionState(controller);
        return components.installedModuleCount();
    }

    private void syncComponentState() {
        components.replaceComponents(controller.legacyComponentsForRuntime());
        components.replaceModifiers(controller.legacyModifiersForRuntime());
        components.replaceLevels(controller.legacyLevelsForRuntime());
        components.replaceLinkedPortPositions(controller.legacyLinkedPortPositionsForRuntime());
    }
}
