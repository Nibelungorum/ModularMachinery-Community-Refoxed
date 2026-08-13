package cn.howxu.mmcr.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.BooleanSupplier;

/**
 * Client-side loop sound bound to a single machine controller position.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineLoopSound extends AbstractTickableSoundInstance implements MachineSoundManager.ReconciledSound {
    private final BooleanSupplier shouldPlay;

    public MachineLoopSound(SoundEvent sound, BlockPos pos, BooleanSupplier shouldPlay) {
        super(sound, SoundSource.BLOCKS, RandomSource.create());
        this.shouldPlay = shouldPlay;
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
        this.looping = true;
    }

    @Override
    public void tick() {
        if (!shouldPlay.getAsBoolean()) {
            stop();
        }
    }

    @Override
    public void stopSound() {
        stop();
    }
}
