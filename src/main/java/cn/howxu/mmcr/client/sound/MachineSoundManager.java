package cn.howxu.mmcr.client.sound;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles active machine controllers with client-side looping sound instances.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineSoundManager {
    private final Map<ControllerKey, TrackedSound> tracked = new HashMap<>();
    private final LoadedSoundControllerTracker loadedControllers;

    public MachineSoundManager() {
        this(new LoadedSoundControllerTracker());
    }

    public MachineSoundManager(LoadedSoundControllerTracker loadedControllers) {
        this.loadedControllers = loadedControllers;
    }

    public void clientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        loadedControllers.prune(level.getChunkSource());
        ChunkPos center = minecraft.player == null ? new ChunkPos(0, 0) : minecraft.player.chunkPosition();
        reconcile(descriptorsFor(level.dimension(), center, minecraft.options.getEffectiveRenderDistance()), (key, soundId) -> {
            SoundEvent sound = MachineSoundRegistry.get(soundId);
            if (sound == null) return null;
            MachineLoopSound instance = new MachineLoopSound(sound, key.pos(), () -> tracked.containsKey(key));
            minecraft.getSoundManager().play(instance);
            return instance;
        });
    }

    public void clear() {
        tracked.values().forEach(trackedSound -> trackedSound.sound().stopSound());
        tracked.clear();
        loadedControllers.clear();
    }

    void reconcile(Collection<ControllerDescriptor> controllers) {
        reconcile(controllers, (key, soundId) -> () -> { });
    }

    int trackedCount() {
        return tracked.size();
    }

    void reconcile(Collection<ControllerDescriptor> controllers, SoundFactory soundFactory) {
        Set<ControllerKey> desired = new HashSet<>();
        for (ControllerDescriptor controller : controllers) {
            if (!controller.active() || controller.soundId() == null) continue;
            desired.add(controller.key());
            TrackedSound existing = tracked.get(controller.key());
            if (existing != null && !existing.soundId().equals(controller.soundId())) {
                existing.sound().stopSound();
                tracked.remove(controller.key());
            }
            tracked.computeIfAbsent(controller.key(), key -> {
                ReconciledSound sound = soundFactory.create(key, controller.soundId());
                return sound == null ? null : new TrackedSound(controller.soundId(), sound);
            });
        }

        tracked.entrySet().removeIf(entry -> {
            if (desired.contains(entry.getKey())) return false;
            entry.getValue().sound().stopSound();
            return true;
        });
    }

    List<ControllerDescriptor> descriptorsFor(ResourceKey<Level> dimension, ChunkPos center, int viewDistance) {
        return loadedControllers.controllers().stream()
                .filter(controller -> {
                    ChunkPos pos = new ChunkPos(controller.getBlockPos());
                    return Math.abs(pos.x - center.x) <= viewDistance
                            && Math.abs(pos.z - center.z) <= viewDistance;
                })
                .map(controller -> descriptorFor(new ControllerKey(dimension, controller.getBlockPos()), controller))
                .toList();
    }

    private static ResourceLocation controllerMachineId(MachineControllerBlockEntity controller) {
        ResourceLocation stateMachineId = machineIdFromState(controller.getBlockState());
        if (stateMachineId != null) return stateMachineId;
        var structure = controller.runtimeSnapshot().structure();
        if (structure.configuredMachine() != null) return structure.configuredMachine().registryName();
        if (structure.machine() != null) return structure.machine().registryName();
        return null;
    }

    static ResourceLocation machineIdFromState(BlockState state) {
        if (state.getBlock() instanceof MachineControllerBlock block) return block.machineId();
        return null;
    }

    static ControllerDescriptor descriptorForTest(ResourceKey<Level> dimension, MachineControllerBlockEntity controller) {
        return descriptorFor(new ControllerKey(dimension, controller.getBlockPos()), controller);
    }

    private static ControllerDescriptor descriptorFor(ControllerKey key, MachineControllerBlockEntity controller) {
        ResourceLocation soundId = null;
        MachineRegistration registration = MachineDefinitions.getRegistration(controllerMachineId(controller));
        if (registration != null) soundId = registration.runningSoundId();
        return new ControllerDescriptor(key, soundId, controller.isRuntimeActive());
    }

    record ControllerKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    record ControllerDescriptor(ControllerKey key, ResourceLocation soundId, boolean active) {
    }

    private record TrackedSound(ResourceLocation soundId, ReconciledSound sound) {
    }

    interface ReconciledSound {
        void stopSound();
    }

    interface SoundFactory {
        ReconciledSound create(ControllerKey key, ResourceLocation soundId);
    }
}
