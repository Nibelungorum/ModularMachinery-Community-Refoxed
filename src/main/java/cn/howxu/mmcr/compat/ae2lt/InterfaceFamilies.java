package cn.howxu.mmcr.compat.ae2lt;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import java.util.function.Predicate;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.InterfaceFamilyContributor;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import java.util.List;

/** Contributes this integration's interface capabilities during AE family discovery.
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceFamilies implements InterfaceFamilyContributor {
    @Override public List<CapabilityBinding> bindings(List<CapabilityBinding> base, CapabilityDirections directions, boolean external) {
        return LightningBridge.get().appendBindings(base, directions);
    }
    @Override public List<PortFamilyDescriptor> families(List<PortFamilyDescriptor> base) {
        return LightningBridge.get().appendFamilies(base);
    }
    @Override public boolean acceptsPatternKey(AEKey key) { return LightningBridge.get().supportsPatternInput(key); }
    @Override public MachineOutput patternOutput(AEKey key, long amount) {
        return LightningBridge.get().patternOutput(key, amount).orElse(null);
    }
    @Override public List<MachineCapability> requestCapabilities(GenericStackInv inventory, KeyCounter[] holders) {
        return LightningBridge.get().patternInputs(inventory);
    }
    @Override public long flush(IOPortBlockEntity host, long budget) { return LightningBridge.get().flush(host, budget); }
    @Override public Predicate<AEKey> inputMatcher(MachineRequirement value) {
        if (!(value instanceof LightningRequirement lightning)) return null;
        return key -> patternOutput(key, 1) instanceof LightningOutput output && output.tier() == lightning.tier();
    }
    @Override public long patternLimit() { return Integer.MAX_VALUE; }
    @Override public boolean owns(AEKeyType type) { return LightningBridge.get().ownsKeyType(type); }
}
