package cn.howxu.mmcr.compat.jade;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerComponentProviderTest {

    @Test
    void normal_controller_lines_include_recipe_progress_ports_parallel_slots_and_parallelism() {
        MachineControllerComponentProvider.Snapshot snapshot = snapshot(false);

        assertThat(MachineControllerComponentProvider.lineKeys(snapshot))
                .containsExactly("machine", "structure", "state", "recipe", "progress", "parallel_slots", "parallelism");
    }

    @Test
    void threaded_controller_lines_hide_recipe_and_progress_and_show_multithreading() {
        MachineControllerComponentProvider.Snapshot snapshot = snapshot(true);

        assertThat(MachineControllerComponentProvider.lineKeys(snapshot))
                .containsExactly("machine", "structure", "state", "parallel_slots", "parallelism", "threads");
    }

    @Test
    void controller_without_parallel_controllers_hides_parallel_lines() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putInt("parallelism", 0);
        tag.putInt("maxParallelism", 1);

        assertThat(MachineControllerComponentProvider.lineKeys(MachineControllerComponentProvider.Snapshot.from(tag)))
                .containsExactly("structure", "state");
    }

    private static MachineControllerComponentProvider.Snapshot snapshot(boolean threaded) {
        CompoundTag tag = new CompoundTag();
        tag.putString("machine", "mmcr:blast_furnace");
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);
        tag.putString("activeRecipe", "mmcr:test_recipe");
        tag.putInt("tick", 20);
        tag.putInt("totalTick", 100);
        tag.putInt("parallelism", threaded ? 2 : 0);
        tag.putInt("maxParallelism", 4);
        tag.putInt("parallelSlots", 1);
        tag.putInt("maxParallelSlots", 4);
        tag.putInt("itemInputs", 1);
        tag.putInt("energyInputs", 1);
        if (threaded) {
            tag.putBoolean("factorySupported", true);
            tag.putBoolean("factoryPresent", true);
            tag.putInt("factoryLanes", 2);
            tag.putInt("factoryThreadLimit", 8);
        }
        return MachineControllerComponentProvider.Snapshot.from(tag);
    }
}
