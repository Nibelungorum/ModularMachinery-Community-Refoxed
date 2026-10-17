package cn.howxu.mmcr.compat.ae2lt.loaded;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.ae2lt.LightningViewFacet;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** AE2LT-present additions to every AE2/ExtendedAE ME interface profile.
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedLightningBridge implements LightningBridge {
    private static final CapabilityType TYPE = new CapabilityType(LightningRecipeTypes.LIGHTNING);

    public static LightningKey key(LightningTier tier) {
        return tier == LightningTier.HIGH_VOLTAGE ? LightningKey.HIGH_VOLTAGE : LightningKey.EXTREME_HIGH_VOLTAGE;
    }
    public static boolean isLightning(Object key) {
        return LightningKey.HIGH_VOLTAGE.equals(key) || LightningKey.EXTREME_HIGH_VOLTAGE.equals(key);
    }
    @Override public boolean available() { return true; }
    @Override public void registerCapabilityDefinitions() {
        CapabilityRegistry.register(new CapabilityDefinition(TYPE, Set.of(LightningViewFacet.class, OperationFacet.class), context -> {
            if (!(context.host() instanceof IOPortBlockEntity host)) throw new IllegalArgumentException("Lightning requires an ME interface host");
            return new LightningCapability(host, storage(host), context.ioType());
        }));
    }
    @Override public List<CapabilityBinding> appendBindings(List<CapabilityBinding> base, CapabilityDirections directions) {
        List<CapabilityBinding> bindings = new ArrayList<>(base);
        bindings.add(CapabilityBinding.internalOnly(TYPE, directions, context -> {
            IOPortBlockEntity host = (IOPortBlockEntity) context.host();
            IOType direction = host instanceof PatternInterfaceBlockEntity || directions.supports(IOType.OUTPUT) ? IOType.OUTPUT : IOType.INPUT;
            return new LightningCapability(host, storage(host), direction);
        }, (binding, tier) -> true));
        return List.copyOf(bindings);
    }
    @Override public List<PortFamilyDescriptor> appendFamilies(List<PortFamilyDescriptor> base) {
        List<PortFamilyDescriptor> families = new ArrayList<>(base);
        for (IOType direction : IOType.values()) if (base.stream().anyMatch(family -> family.ioType() == direction)) {
            families.add(new PortFamilyDescriptor(LightningRecipeTypes.LIGHTNING, direction, 0, List.of()));
        }
        return List.copyOf(families);
    }
    public static LightningStorage storage(IOPortBlockEntity host) {
        IActionSource source = IActionSource.ofMachine((IActionHost) host);
        if (host instanceof StockingInterfaceBlockEntity stocking) return new NetworkLightningStorage(stocking::networkStorage, stocking::configuredKeys, source);
        if (host instanceof InputInterfaceBlockEntity input) return new InventoryLightningStorage(input.getStorage());
        if (host instanceof OutputInterfaceBlockEntity output) return new OutputLightningStorage(output.getStorage(), output::networkStorage, source, output::onStorageChanged, true);
        if (host instanceof AsyncOutputInterfaceBlockEntity output) return new OutputLightningStorage(output.getStorage(), output::networkStorage, source, output::saveChanges, false);
        if (host instanceof PatternInterfaceBlockEntity pattern) return new InventoryLightningStorage(pattern.getLogic().getReturnInv());
        throw new IllegalArgumentException("Lightning requires an ME interface host");
    }
    @Override public boolean supportsPatternInput(Object key) { return isLightning(key); }
    @Override public Optional<MachineOutput> patternOutput(Object key, long amount) {
        return amount > 0L && amount <= Integer.MAX_VALUE && isLightning(key)
                ? Optional.of(new LightningOutput(LightningKey.HIGH_VOLTAGE.equals(key) ? LightningTier.HIGH_VOLTAGE
                        : LightningTier.EXTREME_HIGH_VOLTAGE, amount, 1F)) : Optional.empty();
    }
    @Override public List<MachineCapability> patternInputs(Object inventory) {
        return List.of(new LightningCapability(new InventoryLightningStorage((GenericStackInv) inventory), IOType.INPUT));
    }
    @Override public long flush(IOPortBlockEntity host, long budget) {
        if (!(host instanceof OutputInterfaceBlockEntity output)) return 0L;
        return AE2NativeAdapters.flush(output.getStorage(), output::networkStorage, IActionSource.ofMachine(output), LightningKeyType.INSTANCE, budget);
    }
}
