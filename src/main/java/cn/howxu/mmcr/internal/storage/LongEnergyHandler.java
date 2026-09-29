package cn.howxu.mmcr.internal.storage;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Extended native energy storage contract with long internal accounting.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface LongEnergyHandler extends IEnergyStorage {
    long getTransferLimit();

    long getAmountAsLong();

    long getCapacityAsLong();

    long insertLong(long amount, boolean simulate);

    long extractLong(long amount, boolean simulate);
}
