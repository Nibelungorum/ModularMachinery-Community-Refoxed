package cn.howxu.mmcr.client.sound;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.Client;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import net.minecraft.world.level.block.Blocks;
import static org.assertj.core.api.Assertions.assertThat;

class MachineSoundManagerTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("test", "overworld"));
    private static final ResourceLocation LOOP_SOUND = ResourceLocation.fromNamespaceAndPath("minecraft", "block.furnace.fire_crackle");

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

        manager.reconcile(List.of(activeController(key, ResourceLocation.fromNamespaceAndPath("test", "machine.loop.changed"))));

        assertThat(manager.trackedCount()).isEqualTo(1);
        assertThat(stopped).hasValue(1);
    }

    @Test
    void machine_id_can_be_derived_from_controller_block_state_without_found_machine() throws Exception {
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("test", "client_synced_machine");
        MachineControllerBlock controllerBlock = testControllerBlock(machineId);

        assertThat(MachineSoundManager.machineIdFromState(controllerBlock.defaultBlockState())).isEqualTo(machineId);
    }

    @Test
    void descriptor_uses_controller_block_machine_id_when_found_machine_is_not_synced() throws Exception {
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("test", "client_synced_descriptor_machine");
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

        manager.reconcile(List.of(activeController(key, ResourceLocation.fromNamespaceAndPath("test", "missing.loop"))),
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

    @Test
    void candidates_include_only_tracked_controllers_within_the_square_chunk_view_distance() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_candidates", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        MachineControllerBlockEntity diagonal = activeLoadedController(machineId, new BlockPos(-1, 5, 64));
        MachineControllerBlockEntity center = activeLoadedController(machineId, new BlockPos(-32, 5, 48));
        MachineControllerBlockEntity outsideX = activeLoadedController(machineId, new BlockPos(0, 5, 48));
        MachineControllerBlockEntity outsideZ = activeLoadedController(machineId, new BlockPos(-32, 5, 31));
        MachineControllerBlockEntity untracked = activeLoadedController(machineId, new BlockPos(-32, 6, 48));
        for (var controller : List.of(diagonal, center, outsideX, outsideZ)) tracker.loaded(controller);

        var candidates = manager.descriptorsFor(OVERWORLD, new ChunkPos(-2, 3), 1);

        assertThat(candidates).extracting(descriptor -> descriptor.key().pos())
                .containsExactlyInAnyOrder(diagonal.getBlockPos(), center.getBlockPos())
                .doesNotContain(untracked.getBlockPos());
        assertThat(manager.descriptorsFor(OVERWORLD, new ChunkPos(-2, 3), 0))
                .extracting(descriptor -> descriptor.key().pos()).containsExactly(center.getBlockPos());
    }

    @Test
    void render_distance_changes_and_player_movement_stop_out_of_range_loops() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_view_distance", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        tracker.loaded(activeLoadedController(machineId, new BlockPos(32, 5, 0)));
        AtomicInteger stopped = new AtomicInteger();
        MachineSoundManager.SoundFactory factory = (key, soundId) -> stopped::incrementAndGet;
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 2), factory);
        assertThat(manager.trackedCount()).isEqualTo(1);

        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 1), factory);
        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(1);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 2), factory);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(-1, 0), 2), factory);
        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(2);
    }

    @Test
    void inactive_or_removed_loaded_controller_stops_its_loop_and_inactive_controller_creates_none() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_active_lifecycle", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        MachineControllerBlockEntity controller = activeLoadedController(machineId, BlockPos.ZERO);
        tracker.loaded(controller);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        MachineSoundManager.SoundFactory factory = (key, soundId) -> {
            created.incrementAndGet();
            return stopped::incrementAndGet;
        };

        setField(MachineControllerBlockEntity.class, controller, "clientActive", false);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 1), factory);
        assertThat(created).hasValue(0);
        setField(MachineControllerBlockEntity.class, controller, "clientActive", true);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 1), factory);
        assertThat(created).hasValue(1);
        setField(MachineControllerBlockEntity.class, controller, "clientActive", false);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 1), factory);
        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(1);

        setField(MachineControllerBlockEntity.class, controller, "clientActive", true);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 1), factory);
        tracker.removed(controller);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 1), factory);
        assertThat(manager.trackedCount()).isZero();
        assertThat(created).hasValue(2);
        assertThat(stopped).hasValue(2);
    }

    @Test
    void definition_reload_replaces_the_loop_and_removing_running_sound_stops_it_without_creating_another() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_definition_reload", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        tracker.loaded(activeLoadedController(machineId, BlockPos.ZERO));
        AtomicInteger stopped = new AtomicInteger();
        AtomicInteger created = new AtomicInteger();
        MachineSoundManager.SoundFactory factory = (key, soundId) -> {
            created.incrementAndGet();
            return stopped::incrementAndGet;
        };
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 1), factory);

        ResourceLocation changedSound = ResourceLocation.fromNamespaceAndPath("minecraft", "block.lava.pop");
        registerSoundMachine("sound_definition_reload", changedSound);
        var reloaded = manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 1);
        assertThat(reloaded).extracting(MachineSoundManager.ControllerDescriptor::soundId).containsExactly(changedSound);
        manager.reconcile(reloaded, factory);
        assertThat(stopped).hasValue(1);
        assertThat(created).hasValue(2);

        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        MachineDefinitions.freezeRegistryPhase();
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 1), factory);
        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(2);
        assertThat(created).hasValue(2);
    }

    @Test
    void world_unload_handler_ignores_server_world_and_clears_client_controllers_and_loops() throws Exception {
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        tracker.loaded(controller);
        AtomicInteger stopped = new AtomicInteger();
        manager.reconcile(List.of(activeController(new MachineSoundManager.ControllerKey(OVERWORLD, controller.getBlockPos()), LOOP_SOUND)),
                (key, soundId) -> stopped::incrementAndGet);
        Client client = clientWithSoundManager(manager, tracker);
        Level level = LevelStub.create(Map.of());

        invokeClientHandler(client, "clearMachineSounds", LevelEvent.Unload.class, new LevelEvent.Unload(level));
        assertThat(tracker.controllers()).containsExactly(controller);
        assertThat(manager.trackedCount()).isEqualTo(1);
        assertThat(stopped).hasValue(0);

        setField(Level.class, level, "isClientSide", true);
        invokeClientHandler(client, "clearMachineSounds", LevelEvent.Unload.class, new LevelEvent.Unload(level));
        assertThat(tracker.controllers()).isEmpty();
        assertThat(manager.trackedCount()).isZero();
        assertThat(stopped).hasValue(1);
        manager.clear();
        assertThat(stopped).hasValue(1);
    }

    @Test
    void chunk_unload_handler_ignores_server_chunk_and_removes_only_the_unloaded_client_chunk() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_chunk_unload", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        MachineControllerBlockEntity unloaded = activeLoadedController(machineId, new BlockPos(-1, 5, -1));
        MachineControllerBlockEntity neighbor = activeLoadedController(machineId, BlockPos.ZERO);
        tracker.loaded(unloaded);
        tracker.loaded(neighbor);
        AtomicInteger stopped = new AtomicInteger();
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 1),
                (key, soundId) -> stopped::incrementAndGet);
        Client client = clientWithSoundManager(manager, tracker);
        Level level = LevelStub.create(Map.of());
        LevelChunk chunk = (LevelChunk) unsafe().allocateInstance(LevelChunk.class);
        setField(ChunkAccess.class, chunk, "chunkPos", new ChunkPos(-1, -1));
        setField(LevelChunk.class, chunk, "level", level);

        invokeClientHandler(client, "clearControllerScreenTextCache", ChunkEvent.Unload.class, new ChunkEvent.Unload(chunk));
        assertThat(tracker.controllers()).containsExactlyInAnyOrder(unloaded, neighbor);

        setField(Level.class, level, "isClientSide", true);
        invokeClientHandler(client, "clearControllerScreenTextCache", ChunkEvent.Unload.class, new ChunkEvent.Unload(chunk));
        assertThat(tracker.controllers()).containsExactly(neighbor);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, new ChunkPos(0, 0), 1));
        assertThat(manager.trackedCount()).isEqualTo(1);
        assertThat(stopped).hasValue(1);
    }

    @Test
    void actual_chunk_cache_resize_prunes_evicted_controllers_and_cannot_restart_stale_loops() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_cache_resize", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineSoundManager manager = new MachineSoundManager(tracker);
        MachineControllerBlockEntity center = activeLoadedController(machineId, BlockPos.ZERO);
        MachineControllerBlockEntity cachedOutsideSoundRange = activeLoadedController(machineId, new BlockPos(64, 5, 0));
        MachineControllerBlockEntity evicted = activeLoadedController(machineId, new BlockPos(128, 5, 0));
        ClientChunkCache cache = chunkCache(15);
        for (var controller : List.of(center, cachedOutsideSoundRange, evicted)) {
            cacheChunk(cache, controller);
            tracker.loaded(controller);
        }
        AtomicInteger created = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        MachineSoundManager.SoundFactory factory = (key, soundId) -> {
            created.incrementAndGet();
            return stopped::incrementAndGet;
        };
        tracker.prune(cache);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 12), factory);
        assertThat(created).hasValue(3);

        cache.updateViewRadius(2);
        cache.drop(new ChunkPos(evicted.getBlockPos()));
        assertThat(evicted.isRemoved()).isFalse(); // Resize and the subsequent forget skip BE callbacks.
        tracker.prune(cache);
        assertThat(tracker.controllers()).containsExactlyInAnyOrder(center, cachedOutsideSoundRange);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 2), factory);
        assertThat(manager.trackedCount()).isEqualTo(1);
        assertThat(stopped).hasValue(2);

        cache.updateViewRadius(12);
        tracker.prune(cache);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 12), factory);
        assertThat(manager.trackedCount()).isEqualTo(2);
        assertThat(created).hasValue(4); // The retained cached controller resumes; the evicted one cannot.
        MachineControllerBlockEntity reloaded = activeLoadedController(machineId, evicted.getBlockPos());
        cacheChunk(cache, reloaded);
        tracker.loaded(reloaded);
        tracker.removed(evicted);
        tracker.prune(cache);
        manager.reconcile(manager.descriptorsFor(OVERWORLD, ChunkPos.ZERO, 12), factory);
        assertThat(tracker.controllers()).contains(reloaded).doesNotContain(evicted);
        assertThat(created).hasValue(5);
    }

    @Test
    void prune_checks_actual_cached_be_identity_and_removed_state() throws Exception {
        ResourceLocation machineId = registerSoundMachine("sound_cached_identity", LOOP_SOUND);
        LoadedSoundControllerTracker tracker = new LoadedSoundControllerTracker();
        MachineControllerBlockEntity stale = activeLoadedController(machineId, BlockPos.ZERO);
        MachineControllerBlockEntity replacement = activeLoadedController(machineId, BlockPos.ZERO);
        ClientChunkCache cache = chunkCache(5);
        cacheChunk(cache, replacement);
        tracker.loaded(stale);

        tracker.prune(cache);
        assertThat(tracker.controllers()).isEmpty();
        tracker.loaded(replacement);
        tracker.removed(stale);
        tracker.prune(cache);
        assertThat(tracker.controllers()).containsExactly(replacement);
        replacement.setRemoved();
        tracker.prune(cache);
        assertThat(tracker.controllers()).isEmpty();
    }

    private static ClientChunkCache chunkCache(int radius) throws Exception {
        // Only storage is needed to execute the real cache lookup/resize path without a renderer.
        ClientChunkCache cache = (ClientChunkCache) unsafe().allocateInstance(ClientChunkCache.class);
        Class<?> storageType = Class.forName(ClientChunkCache.class.getName() + "$Storage");
        var constructor = storageType.getDeclaredConstructor(ClientChunkCache.class, int.class);
        constructor.setAccessible(true);
        setField(ClientChunkCache.class, cache, "storage", constructor.newInstance(cache, radius));
        return cache;
    }

    private static void cacheChunk(ClientChunkCache cache, MachineControllerBlockEntity controller) throws Exception {
        ChunkPos pos = new ChunkPos(controller.getBlockPos());
        LevelChunk chunk = (LevelChunk) unsafe().allocateInstance(LevelChunk.class);
        setField(ChunkAccess.class, chunk, "chunkPos", pos);
        setField(ChunkAccess.class, chunk, "sections", new LevelChunkSection[0]);
        setField(ChunkAccess.class, chunk, "blockEntities", Map.of(controller.getBlockPos(), controller));
        Field storageField = ClientChunkCache.class.getDeclaredField("storage");
        storageField.setAccessible(true);
        Object storage = storageField.get(cache);
        var indexMethod = storage.getClass().getDeclaredMethod("getIndex", int.class, int.class);
        indexMethod.setAccessible(true);
        Field chunksField = storage.getClass().getDeclaredField("chunks");
        chunksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var chunks = (AtomicReferenceArray<LevelChunk>) chunksField.get(storage);
        chunks.set((int) indexMethod.invoke(storage, pos.x, pos.z), chunk);
    }

    private static ResourceLocation registerSoundMachine(String path, ResourceLocation soundId) {
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("test", path);
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).runningSound(soundId).build());
        MachineDefinitions.freezeRegistryPhase();
        return machineId;
    }

    private static MachineControllerBlockEntity activeLoadedController(ResourceLocation machineId, BlockPos pos) throws Exception {
        TestBootstrap.bindControllerForTesting(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(machineId, pos);
        Level level = LevelStub.create(Map.of());
        setField(Level.class, level, "isClientSide", true);
        controller.setLevel(level);
        setField(MachineControllerBlockEntity.class, controller, "clientActive", true);
        return controller;
    }

    private static Client clientWithSoundManager(MachineSoundManager manager, LoadedSoundControllerTracker tracker) throws Exception {
        Client client = (Client) unsafe().allocateInstance(Client.class);
        setField(Client.class, client, "machineSoundManager", manager);
        setField(Client.class, client, "loadedSoundControllers", tracker);
        return client;
    }

    private static <T> void invokeClientHandler(Client client, String name, Class<T> eventType, T event) throws Exception {
        var method = Client.class.getDeclaredMethod(name, eventType);
        method.setAccessible(true);
        method.invoke(client, event);
    }

    private static MachineSoundManager managerWithTrackedActiveController() {
        MachineSoundManager manager = new MachineSoundManager();
        MachineSoundManager.ControllerKey key = new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3));
        manager.reconcile(List.of(activeController(key, LOOP_SOUND)));
        return manager;
    }

    private static MachineSoundManager.ControllerDescriptor activeController(
            MachineSoundManager.ControllerKey key, ResourceLocation soundId) {
        return new MachineSoundManager.ControllerDescriptor(key, soundId, true);
    }

    private static MachineSoundManager.ControllerDescriptor inactiveController() {
        return new MachineSoundManager.ControllerDescriptor(
                new MachineSoundManager.ControllerKey(OVERWORLD, new BlockPos(1, 2, 3)), LOOP_SOUND, false);
    }

    private static MachineControllerBlock testControllerBlock(ResourceLocation machineId) throws Exception {
        TestBootstrap.bindControllerForTesting(machineId);
        return (MachineControllerBlock) ModBlocks.controllerFor(machineId).get();
    }

    private static MachineControllerBlockEntity controllerBlockEntityWithoutRunningMinecraftConstructor() throws Exception {
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("test", "client_synced_descriptor_machine");
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
