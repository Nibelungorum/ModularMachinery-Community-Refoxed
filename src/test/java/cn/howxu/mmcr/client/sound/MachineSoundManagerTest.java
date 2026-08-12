package cn.howxu.mmcr.client.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MachineSoundManagerTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("test", "overworld"));
    private static final Identifier LOOP_SOUND = Identifier.fromNamespaceAndPath("test", "machine.loop");

    @Test
    void manager_tracks_one_loop_per_dimension_and_controller_position() {
        MachineSoundManager manager = new MachineSoundManager();
        MachineSoundManager.ControllerKey key = new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3));

        manager.reconcile(List.of(activeController(key, LOOP_SOUND), activeController(key, LOOP_SOUND)));

        assertThat(manager.trackedCount()).isEqualTo(1);
    }

    @Test
    void manager_removes_loop_when_controller_becomes_inactive_or_disappears() {
        MachineSoundManager manager = managerWithTrackedActiveController();

        manager.reconcile(List.of(inactiveController()));

        assertThat(manager.trackedCount()).isZero();
    }

    @Test
    void manager_replaces_loop_when_sound_id_changes() {
        MachineSoundManager manager = new MachineSoundManager();
        MachineSoundManager.ControllerKey key = new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3));
        AtomicInteger stopped = new AtomicInteger();
        manager.reconcile(List.of(activeController(key, LOOP_SOUND)), (trackedKey, soundId) -> stopped::incrementAndGet);

        manager.reconcile(List.of(activeController(key, Identifier.fromNamespaceAndPath("test", "machine.loop.changed"))));

        assertThat(manager.trackedCount()).isEqualTo(1);
        assertThat(stopped).hasValue(1);
    }

    private static MachineSoundManager managerWithTrackedActiveController() {
        MachineSoundManager manager = new MachineSoundManager();
        MachineSoundManager.ControllerKey key = new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3));
        manager.reconcile(List.of(activeController(key, LOOP_SOUND)));
        return manager;
    }

    private static MachineSoundManager.ControllerDescriptor activeController(
            MachineSoundManager.ControllerKey key, Identifier soundId) {
        return new MachineSoundManager.ControllerDescriptor(key, soundId, true);
    }

    private static MachineSoundManager.ControllerDescriptor inactiveController() {
        return new MachineSoundManager.ControllerDescriptor(
                new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3)), LOOP_SOUND, false);
    }
}
