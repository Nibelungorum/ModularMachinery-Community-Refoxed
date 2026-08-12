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
        tag.putInt("parallelSlots", 1);
        tag.putInt("maxParallelSlots", 4);
        tag.putBoolean("factoryPresent", true);
        tag.putInt("factoryLanes", 2);
        tag.putInt("factoryThreadLimit", 3);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.status()).isEqualTo("working");
        assertThat(snapshot.hasProgress()).isTrue();
        assertThat(snapshot.progressPercent()).isEqualTo(25);
        assertThat(snapshot.parallelism()).isEqualTo(4);
        assertThat(snapshot.maxParallelism()).isEqualTo(16);
        assertThat(snapshot.parallelSlots()).isEqualTo(1);
        assertThat(snapshot.maxParallelSlots()).isEqualTo(4);
        assertThat(snapshot.shouldShowParallelSlots()).isTrue();
        assertThat(snapshot.factoryLanes()).isEqualTo(2);
        assertThat(snapshot.factoryThreadLimit()).isEqualTo(3);
        assertThat(snapshot.shouldShowFactoryLanes()).isTrue();
    }

    @Test
    void ignores_stale_active_flag_when_no_recipe_or_factory_threads_are_present() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.status()).isEqualTo("idle");
        assertThat(snapshot.hasProgress()).isFalse();
    }

    @Test
    void inactive_snapshot_ignores_stale_active_recipe_from_previous_jade_payload() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("active", false);
        tag.putString("activeRecipe", "mmcr:finished_recipe");
        tag.putInt("tick", 20);
        tag.putInt("totalTick", 20);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.status()).isEqualTo("idle");
        assertThat(snapshot.hasProgress()).isFalse();
    }

    @Test
    void keeps_backward_safe_parallel_and_factory_defaults() {
        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(new CompoundTag());

        assertThat(snapshot.parallelism()).isZero();
        assertThat(snapshot.maxParallelism()).isEqualTo(1);
        assertThat(snapshot.factorySupported()).isFalse();
        assertThat(snapshot.factoryLanes()).isZero();
        assertThat(snapshot.factoryThreadLimit()).isEqualTo(1);
        assertThat(snapshot.shouldShowParallelSlots()).isFalse();
        assertThat(snapshot.shouldShowFactoryLanes()).isFalse();
    }

    @Test
    void idle_parallel_snapshot_shows_slot_count_and_zero_running_parallelism() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("active", false);
        tag.putInt("parallelism", 0);
        tag.putInt("maxParallelism", 4);
        tag.putInt("parallelSlots", 1);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.parallelSlots()).isEqualTo(1);
        assertThat(snapshot.parallelism()).isZero();
        assertThat(snapshot.maxParallelism()).isEqualTo(4);
        assertThat(snapshot.shouldShowParallelSlots()).isTrue();
        assertThat(snapshot.shouldShowParallelism()).isTrue();
    }

    @Test
    void factory_diagnostics_show_active_lanes_and_thread_limit() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);
        tag.putBoolean("factorySupported", true);
        tag.putBoolean("factoryPresent", true);
        tag.putInt("factoryLanes", 2);
        tag.putInt("factoryThreadLimit", 3);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.status()).isEqualTo("working");
        assertThat(snapshot.shouldShowFactoryLanes()).isTrue();
        assertThat(snapshot.factoryLanes()).isEqualTo(2);
        assertThat(snapshot.factoryThreadLimit()).isEqualTo(3);
        assertThat(snapshot.shouldShowParallelism()).isFalse();
    }

    @Test
    void hides_factory_lanes_when_supported_without_thread_controller() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("factorySupported", true);
        tag.putInt("factoryLanes", 0);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.factorySupported()).isTrue();
        assertThat(snapshot.factoryLanes()).isZero();
        assertThat(snapshot.shouldShowFactoryLanes()).isFalse();
    }

    @Test
    void shows_factory_lanes_when_thread_controller_exists_without_active_lanes() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("factoryPresent", true);
        tag.putInt("factoryLanes", 0);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.factoryPresent()).isTrue();
        assertThat(snapshot.factoryLanes()).isZero();
        assertThat(snapshot.shouldShowFactoryLanes()).isTrue();
    }

    @Test
    void factory_snapshot_hides_parallelism_when_thread_controller_exists() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("factoryPresent", true);
        tag.putInt("parallelism", 7);
        tag.putInt("maxParallelism", 524);

        MachineControllerComponentProvider.Snapshot snapshot = MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(MachineControllerComponentProvider.lineKeys(snapshot))
                .doesNotContain("parallelism")
                .contains("threads");
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
