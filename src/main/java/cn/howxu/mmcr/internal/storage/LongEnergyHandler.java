package cn.howxu.mmcr.internal.storage;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Extended energy handler contract for long-sized transfers within MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface LongEnergyHandler extends EnergyHandler {
    long getTransferLimit();

    long insertLong(long amount, TransactionContext transaction);

    long extractLong(long amount, TransactionContext transaction);
}
