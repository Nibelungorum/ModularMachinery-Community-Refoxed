package cn.howxu.mmcr.client.sound;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

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

    public void clientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        reconcile(descriptorsFor(level), (key, soundId) -> {
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

    private static List<ControllerDescriptor> descriptorsFor(ClientLevel level) {
        return loadedBlockEntities(level).stream()
                .filter(MachineControllerBlockEntity.class::isInstance)
                .map(MachineControllerBlockEntity.class::cast)
                .map(controller -> descriptorFor(level, controller))
                .toList();
    }

    private static List<BlockEntity> loadedBlockEntities(ClientLevel level) {
        int viewDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        ChunkPos center = Minecraft.getInstance().player == null ? new ChunkPos(0, 0)
                : Minecraft.getInstance().player.chunkPosition();
        return ChunkPos.rangeClosed(center, viewDistance)
                .map(chunkPos -> level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z()))
                .filter(chunk -> chunk instanceof LevelChunk)
                .map(LevelChunk.class::cast)
                .flatMap(chunk -> chunk.getBlockEntities().values().stream())
                .toList();
    }

    private static ControllerDescriptor descriptorFor(ClientLevel level, MachineControllerBlockEntity controller) {
        Identifier soundId = null;
        if (controller.getFoundMachine() != null) {
            MachineRegistration registration = MachineDefinitions.getRegistration(controller.getFoundMachine().registryName());
            soundId = registration == null ? null : registration.runningSoundId();
        }
        return new ControllerDescriptor(
                new ControllerKey(level.dimension(), controller.getBlockPos()), soundId, controller.isRuntimeActive());
    }

    record ControllerKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    record ControllerDescriptor(ControllerKey key, Identifier soundId, boolean active) {
    }

    private record TrackedSound(Identifier soundId, ReconciledSound sound) {
    }

    interface ReconciledSound {
        void stopSound();
    }

    interface SoundFactory {
        ReconciledSound create(ControllerKey key, Identifier soundId);
    }
}
