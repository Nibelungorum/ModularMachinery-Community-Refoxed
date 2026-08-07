package cn.howxu.mmcr.compat.jade;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerComponentProviderSnapshotTest {

    @Test
    void derives_unformed_status_without_progress_when_total_tick_is_zero() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", false);
        tag.putBoolean("active", true);
        tag.putInt("tick", 20);
        tag.putInt("totalTick", 0);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.status()).isEqualTo("unformed");
        assertThat(snapshot.hasProgress()).isFalse();
        assertThat(snapshot.progressPercent()).isZero();
    }

    @Test
    void derives_working_status_progress_and_parallelism_from_active_recipe() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);
        tag.putString("activeRecipe", "mmcr:test_recipe");
        tag.putInt("tick", 25);
        tag.putInt("totalTick", 100);
        tag.putInt("parallelism", 4);
        tag.putInt("maxParallelism", 16);
        tag.putInt("factoryLanes", 2);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.status()).isEqualTo("working");
        assertThat(snapshot.hasProgress()).isTrue();
        assertThat(snapshot.progressPercent()).isEqualTo(25);
        assertThat(snapshot.parallelism()).isEqualTo(4);
        assertThat(snapshot.maxParallelism()).isEqualTo(16);
        assertThat(snapshot.shouldShowParallelism()).isTrue();
        assertThat(snapshot.factoryLanes()).isEqualTo(2);
        assertThat(snapshot.shouldShowFactoryLanes()).isTrue();
    }

    @Test
    void keeps_backward_safe_parallel_and_factory_defaults() {
        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(new CompoundTag());

        assertThat(snapshot.parallelism()).isEqualTo(1);
        assertThat(snapshot.maxParallelism()).isEqualTo(1);
        assertThat(snapshot.factoryLanes()).isZero();
        assertThat(snapshot.shouldShowParallelism()).isFalse();
        assertThat(snapshot.shouldShowFactoryLanes()).isFalse();
    }

    @Test
    void counts_component_kinds_by_io_direction() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("itemInputs", 2);
        tag.putInt("itemOutputs", 1);
        tag.putInt("fluidInputs", 3);
        tag.putInt("fluidOutputs", 4);
        tag.putInt("energyInputs", 5);
        tag.putInt("energyOutputs", 6);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.itemInputs()).isEqualTo(2);
        assertThat(snapshot.itemOutputs()).isEqualTo(1);
        assertThat(snapshot.fluidInputs()).isEqualTo(3);
        assertThat(snapshot.fluidOutputs()).isEqualTo(4);
        assertThat(snapshot.energyInputs()).isEqualTo(5);
        assertThat(snapshot.energyOutputs()).isEqualTo(6);
    }
}
