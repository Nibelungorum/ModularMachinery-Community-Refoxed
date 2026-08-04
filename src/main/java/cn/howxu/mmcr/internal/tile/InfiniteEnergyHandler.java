package cn.howxu.mmcr.internal.tile;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 调试用无限能量处理器。{@code insert}/{@code extract} 始终按请求量全量成功,
 * 容量视为无限(返回 {@link Long#MAX_VALUE})。无状态,不参与 transaction 撤销。
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InfiniteEnergyHandler implements EnergyHandler {

    public static final InfiniteEnergyHandler INSTANCE = new InfiniteEnergyHandler();

    @Override
    public long getAmountAsLong() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCapacityAsLong() {
        return Long.MAX_VALUE;
    }

    @Override
    public int insert(int amount, TransactionContext tx) {
        return amount;
    }

    @Override
    public int extract(int amount, TransactionContext tx) {
        return amount;
    }
}
