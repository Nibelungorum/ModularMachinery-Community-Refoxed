package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the authoritative runtime state and publishes immutable controller snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRuntime {
    private final MachineControllerBlockEntity controller;
    private final StructureRuntime structure;
    private final ComponentRuntime components = new ComponentRuntime();
    private CraftingContext craftingContext = new CraftingContext(new CapabilitySnapshot(List.of()));
    private CraftingStateSnapshot craftingState = CraftingStateSnapshot.empty(0L, 0L, 0L);
    private long contextCapabilityVersion = Long.MIN_VALUE;
    private long contextModifierVersion = Long.MIN_VALUE;

    public MachineControllerRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.structure = new StructureRuntime(controller);
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
        structure.tick(level, controllerPos);
        controller.tickRuntimeWork(level, controllerPos);
    }

    public ControllerRuntimeSnapshot snapshot() {
        return new ControllerRuntimeSnapshot(structure.snapshot(), components.components(), components.capabilities(),
                components.capabilityVersion(), components.modifierVersion(), components.stateVersion(),
                components.foundModifiers(), components.foundLevels(), components.linkedPortPositions(),
                components.moduleConnectionStatus(), components.installedModuleCount(), craftingState,
                FactorySnapshot.empty());
    }

    public CraftingContext craftingContext() {
        return craftingContext;
    }

    public void publishStructureState(boolean structureAreaLoaded) {
        structure.setStructureAreaLoaded(structureAreaLoaded);
    }

    public void publishComponentState(List<ProcessingComponent> nextComponents,
                                      Map<String, List<RecipeModifier>> modifiers,
                                      Map<Identifier, MachineLevel> levels,
                                      Set<BlockPos> linkedPositions) {
        components.replaceComponents(nextComponents);
        components.replaceModifiers(modifiers);
        components.replaceLevels(levels);
        components.replaceLinkedPortPositions(linkedPositions);
        refreshCraftingContext();
    }

    public void publishModuleConnectionState(ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceModuleConnectionState(status, installedModuleCount);
    }

    public void refreshModuleConnectionState() {
        if (!(controller.getLevel() instanceof ServerLevel)) {
            publishModuleConnectionState(ModuleConnectionStatus.notRequired(), 0);
            return;
        }
        publishModuleConnectionState(ModuleConnectionCoordinator.connectionStatus(controller),
                ModuleConnectionCoordinator.installedModuleCount(controller));
    }

    public void publishCraftingState(@Nullable Identifier recipeId, CraftingStatus status,
                                     @Nullable ExecutionStatus failure) {
        refreshCraftingContext();
        craftingState = new CraftingStateSnapshot(recipeId, status, failure,
                structure.version(), components.capabilityVersion(), components.modifierVersion());
    }

    public StructureRuntime structure() {
        return structure;
    }

    public ComponentRuntime components() {
        return components;
    }

    private void refreshCraftingContext() {
        if (contextCapabilityVersion == components.capabilityVersion()
                && contextModifierVersion == components.modifierVersion()) return;
        craftingContext = new CraftingContext(new CapabilitySnapshot(components.capabilities()), components.modifierList());
        contextCapabilityVersion = components.capabilityVersion();
        contextModifierVersion = components.modifierVersion();
    }
}
