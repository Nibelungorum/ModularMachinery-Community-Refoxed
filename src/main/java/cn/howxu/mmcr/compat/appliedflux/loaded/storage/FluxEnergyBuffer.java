package cn.howxu.mmcr.compat.appliedflux.loaded.storage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.minecraft.nbt.CompoundTag;

/**
 * Local AppFlux energy cache. Network operations are deliberately native and
 * immediate: neither AE2 nor a foreign energy store offers rollback here.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxEnergyBuffer {
    public static final long IDLE_SOFT_LIMIT = 10_000L;
    public static final int IDLE_DELAY_TICKS = 200;

    private final LongValueStorage storage;
    private long reserved;
    private long idleExcess;
    private int idleTicks;

    public FluxEnergyBuffer() {
        this(null);
    }

    public FluxEnergyBuffer(Runnable onChange) {
        storage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, onChange);
    }

    public LongValueStorage storage() {
        return storage;
    }

    public long amount() {
        return storage.amount();
    }

    public long reserved() {
        return reserved;
    }

    public long idleExcess() {
        return idleExcess;
    }

    public boolean isIdleReady() {
        return idleExcess > IDLE_SOFT_LIMIT && idleTicks >= IDLE_DELAY_TICKS;
    }

    public void reserve(long requested) {
        if (requested <= 0L || requested > available()) throw new IllegalArgumentException("Invalid reservation");
        reserved += requested;
        idleExcess -= Math.min(idleExcess, requested);
        resetIdleTicks();
    }

    public long extract(long requested) {
        long extracted = storage.extract(requested, false);
        if (extracted <= 0L) return 0L;
        long reservedExtracted = Math.min(reserved, extracted);
        reserved -= reservedExtracted;
        idleExcess -= Math.min(idleExcess, extracted - reservedExtracted);
        resetIdleTicks();
        return extracted;
    }

    public long returnIdle(long requested) {
        if (requested <= 0L || idleExcess <= 0L) return 0L;
        long returned = storage.extract(Math.min(requested, idleExcess), false);
        if (returned <= 0L) return 0L;
        idleExcess -= returned;
        resetIdleTicks();
        return returned;
    }

    public long insert(long requested) {
        long inserted = storage.insert(requested, false);
        if (inserted <= 0L) return 0L;
        idleExcess = saturatingAdd(idleExcess, inserted);
        resetIdleTicks();
        return inserted;
    }

    public long releaseReservation(long requested) {
        long released = Math.min(Math.max(0L, requested), reserved);
        if (released == 0L) return 0L;
        reserved -= released;
        idleExcess = saturatingAdd(idleExcess, released);
        resetIdleTicks();
        return released;
    }

    public void advanceIdle() {
        if (idleExcess > IDLE_SOFT_LIMIT && idleTicks < IDLE_DELAY_TICKS) idleTicks++;
    }

    public void save(CompoundTag output) {
        output.putLong("amount", amount());
        output.putLong("reserved", reserved);
        output.putLong("idle_excess", idleExcess);
        output.putInt("idle_ticks", idleTicks);
    }

    public void load(CompoundTag input) {
        storage.setAmount(input.getLong("amount"));
        reserved = Math.min(Math.max(0L, input.getLong("reserved")), amount());
        idleExcess = Math.min(Math.max(0L, input.getLong("idle_excess")), available());
        idleTicks = Math.max(0, input.getInt("idle_ticks"));
    }

    public void setAmount(long amount) {
        storage.setAmount(amount);
        reserved = Math.min(reserved, amount());
        idleExcess = Math.min(idleExcess, available());
        if (idleExcess <= IDLE_SOFT_LIMIT) resetIdleTicks();
    }

    private long available() {
        return amount() - reserved;
    }

    private void resetIdleTicks() {
        idleTicks = 0;
    }

    private static long saturatingAdd(long first, long second) {
        return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second;
    }
}
