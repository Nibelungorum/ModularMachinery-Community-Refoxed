package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
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
        MachineStateSnapshot machineState = SYNC_RUNTIME.machineState(runtime);
        FactorySnapshot factory = SYNC_RUNTIME.factoryState(runtime);
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        data.putBoolean("formed", machineState.formed());
        data.putBoolean("active", machineState.active());
        data.putBoolean("tickMachine", machine != null && machine.behavior() instanceof TickBehavior);
        data.putLong("parallelism", machineState.parallelism());
        data.putLong("maxParallelism", machineState.maxParallelism());
        data.putInt("parallelSlots", factory.parallelSlots());
        data.putLong("maxParallelSlots", machineState.maxParallelControllerCount());
        data.putBoolean("factorySupported", runtime.factorySupported());
        data.putBoolean("factoryPresent", machineState.factoryControllerPresent());
        data.putInt("factoryLanes", factory.activeLaneCount());
        data.putInt("factoryThreadLimit", factory.laneLimit());
        String activeRecipe = machineState.activeRecipe();
        if (!activeRecipe.isEmpty()) data.putString("activeRecipe", activeRecipe);
        data.putInt("tick", machineState.tick());
        data.putInt("totalTick", machineState.totalTick());

        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        int energyInputs = 0;
        int energyOutputs = 0;
        for (ControllerRuntimeSnapshot.ComponentPresentation component : machineState.components()) {
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
