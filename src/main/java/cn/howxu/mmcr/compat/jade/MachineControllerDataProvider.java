package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum MachineControllerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = MMCR.id("machine_controller");
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Deprecated // many have been no longer used
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof MachineControllerBlockEntity controller)) return;

        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        data.putBoolean("formed", runtime.structure().formed());
        data.putBoolean("active", SYNC_RUNTIME.active(runtime));
        data.putBoolean("tickMachine", machine != null && machine.behavior() instanceof TickBehavior);
        data.putLong("parallelism", SYNC_RUNTIME.currentParallelism(runtime));
        data.putLong("maxParallelism", runtime.maxParallelism());
        data.putInt("parallelSlots", runtime.parallelControllerCount());
        data.putLong("maxParallelSlots", runtime.maxParallelControllerCount());
        data.putBoolean("factorySupported", runtime.factorySupported());
        data.putBoolean("factoryPresent", runtime.factoryControllerPresent());
        data.putInt("factoryLanes", runtime.factory().activeLaneCount());
        data.putInt("factoryThreadLimit", runtime.factory().laneLimit());
        data.putInt("tick", SYNC_RUNTIME.tick(runtime));
        data.putInt("totalTick", SYNC_RUNTIME.totalTick(runtime));
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId != null) data.putString("recipePool", recipePoolId.toString());

        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        int energyInputs = 0;
        int energyOutputs = 0;
        for (ControllerRuntimeSnapshot.ComponentPresentation component : runtime.componentPresentations()) {
            if (component.kindId() == null) continue;
            switch (component.kindId()) {
                case "item_input_bus" -> itemInputs++;
                case "item_output_bus" -> itemOutputs++;
                case "fluid_input_hatch" -> fluidInputs++;
                case "fluid_output_hatch" -> fluidOutputs++;
                case "energy_input_hatch" -> energyInputs++;
                case "energy_output_hatch" -> energyOutputs++;
                default -> { }
            }
        }
        data.putInt("itemInputs", itemInputs);
        data.putInt("itemOutputs", itemOutputs);
        data.putInt("fluidInputs", fluidInputs);
        data.putInt("fluidOutputs", fluidOutputs);
        data.putInt("energyInputs", energyInputs);
        data.putInt("energyOutputs", energyOutputs);
        JadeTextCodec.write(data, controller.jadeTextSnapshot());
    }
}
