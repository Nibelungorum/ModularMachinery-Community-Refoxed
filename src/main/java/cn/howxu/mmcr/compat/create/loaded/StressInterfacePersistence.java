package cn.howxu.mmcr.compat.create.loaded;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.nbt.CompoundTag;

/** One-time migration of recipe contributions out of Create's saved unloaded ledger.
 * @author howxu <dev@howxu.cn>
 */
public final class StressInterfacePersistence {
    private CompoundTag saved;
    private boolean settling;

    public void read(CompoundTag tag) {
        saved = tag.contains("Network") ? tag.copy() : null;
    }

    public boolean pending() { return saved != null; }
    public boolean settling() { return settling; }
    public Long networkId() { return saved == null ? null : saved.getCompound("Network").getLong("Id"); }
    public float oldRpm() { return saved == null ? 0F : saved.getFloat("Speed"); }
    public float oldGeneratedRpm() {
        return saved == null ? 0F : saved.getCompound("StressRecovery").getFloat("GeneratedRpm");
    }

    public void write(CompoundTag tag, float generatedRpm) {
        if (saved != null) {
            // A save before first initialization must retain the unsettled debit, even
            // if packet/tooltip calculations have already reset the native last-base fields.
            tag.put("Network", saved.getCompound("Network").copy());
            tag.putFloat("Speed", saved.getFloat("Speed"));
            if (saved.contains("Source")) tag.put("Source", saved.get("Source").copy());
            else tag.remove("Source");
            generatedRpm = oldGeneratedRpm();
        }
        if (tag.contains("Network")) {
            CompoundTag recovery = new CompoundTag();
            recovery.putFloat("GeneratedRpm", generatedRpm);
            tag.put("StressRecovery", recovery);
        } else tag.remove("StressRecovery");
        tag.remove("GeneratedRpm");
    }

    /** Uses native addSilently to debit old bases/RPM and exactly one unloaded member.
     * The caller exposes old RPM and zero new bases only while settling() is true.
     */
    public boolean settle(KineticNetwork network, KineticBlockEntity entity) {
        if (saved == null) return false;
        CompoundTag ledger = saved.getCompound("Network");
        try {
            if (network.members.containsKey(entity)) return false;
            if (!network.initialized) {
                network.initFromTE(ledger.getFloat("Capacity"), ledger.getFloat("Stress"), ledger.getInt("Size"));
            }
            settling = true;
            network.addSilently(entity, ledger.getFloat("AddedCapacity"), ledger.getFloat("AddedStress"));
            return true;
        } finally {
            settling = false;
            saved = null;
        }
    }
}
