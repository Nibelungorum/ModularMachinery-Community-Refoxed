package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum MachineControllerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    @Override
    public Identifier getUid() {
        return MachineControllerComponentProvider.UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof MachineControllerBlockEntity controller)) return;

        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        var machineState = SYNC_RUNTIME.machineState(runtime);
        FactorySnapshot factory = SYNC_RUNTIME.factoryState(runtime);
        var machine = SYNC_RUNTIME.machine(runtime);
        data.putBoolean("formed", machineState.structure().formed());
        data.putBoolean("active", SYNC_RUNTIME.active(runtime));
        data.putInt("parallelism", SYNC_RUNTIME.currentParallelism(runtime));
        data.putInt("maxParallelism", SYNC_RUNTIME.maxParallelism(runtime));
        data.putInt("parallelSlots", factory.parallelSlots());
        data.putInt("maxParallelSlots", SYNC_RUNTIME.maxParallelControllerCount(runtime));
        data.putBoolean("factorySupported", machine != null && machine.hasFactory());
        data.putBoolean("factoryPresent", SYNC_RUNTIME.factoryControllerPresent(runtime));
        data.putInt("factoryLanes", factory.activeLaneCount());
        data.putInt("factoryThreadLimit", factory.laneLimit());
        String activeRecipe = SYNC_RUNTIME.activeRecipe(runtime);
        if (!activeRecipe.isEmpty()) data.putString("activeRecipe", activeRecipe);
        data.putInt("tick", SYNC_RUNTIME.tick(runtime));
        data.putInt("totalTick", SYNC_RUNTIME.totalTick(runtime));

        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        int energyInputs = 0;
        int energyOutputs = 0;
        for (ProcessingComponent processingComponent : runtime.components()) {
            MachineComponent component = processingComponent.getComponent();
            if (component == null || component.kind() == null) continue;
            switch (component.kind().id()) {
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
    }
}
