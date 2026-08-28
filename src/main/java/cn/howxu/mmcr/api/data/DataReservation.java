package cn.howxu.mmcr.api.data;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Future transactional reservation boundary for lazy data repositories.
 *
 * <p>Implementations decide how a reservation is committed or cancelled;
 * this module intentionally provides no concrete repository implementation.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public interface DataReservation {
    boolean commit(TransactionContext transaction);

    void cancel();
}
