package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the client-local controller idle easter egg without server state or synchronization.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerIdleEasterEggManager {
    private static final ResourceLocation DEFAULT_IDLE_OVERLAY = MMCR.id("block/overlay_basic_idle");
    private static final ControllerIdleEasterEggTracker TRACKER =
            new ControllerIdleEasterEggTracker(() -> ThreadLocalRandom.current().nextDouble());
    private static volatile long clientTicks;

    private ControllerIdleEasterEggManager() {
    }

    static boolean trackAndIsActive(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                    ResourceLocation machineId) {
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (!canTrackRenderView(level, clientLevel != null)) {
            return false;
        }
        if (!eligible(state, machineId)) {
            TRACKER.untrack(pos);
            return false;
        }
        long tick = clientTicks;
        TRACKER.track(pos, tick);
        return TRACKER.isActive(pos, tick);
    }

    static boolean canTrackRenderView(BlockAndTintGetter level, boolean currentLevelAvailable) {
        return currentLevelAvailable && level != BlockAndTintGetter.EMPTY;
    }

    public static void clientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        long tick = ++clientTicks;
        for (BlockPos pos : TRACKER.tick(tick, trackedPos -> eligible(level, trackedPos))) {
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.setSectionDirty(
                        SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getY()),
                        SectionPos.blockToSectionCoord(pos.getZ()));
            }
        }
    }

    public static void clear() {
        TRACKER.clear();
        clientTicks = 0L;
    }

    static boolean eligible(BlockState state, ResourceLocation machineId) {
        if (!(state.getBlock() instanceof MachineControllerBlock)
                || !state.getValue(MachineControllerBlock.FORMED)
                || state.getValue(MachineControllerBlock.ACTIVE)) {
            return false;
        }
        MachineAppearanceSpec appearance = machineId == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceCache.specFor(machineId);
        return appearance.controllerIdleOverlayTexture().equals(DEFAULT_IDLE_OVERLAY);
    }

    private static boolean eligible(ClientLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        ResourceLocation machineId = state.getBlock() instanceof MachineControllerBlock controller
                ? controller.machineId() : null;
        return eligible(state, machineId);
    }
}
