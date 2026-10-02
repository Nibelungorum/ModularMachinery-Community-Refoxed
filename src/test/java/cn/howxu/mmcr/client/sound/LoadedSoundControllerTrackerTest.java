package cn.howxu.mmcr.client.sound;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies loaded sound controller ownership and unload invalidation.
 *
 * @author howxu <dev@howxu.cn>
 */
class LoadedSoundControllerTrackerTest {
    private static final ResourceLocation MACHINE_ID = ResourceLocation.fromNamespaceAndPath("test", "loaded_sound_controller");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bindControllerForTesting(MACHINE_ID);
    }

    @AfterEach
    void resetLifecycleListeners() {
        MachineControllerBlockEntity.setClientLifecycleListeners(controller -> { }, controller -> { });
    }

    @Test
    void duplicate_registration_keeps_one_controller_and_exposes_a_read_only_live_view() {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        var view = tracker.controllers();
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);

        tracker.loaded(controller);
        tracker.loaded(controller);

        assertThat(view).containsExactly(controller);
        assertThatThrownBy(view::clear).isInstanceOf(UnsupportedOperationException.class);
        tracker.removed(controller);
        assertThat(view).isEmpty();
    }

    @Test
    void stale_removal_does_not_remove_the_replacement_at_the_same_position() {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineControllerBlockEntity previous = controller(new BlockPos(5, 10, -3));
        MachineControllerBlockEntity replacement = controller(previous.getBlockPos());

        tracker.loaded(previous);
        tracker.loaded(replacement);
        tracker.removed(previous);

        assertThat(tracker.controllers()).containsExactly(replacement);
        tracker.removed(replacement);
        assertThat(tracker.controllers()).isEmpty();
    }

    @Test
    void chunk_unload_removes_only_controllers_in_that_chunk_including_negative_coordinates() {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineControllerBlockEntity first = controller(new BlockPos(-16, 10, -1));
        MachineControllerBlockEntity second = controller(new BlockPos(-1, 90, -16));
        MachineControllerBlockEntity neighbor = controller(new BlockPos(-17, 10, -1));
        tracker.loaded(first);
        tracker.loaded(second);
        tracker.loaded(neighbor);

        tracker.unloadChunk(new ChunkPos(-1, -1));
        tracker.unloadChunk(new ChunkPos(-1, -1));

        assertThat(tracker.controllers()).containsExactly(neighbor);
    }

    @Test
    void world_clear_allows_a_new_world_controller_at_the_same_position() {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineControllerBlockEntity previous = controller(BlockPos.ZERO);
        MachineControllerBlockEntity nextWorld = controller(BlockPos.ZERO);
        tracker.loaded(previous);

        tracker.clear();

        assertThat(tracker.controllers()).isEmpty();
        tracker.loaded(nextWorld);
        tracker.removed(previous);
        assertThat(tracker.controllers()).containsExactly(nextWorld);
    }

    @Test
    void client_lifecycle_tracks_load_replacement_removal_and_chunk_reload() throws Exception {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineControllerBlockEntity.setClientLifecycleListeners(tracker::loaded, tracker::removed);
        MachineControllerBlockEntity previous = controller(BlockPos.ZERO);
        MachineControllerBlockEntity replacement = controller(BlockPos.ZERO);
        Level level = level(true);
        previous.setLevel(level);
        replacement.setLevel(level);

        previous.onLoad();
        assertThat(tracker.controllers()).containsExactly(previous);
        replacement.onLoad();
        previous.setRemoved();
        assertThat(tracker.controllers()).containsExactly(replacement);

        replacement.onChunkUnloaded();
        assertThat(tracker.controllers()).isEmpty();
        replacement.onLoad();
        assertThat(tracker.controllers()).containsExactly(replacement);
        replacement.setRemoved();
        assertThat(tracker.controllers()).isEmpty();
    }

    @Test
    void server_and_level_less_lifecycle_never_calls_client_listeners() throws Exception {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineControllerBlockEntity client = controller(BlockPos.ZERO);
        tracker.loaded(client);
        MachineControllerBlockEntity.setClientLifecycleListeners(
                controller -> { throw new AssertionError("Server registered a client sound controller"); },
                controller -> { throw new AssertionError("Server removed a client sound controller"); });
        MachineControllerBlockEntity server = controller(BlockPos.ZERO);
        server.setLevel(level(false));
        MachineControllerBlockEntity levelLess = controller(BlockPos.ZERO);

        for (MachineControllerBlockEntity controller : new MachineControllerBlockEntity[]{server, levelLess}) {
            controller.onLoad();
            controller.onChunkUnloaded();
            controller.setRemoved();
        }

        assertThat(tracker.controllers()).containsExactly(client);
    }

    @Test
    void unregistered_client_listeners_are_no_ops() throws Exception {
        resetLifecycleListeners();
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);
        controller.setLevel(level(true));

        controller.onLoad();
        controller.onChunkUnloaded();
        controller.setRemoved();

        assertThat(controller.isRemoved()).isTrue();
    }

    private static Level level(boolean clientSide) throws Exception {
        Level level = LevelStub.create(Map.of());
        Field field = Level.class.getDeclaredField("isClientSide");
        field.setAccessible(true);
        field.set(level, clientSide);
        return level;
    }

    private static MachineControllerBlockEntity controller(BlockPos pos) {
        return RuntimeTestFixtures.controllerEntity(MACHINE_ID, pos);
    }
}
