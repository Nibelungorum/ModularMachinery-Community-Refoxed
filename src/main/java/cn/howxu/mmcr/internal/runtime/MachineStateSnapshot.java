package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Immutable machine presentation values captured from one published runtime snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStateSnapshot(
        boolean formed,
        boolean structureAreaLoaded,
        boolean active,
        String activeRecipe,
        List<String> foundLevelIds,
        boolean recipeLocked,
        String lockedRecipeId,
        String machineId,
        int controllerRole,
        int installedModuleCount,
        boolean moduleConnected,
        String connectedHostId,
        CraftingStatus.Status craftingStatus,
        String craftingMessage,
        ExecutionStatus failure,
        int tick,
        int totalTick,
        long parallelism,
        long maxParallelism,
        boolean redstonePaused,
        boolean factoryControllerPresent,
        int factoryThreadCount,
        int activeFactoryThreadCount,
        int parallelControllerCount,
        long maxParallelControllerCount,
        List<ControllerRuntimeSnapshot.ComponentPresentation> components,
        List<ControllerRuntimeSnapshot.CapabilityPresentation> capabilities,
        long totalStoredEnergy,
        long totalCapacityEnergy,
        FluidStack primaryFluid,
        FluidStack primaryOutputFluid) {

    public MachineStateSnapshot {
        activeRecipe = activeRecipe == null ? "" : activeRecipe;
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
        lockedRecipeId = lockedRecipeId == null ? "" : lockedRecipeId;
        machineId = machineId == null ? "" : machineId;
        connectedHostId = connectedHostId == null ? "" : connectedHostId;
        craftingStatus = craftingStatus == null ? CraftingStatus.Status.IDLE : craftingStatus;
        craftingMessage = craftingMessage == null ? "" : craftingMessage;
        components = List.copyOf(components == null ? List.of() : components);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        primaryFluid = primaryFluid == null ? FluidStack.EMPTY : primaryFluid.copy();
        primaryOutputFluid = primaryOutputFluid == null ? FluidStack.EMPTY : primaryOutputFluid.copy();
        if (installedModuleCount < 0 || tick < 0 || totalTick < 0 || tick > totalTick
                || parallelism < 0 || maxParallelism < 1 || factoryThreadCount < 0
                || activeFactoryThreadCount < 0 || parallelControllerCount < 0 || maxParallelControllerCount < 0
                || totalStoredEnergy < 0L || totalCapacityEnergy < 0L) {
            throw new IllegalArgumentException("Invalid machine presentation values");
        }
    }

    public FluidStack primaryFluid() {
        return primaryFluid.copy();
    }

    public FluidStack primaryOutputFluid() {
        return primaryOutputFluid.copy();
    }
}
