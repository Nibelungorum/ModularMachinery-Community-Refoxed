package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable context for one automatic capability transfer attempt.
 *
 * @param capability capability being transferred
 * @param ioType direction of the capability operation
 * @param side adjacent side used by the operation
 * @param parallelism requested transfer parallelism
 * @param simulate whether the operation must leave storage unchanged
 * @param transaction transaction supplied for a committed operation
 * @param eject whether the operation moves contents out of the local port
 * @author howxu <dev@howxu.cn>
 */
public record TransferContext(MachineCapability capability, IOType ioType, Direction side, long parallelism,
                              boolean simulate, @Nullable TransactionContext transaction, boolean eject) {
    public TransferContext {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(ioType, "ioType");
        Objects.requireNonNull(side, "side");
        if (capability.type() == null || !ioType.equals(capability.ioType())) {
            throw new IllegalArgumentException("context direction must match capability");
        }
        if (parallelism <= 0L) throw new IllegalArgumentException("parallelism must be positive");
        if (!simulate && transaction == null) {
            throw new IllegalArgumentException("committed transfer requires a transaction");
        }
    }

    public TransferContext(MachineCapability capability, IOType ioType, Direction side, long parallelism,
                           boolean simulate, @Nullable TransactionContext transaction) {
        this(capability, ioType, side, parallelism, simulate, transaction, false);
    }

    public static TransferContext simulate(MachineCapability capability, Direction side, long parallelism) {
        Objects.requireNonNull(capability, "capability");
        return simulate(capability, capability.ioType(), side, parallelism);
    }

    public static TransferContext simulate(MachineCapability capability, IOType ioType, Direction side,
                                           long parallelism) {
        return new TransferContext(capability, ioType, side, parallelism, true, null, false);
    }

    public static TransferContext commit(MachineCapability capability, Direction side, long parallelism,
                                         TransactionContext transaction) {
        Objects.requireNonNull(capability, "capability");
        return commit(capability, capability.ioType(), side, parallelism, transaction);
    }

    public static TransferContext commit(MachineCapability capability, IOType ioType, Direction side,
                                         long parallelism, TransactionContext transaction) {
        return new TransferContext(capability, ioType, side, parallelism, false,
                Objects.requireNonNull(transaction, "transaction"), false);
    }

    public TransferContext asEjection() {
        return new TransferContext(capability, ioType, side, parallelism, simulate, transaction, true);
    }
}
