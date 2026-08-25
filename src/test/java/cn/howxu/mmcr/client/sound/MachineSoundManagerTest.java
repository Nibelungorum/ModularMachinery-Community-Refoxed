package cn.howxu.mmcr.client.sound;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import net.minecraft.world.level.block.Blocks;
import static org.assertj.core.api.Assertions.assertThat;

class MachineSoundManagerTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("test", "overworld"));
    private static final Identifier LOOP_SOUND = Identifier.fromNamespaceAndPath("minecraft", "block.furnace.fire_crackle");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

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

    @Test
    void machine_id_can_be_derived_from_controller_block_state_without_found_machine() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("test", "client_synced_machine");
        MachineControllerBlock controllerBlock = testControllerBlock(machineId);

        assertThat(MachineSoundManager.machineIdFromState(controllerBlock.defaultBlockState())).isEqualTo(machineId);
    }

    @Test
    void descriptor_uses_controller_block_machine_id_when_found_machine_is_not_synced() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("test", "client_synced_descriptor_machine");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(
                MachineRegistration.builder(machineId).runningSound(LOOP_SOUND).build());
        MachineDefinitions.freezeRegistryPhase();
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        setField(BlockEntity.class, controller, "worldPosition", new BlockPos(4, 5, 6));
        MachineControllerBlock controllerBlock = testControllerBlock(machineId);
        setField(BlockEntity.class, controller, "blockState", controllerBlock.defaultBlockState());
        Level level = LevelStub.create(Map.of(new BlockPos(4, 5, 6), controllerBlock), List.of(controller));
        setField(Level.class, level, "isClientSide", true);
        setField(BlockEntity.class, controller, "level", level);
        setField(MachineControllerBlockEntity.class, controller, "clientActive", true);

        MachineSoundManager.ControllerDescriptor descriptor = MachineSoundManager.descriptorForTest(OVERWORLD, controller);

        assertThat(controller.structureSnapshot().machine()).isNull();
        assertThat(descriptor.active()).isTrue();
        assertThat(descriptor.soundId()).isEqualTo(LOOP_SOUND);
    }

    @Test
    void manager_removes_loop_when_sound_factory_cannot_resolve_new_sound() {
        MachineSoundManager manager = new MachineSoundManager();
        MachineSoundManager.ControllerKey key = new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3));
        AtomicInteger stopped = new AtomicInteger();
        manager.reconcile(List.of(activeController(key, LOOP_SOUND)), (trackedKey, soundId) -> stopped::incrementAndGet);

        manager.reconcile(List.of(activeController(key, Identifier.fromNamespaceAndPath("test", "missing.loop"))),
                (trackedKey, soundId) -> null);

        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(1);
    }

    @Test
    void clear_stops_all_tracked_loops() {
        MachineSoundManager manager = new MachineSoundManager();
        AtomicInteger stopped = new AtomicInteger();
        manager.reconcile(List.of(
                activeController(new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3)), LOOP_SOUND),
                activeController(new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(4, 5, 6)), LOOP_SOUND)),
                (trackedKey, soundId) -> stopped::incrementAndGet);

        manager.clear();

        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(2);
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

    private static MachineControllerBlock testControllerBlock(Identifier machineId) throws Exception {
        TestBootstrap.bindControllerForTesting(machineId);
        return (MachineControllerBlock) ModBlocks.controllerFor(machineId).get();
    }

    private static MachineControllerBlockEntity controllerBlockEntityWithoutRunningMinecraftConstructor() throws Exception {
        Identifier machineId = Identifier.fromNamespaceAndPath("test", "client_synced_descriptor_machine");
        TestBootstrap.bindControllerForTesting(machineId);
        return RuntimeTestFixtures.controllerEntity(machineId, new BlockPos(4, 5, 6));
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
