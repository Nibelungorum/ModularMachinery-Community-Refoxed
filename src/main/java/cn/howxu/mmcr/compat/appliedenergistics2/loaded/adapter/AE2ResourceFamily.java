package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.AsyncOutputResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.OutputResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestState;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.NetworkResourceStorage;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Typed AE2 adapter, port descriptors, and storage-view factories for one MMCR resource family.
 *
 * @param <R> MMCR resource type handled by this family
 * @author howxu <dev@howxu.cn>
 */
public final class AE2ResourceFamily<R> {
    private final CapabilityType capabilityType;
    private final AE2KeyAdapter<R> adapter;
    private final PortFamilyDescriptor inputFamily;
    private final PortFamilyDescriptor outputFamily;
    private final Function<GenericStackInv, ResourceStorage<R>> inventoryViewFactory;
    private final BiFunction<MEStorage, List<AEKey>, NetworkResourceStorage<R>> networkViewFactory;
    private final CapabilityViewFactory<R> capabilityViewFactory;

    public AE2ResourceFamily(CapabilityType capabilityType, AE2KeyAdapter<R> adapter,
                             PortFamilyDescriptor inputFamily, PortFamilyDescriptor outputFamily,
                             Function<GenericStackInv, ResourceStorage<R>> inventoryViewFactory,
                             BiFunction<MEStorage, List<AEKey>, NetworkResourceStorage<R>> networkViewFactory,
                             CapabilityViewFactory<R> capabilityViewFactory) {
        this.capabilityType = Objects.requireNonNull(capabilityType, "capabilityType");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.inputFamily = Objects.requireNonNull(inputFamily, "inputFamily");
        this.outputFamily = Objects.requireNonNull(outputFamily, "outputFamily");
        this.inventoryViewFactory = Objects.requireNonNull(inventoryViewFactory, "inventoryViewFactory");
        this.networkViewFactory = Objects.requireNonNull(networkViewFactory, "networkViewFactory");
        this.capabilityViewFactory = Objects.requireNonNull(capabilityViewFactory, "capabilityViewFactory");
    }

    public CapabilityType capabilityType() {
        return capabilityType;
    }

    public AE2KeyAdapter<R> adapter() {
        return adapter;
    }

    public PortFamilyDescriptor inputFamily() {
        return inputFamily;
    }

    public PortFamilyDescriptor outputFamily() {
        return outputFamily;
    }

    public ResourceStorage<R> standardInputView(GenericStackInv inventory) {
        return inventoryViewFactory.apply(inventory);
    }

    public NetworkResourceStorage<R> stockingInputView(MEStorage storage, List<AEKey> keys) {
        return networkViewFactory.apply(storage, keys);
    }

    public OutputResourceStorage<R> outputView(GenericStackInv inventory,
                                                Supplier<MEStorage> networkSupplier,
                                                IActionSource actionSource,
                                                Runnable changeCallback) {
        return new OutputResourceStorage<>(inventory, networkSupplier, adapter, actionSource, changeCallback);
    }

    public AsyncOutputResourceStorage<R> asyncOutputView(Supplier<MEStorage> networkSupplier,
                                                          AsyncOutputService service,
                                                          IActionSource actionSource,
                                                          Runnable wakeCallback) {
        return new AsyncOutputResourceStorage<>(networkSupplier, adapter, service, actionSource, wakeCallback);
    }

    /**
     * Creates a transient network view from the keys currently visible to the pattern provider.
     */
    public ResourceStorage<R> patternInputView(MEStorage storage) {
        var available = storage.getAvailableStacks();
        List<AEKey> keys = available.keySet().stream()
                .filter(key -> adapter.keyType().equals(key.getType()))
                .toList();
        NetworkResourceStorage<R> view = stockingInputView(storage, keys);
        keys.forEach(key -> view.onStackChange(key, available.get(key)));
        return view;
    }

    public PatternRequestResourceStorage<R> patternRequestView(KeyCounter[] inputHolders) {
        return new PatternRequestResourceStorage<>(inputHolders, adapter);
    }

    public PatternRequestResourceStorage<R> patternRequestView(PatternRequestState state) {
        return new PatternRequestResourceStorage<>(state, adapter);
    }

    public PatternReturnResourceStorage<R> patternReturnView(PatternProviderReturnInventory inventory) {
        return new PatternReturnResourceStorage<>(inventory, adapter);
    }

    public PatternReturnResourceStorage<R> patternOutputView(PatternProviderReturnInventory inventory) {
        return patternReturnView(inventory);
    }

    public OutputResourceStorage<R> patternOutputView(PatternProviderReturnInventory inventory,
                                                       Supplier<@Nullable MEStorage> networkSupplier,
                                                       IActionSource actionSource, Runnable changeCallback) {
        return outputView(inventory, networkSupplier, actionSource, changeCallback);
    }

    public CapabilityBinding inputBinding(Function<IOPortBlockEntity, ResourceStorage<R>> storageFactory,
                                           boolean exposeTransferFacet) {
        return binding(IOType.INPUT, CapabilityDirections.input(), storageFactory, exposeTransferFacet, null);
    }

    public CapabilityBinding outputBinding(Function<IOPortBlockEntity, ResourceStorage<R>> storageFactory,
                                            boolean exposeTransferFacet) {
        return binding(IOType.OUTPUT, CapabilityDirections.output(), storageFactory, exposeTransferFacet, null);
    }

    public CapabilityBinding binding(IOType viewDirection, CapabilityDirections declaredDirections,
                                     Function<IOPortBlockEntity, ResourceStorage<R>> storageFactory,
                                     boolean exposeTransferFacet,
                                     CapabilityBinding.ExternalExposure<?> externalExposure) {
        Objects.requireNonNull(viewDirection, "viewDirection");
        Objects.requireNonNull(declaredDirections, "declaredDirections");
        Objects.requireNonNull(storageFactory, "storageFactory");
        return new CapabilityBinding(capabilityType, declaredDirections, context -> {
            if (!(context.host() instanceof IOPortBlockEntity host)) {
                throw new IllegalStateException("AE2 resource family host is not an IO port");
            }
            return capabilityViewFactory.create(host, storageFactory.apply(host), viewDirection, exposeTransferFacet);
        }, (binding, value) -> true, externalExposure);
    }

    /** Builds the MMCR capability view for a storage view of this resource family. */
    @FunctionalInterface
    public interface CapabilityViewFactory<R> {
        MachineCapability create(IOPortBlockEntity host, ResourceStorage<R> storage,
                                 IOType direction, boolean exposeTransferFacet);
    }
}
