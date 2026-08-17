package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
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

    @Override
    public Identifier getUid() {
        return MachineControllerComponentProvider.UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof MachineControllerBlockEntity controller)) return;

        var foundMachine = controller.getFoundMachine();
        var boundMachine = controller.getMachine();
        var machine = foundMachine != null ? foundMachine : boundMachine;
        ActiveMachineRecipe active = controller.getActive();
        FactorySchedulerBlockEntity factory = controller.getFactoryController();

        if (machine != null) data.putString("machine", machine.registryName().toString());
        data.putBoolean("formed", controller.isFormed());
        data.putBoolean("active", controller.isRuntimeActive());
        data.putInt("parallelism", controller.currentParallelism());
        data.putInt("maxParallelism", active == null ? controller.getMaxParallelism() : active.getMaxParallelism());
        data.putInt("parallelSlots", controller.parallelControllerCount());
        data.putInt("maxParallelSlots", controller.maxParallelControllerCount());
        data.putBoolean("factorySupported", machine != null && machine.hasFactory());
        data.putBoolean("factoryPresent", factory != null);
        data.putInt("factoryLanes", factory == null ? 0 : factory.activeThreadCount());
        data.putInt("factoryThreadLimit", factory == null ? 1 : factory.threadLimit());
        if (active != null && active.getRecipe() != null) {
            data.putString("activeRecipe", active.getRecipe().id().toString());
            data.putInt("tick", active.getTick());
            data.putInt("totalTick", active.getTotalTick());
        }

        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        int energyInputs = 0;
        int energyOutputs = 0;
        for (ProcessingComponent processingComponent : controller.getComponents()) {
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
