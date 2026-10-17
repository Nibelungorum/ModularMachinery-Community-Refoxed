package cn.howxu.mmcr.compat.ae2lt.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;

/** Network-first ME output with bounded local remainders, shared network/key reservations.
 * @author howxu <dev@howxu.cn>
 */
public final class OutputLightningStorage extends InventoryLightningStorage {
    private final Supplier<@Nullable MEStorage> network;
    private final IActionSource source;
    private final Runnable changed;
    private final boolean localBuffer;

    public OutputLightningStorage(GenericStackInv inventory, Supplier<@Nullable MEStorage> network,
                                  IActionSource source, Runnable changed, boolean localBuffer) {
        super(inventory);
        this.network = network;
        this.source = source;
        this.changed = changed;
        this.localBuffer = localBuffer;
    }
    @Override public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) {
        MEStorage storage = network.get();
        long accepted = 0L;
        if (storage != null) {
            long reserved = Long.MAX_VALUE - reservations.outputAvailable(storage, key(tier), Long.MAX_VALUE);
            long probe = RequirementHandlerSupport.saturatingAdd(reserved, amount);
            long simulated = Math.clamp(storage.insert(key(tier), probe, Actionable.SIMULATE, source), 0L, probe);
            accepted = Math.min(amount, reservations.outputAvailable(storage, key(tier), simulated));
            if (accepted > 0L && !reservations.reserveOutput(storage, key(tier), accepted)) accepted = 0L;
        }
        return accepted + (localBuffer ? super.reserveOutput(tier, amount - accepted, reservations) : 0L);
    }
    @Override public long insert(LightningTier tier, long amount, boolean simulate) {
        MEStorage storage = network.get();
        long accepted = storage == null ? 0L : Math.clamp(storage.insert(key(tier), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, source), 0L, amount);
        if (localBuffer) accepted += super.insert(tier, amount - accepted, simulate);
        if (!simulate && accepted > 0L) changed.run();
        return accepted;
    }
}
