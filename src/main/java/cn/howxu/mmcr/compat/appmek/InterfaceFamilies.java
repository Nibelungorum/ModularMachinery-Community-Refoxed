package cn.howxu.mmcr.compat.appmek;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import java.util.function.Predicate;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
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
        return AppMekBridge.get().appendBindings(base, directions, external);
    }
    @Override public List<PortFamilyDescriptor> families(List<PortFamilyDescriptor> base) {
        return AppMekBridge.get().appendFamilies(base);
    }
    @Override public boolean acceptsPatternKey(AEKey key) { return AppMekBridge.get().supportsPatternInput(key); }
    @Override public MachineOutput patternOutput(AEKey key, long amount) {
        return AppMekBridge.get().patternOutput(key, amount).orElse(null);
    }
    @Override public List<MachineCapability> requestCapabilities(GenericStackInv inventory, KeyCounter[] holders) {
        var bridge = AppMekBridge.get();
        return (bridge.available() ? bridge.patternRequestFromInventory(inventory) : bridge.patternRequest(holders)).capabilities();
    }
    @Override public long flush(IOPortBlockEntity host, long budget) { return AppMekBridge.get().flush(host, budget); }
    @Override public Predicate<AEKey> inputMatcher(MachineRequirement value) {
        if (!AppMekBridge.get().available() || !(value instanceof LoadedChemicalRequirement chemical) || chemical.ingredient().kind() != ChemicalIngredient.Kind.CHEMICAL) return null;
        return key -> patternOutput(key, 1) instanceof LoadedChemicalOutput output && output.id().equals(chemical.ingredient().id());
    }
    @Override public boolean owns(AEKeyType type) { return AppMekBridge.get().isChemicalKeyType(type); }
    @Override public boolean exposesLocalSlots() { return true; }
}
