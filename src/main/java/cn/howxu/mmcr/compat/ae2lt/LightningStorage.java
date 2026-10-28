package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;

/** Native-independent ME lightning view using the branch's shared physical reservations.
 * @author howxu <dev@howxu.cn>
 */
public interface LightningStorage {
    int size();
    Object identity();
    Object address(int slot);
    Object key(LightningTier tier);
    Object storedKey(int slot);
    long amount(int slot);
    long capacity(int slot, LightningTier tier);
    long extract(int slot, LightningTier tier, long amount, boolean simulate);
    long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations);
    long insert(int slot, LightningTier tier, long amount, boolean simulate);
    long insert(LightningTier tier, long amount, boolean simulate);
}
