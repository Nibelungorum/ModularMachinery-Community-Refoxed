package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import sonar.fluxnetworks.common.data.FluxDeviceConfigComponent;
import sonar.fluxnetworks.common.device.FluxPlugHandler;

/**
 * Admits recipe output into the native Plug buffer without adjacent transfers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkPlugHandler extends FluxPlugHandler {
    private final BooleanSupplier active;
    private final LongSupplier bufferLimiter;
    private final Runnable onChange;
    private final LongValueStorage mirror = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
    // Native mRemoved is private; guard limit reductions before native removal can become negative.
    private long removedThisCycle;

    public FluxNetworkPlugHandler(BooleanSupplier active, LongSupplier bufferLimiter, Runnable onChange) {
        this.active = active;
        this.bufferLimiter = bufferLimiter;
        this.onChange = onChange;
    }

    public LongValueStorage storage() { return mirror; }

    /** Matches native receive admission at the buffer produced by earlier reservations. */
    public long admissionAt(long virtualBuffer) {
        if (!active.getAsBoolean()) return 0L;
        long limit = getLimit();
        long limiter = bufferLimiter.getAsLong();
        if (virtualBuffer < 0L || virtualBuffer >= limit || virtualBuffer >= limiter) return 0L;
        long remaining = limiter - virtualBuffer;
        if (remaining <= virtualBuffer) return 0L;
        return Math.min(limit - virtualBuffer, remaining - virtualBuffer);
    }

    public boolean acceptRecipeEnergy(long amount) {
        if (amount <= 0L || admissionAt(mBuffer) < amount) return false;
        long limiter = bufferLimiter.getAsLong();
        if (super.receive(amount, Direction.DOWN, true, limiter) != amount) return false;
        long accepted = super.receive(amount, Direction.DOWN, false, limiter);
        if (accepted != amount) throw new IllegalStateException("Flux native receive changed during commit");
        changed();
        return true;
    }

    @Override
    public long removeFromBuffer(long amount) {
        if (amount <= 0L || removedThisCycle >= getLimit()) return 0L;
        long removed = super.removeFromBuffer(amount);
        if (removed > 0L) {
            removedThisCycle += removed;
            changed();
        }
        return removed;
    }

    @Override
    public void onCycleEnd() {
        super.onCycleEnd();
        removedThisCycle = 0L;
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        mirror.setAmount(mBuffer);
    }

    @Override
    public void applyConfiguration(FluxDeviceConfigComponent config, long energy) {
        super.applyConfiguration(config, energy);
        mirror.setAmount(mBuffer);
    }

    @Override
    public void readPacketBuffer(FriendlyByteBuf buffer, byte type) {
        super.readPacketBuffer(buffer, type);
        mirror.setAmount(mBuffer);
    }

    private void changed() {
        mirror.setAmount(mBuffer);
        onChange.run();
    }
}
