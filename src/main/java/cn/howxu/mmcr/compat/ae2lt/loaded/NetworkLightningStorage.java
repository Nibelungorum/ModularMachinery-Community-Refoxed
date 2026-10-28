package cn.howxu.mmcr.compat.ae2lt.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import java.util.List;
import java.util.function.Supplier;

/** Live stocking view: reservations are addressed by network identity and AE key, not filtered indexes.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkLightningStorage implements LightningStorage {
    private final Supplier<MEStorage> storage;
    private final Supplier<List<AEKey>> configuredKeys;
    private final IActionSource source;

    public NetworkLightningStorage(Supplier<MEStorage> storage, Supplier<List<AEKey>> configuredKeys, IActionSource source) {
        this.storage = storage;
        this.configuredKeys = configuredKeys;
        this.source = source;
    }
    private List<AEKey> keys() { return configuredKeys.get().stream().filter(LoadedLightningBridge::isLightning).distinct().toList(); }
    @Override public int size() { return keys().size(); }
    @Override public Object identity() { return storage.get(); }
    @Override public Object address(int slot) { return keys().get(slot); }
    @Override public Object key(LightningTier tier) { return LoadedLightningBridge.key(tier); }
    @Override public Object storedKey(int slot) { return address(slot); }
    @Override public long amount(int slot) { return storage.get().extract(keys().get(slot), Long.MAX_VALUE, Actionable.SIMULATE, source); }
    @Override public long capacity(int slot, LightningTier tier) { return 0L; }
    @Override public long extract(int slot, LightningTier tier, long amount, boolean simulate) {
        return key(tier).equals(address(slot)) ? storage.get().extract(LoadedLightningBridge.key(tier), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, source) : 0L;
    }
    @Override public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) { return 0L; }
    @Override public long insert(int slot, LightningTier tier, long amount, boolean simulate) { return 0L; }
    @Override public long insert(LightningTier tier, long amount, boolean simulate) { return 0L; }
}
