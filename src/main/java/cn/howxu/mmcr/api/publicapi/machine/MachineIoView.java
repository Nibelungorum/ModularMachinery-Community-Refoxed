package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplayRegistry;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only aggregate view over the capabilities in a machine snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineIoView {
    /**
     * A resource and its aggregate amount.
     *
     * @param resource resource identity
     * @param amount aggregate amount
     * @param <R> resource type
     * @author howxu <dev@howxu.cn>
     */
    public record ResourceAmount<R>(R resource, long amount) {
        public ResourceAmount {
            if (resource == null || amount < 0L) throw new IllegalArgumentException("invalid resource amount");
        }
    }

    private final CapabilitySnapshot snapshot;

    public MachineIoView(CapabilitySnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public MachineIoView forTags(Set<String> requiredTags) {
        Objects.requireNonNull(requiredTags, "requiredTags");
        Set<String> tags = Set.copyOf(requiredTags);
        if (tags.isEmpty()) return new MachineIoView(snapshot);
        return new MachineIoView(new CapabilitySnapshot(snapshot.capabilities().stream()
                .filter(capability -> capability.view().tags().containsAll(tags))
                .toList()));
    }

    /**
     * Returns the presentation entries for every capability in this view.
     *
     * @return immutable capability display entries
     */
    public List<CapabilityDisplay> displays() {
        return snapshot.capabilities().stream()
                .flatMap(capability -> capability.view().directions().values().stream()
                        .flatMap(ignored -> CapabilityDisplayRegistry.global().displays(capability).stream()))
                .toList();
    }

    public List<ResourceAmount<ItemResource>> itemInputs() {
        Map<ItemResource, Long> amounts = new LinkedHashMap<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            ItemHandlerFacet nativeFacet = capability.facet(ItemHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.itemHandler() != null) {
                var handler = nativeFacet.itemHandler();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty()) amounts.merge(ItemResource.of(stack), itemAmount(handler, slot), MachineIoView::saturatedAdd);
                }
                continue;
            }
            ResourceStorage<?> storage = resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object value = storage.resource(slot);
                long amount = storage.amount(slot);
                if (!(value instanceof ItemResource resource) || resource.isEmpty() || amount <= 0L) continue;
                amounts.merge(resource, amount, MachineIoView::saturatedAdd);
            }
        }
        return resourceAmounts(amounts);
    }

    public List<ResourceAmount<FluidResource>> fluidInputs() {
        Map<FluidResource, Long> amounts = new LinkedHashMap<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            FluidHandlerFacet nativeFacet = capability.facet(FluidHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.fluidHandler() != null) {
                var handler = nativeFacet.fluidHandler();
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack stack = handler.getFluidInTank(tank);
                    if (!stack.isEmpty()) amounts.merge(FluidResource.of(stack), fluidAmount(handler, tank), MachineIoView::saturatedAdd);
                }
                continue;
            }
            ResourceStorage<?> storage = resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object value = storage.resource(slot);
                long amount = storage.amount(slot);
                if (!(value instanceof FluidResource resource) || resource.isEmpty() || amount <= 0L) continue;
                amounts.merge(resource, amount, MachineIoView::saturatedAdd);
            }
        }
        return resourceAmounts(amounts);
    }

    public long energyInput() {
        long amount = 0L;
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            EnergyStorageFacet nativeFacet = capability.facet(EnergyStorageFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.energyStorage() != null) {
                amount = saturatedAdd(amount, energyAmount(nativeFacet.energyStorage()));
                continue;
            }
            LongValueStorage storage = valueStorage(capability, LongValueStorage.class);
            if (storage != null) {
                amount = saturatedAdd(amount, Math.max(0L, storage.amount()));
            }
        }
        return amount;
    }

    public long itemAmount(Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        long amount = 0L;
        for (ResourceAmount<ItemResource> input : itemInputs()) {
            ItemResource resource = input.resource();
            if (ingredient.test(resource.toStack(Math.min(resource.getMaxStackSize(), Integer.MAX_VALUE)))) {
                amount = saturatedAdd(amount, input.amount());
            }
        }
        return amount;
    }

    public long fluidAmount(FluidIngredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        long amount = 0L;
        for (ResourceAmount<FluidResource> input : fluidInputs()) {
            FluidResource resource = input.resource();
            if (ingredient.test(resource.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE)))) {
                amount = saturatedAdd(amount, input.amount());
            }
        }
        return amount;
    }

    public long itemOutputCapacity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        ItemResource resource = ItemResource.of(stack);
        long capacity = 0L;
        for (MachineCapability capability : capabilities(IOType.OUTPUT)) {
            ItemHandlerFacet nativeFacet = capability.facet(ItemHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.itemHandler() != null) {
                var handler = nativeFacet.itemHandler();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack current = handler.getStackInSlot(slot);
                    if (handler.isItemValid(slot, stack) && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack))) {
                        capacity = saturatedAdd(capacity, Math.max(0L, itemCapacity(handler, slot) - itemAmount(handler, slot)));
                    }
                }
                continue;
            }
            ResourceStorage<?> storage = resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                long amount = storage.amount(slot);
                if (current instanceof ItemResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                long slotCapacity = storage.capacityResource(slot, resource);
                ResourceFacet<?> facet = capability.facet(ResourceFacet.class).orElse(null);
                if (facet == null || !facet.supportsLargeStacks()) {
                    slotCapacity = Math.min(slotCapacity, resource.getMaxStackSize());
                }
                capacity = saturatedAdd(capacity, Math.max(0L, slotCapacity - amount));
            }
        }
        return capacity;
    }

    public long fluidOutputCapacity(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        FluidResource resource = FluidResource.of(stack);
        long capacity = 0L;
        for (MachineCapability capability : capabilities(IOType.OUTPUT)) {
            FluidHandlerFacet nativeFacet = capability.facet(FluidHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.fluidHandler() != null) {
                var handler = nativeFacet.fluidHandler();
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack current = handler.getFluidInTank(tank);
                    if (handler.isFluidValid(tank, stack) && (current.isEmpty() || FluidStack.isSameFluidSameComponents(current, stack))) {
                        capacity = saturatedAdd(capacity, Math.max(0L, fluidCapacity(handler, tank) - fluidAmount(handler, tank)));
                    }
                }
                continue;
            }
            ResourceStorage<?> storage = resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                long amount = storage.amount(slot);
                if (current instanceof FluidResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                long slotCapacity = storage.capacityResource(slot, resource);
                capacity = saturatedAdd(capacity, Math.max(0L, slotCapacity - amount));
            }
        }
        return capacity;
    }

    public long energyOutputCapacity() {
        long capacity = 0L;
        for (MachineCapability capability : capabilities(IOType.OUTPUT)) {
            EnergyStorageFacet nativeFacet = capability.facet(EnergyStorageFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.energyStorage() != null) {
                capacity = saturatedAdd(capacity, Math.max(0L, energyCapacity(nativeFacet.energyStorage()) - energyAmount(nativeFacet.energyStorage())));
                continue;
            }
            LongValueStorage storage = valueStorage(capability, LongValueStorage.class);
            if (storage != null) {
                capacity = saturatedAdd(capacity, Math.max(0L, storage.capacity() - storage.amount()));
            }
        }
        return capacity;
    }

    public Optional<Float> smartInterfaceValue(String name) {
        if (name == null) return Optional.empty();
        for (MachineCapability capability : snapshot.capabilities()) {
            FloatValueStorage storage = valueStorage(capability, FloatValueStorage.class);
            if (storage == null) continue;
            Optional<Float> value = storage.value(name);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    public Map<String, Float> smartInterfaceValues() {
        Map<String, Float> values = new LinkedHashMap<>();
        Set<FloatValueStorage> seenStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineCapability capability : snapshot.capabilities()) {
            FloatValueStorage storage = valueStorage(capability, FloatValueStorage.class);
            if (storage == null) continue;
            if (!seenStorages.add(storage)) continue;
            storage.values().forEach(values::putIfAbsent);
        }
        return Map.copyOf(values);
    }

    private List<MachineCapability> capabilities(IOType ioType) {
        return snapshot.capabilities().stream()
                .filter(capability -> capability.view().directions().supports(ioType))
                .toList();
    }

    private static ResourceStorage<?> resourceStorage(MachineCapability capability, Class<?> resourceType) {
        ResourceFacet<?> facet = capability.facet(ResourceFacet.class).orElse(null);
        if (facet != null) return facet.resourceType().equals(resourceType) ? facet.storage() : null;
        ValueFacet<?> valueFacet = capability.facet(ValueFacet.class).orElse(null);
        return valueFacet != null && valueFacet.storage() instanceof ResourceStorage<?> storage
                && storage.resourceType().equals(resourceType) ? storage : null;
    }

    @SuppressWarnings("unchecked")
    private static <S extends CapabilityStorage> S valueStorage(
            MachineCapability capability, Class<S> storageType) {
        ValueFacet<?> facet = capability == null ? null : capability.facet(ValueFacet.class).orElse(null);
        return facet != null && storageType.isInstance(facet.storage())
                ? (S) facet.storage() : null;
    }

    private static <R> List<ResourceAmount<R>> resourceAmounts(Map<R, Long> amounts) {
        List<ResourceAmount<R>> result = new ArrayList<>(amounts.size());
        amounts.forEach((resource, amount) -> result.add(new ResourceAmount<>(resource, amount)));
        return List.copyOf(result);
    }

    private static long saturatedAdd(long first, long second) {
        return second > 0L && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static long itemAmount(net.neoforged.neoforge.items.IItemHandler handler, int slot) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage
                ? storage.amount(slot) : handler.getStackInSlot(slot).getCount();
    }

    private static long itemCapacity(net.neoforged.neoforge.items.IItemHandler handler, int slot) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage
                ? storage.capacity(slot) : handler.getSlotLimit(slot);
    }

    private static long fluidAmount(net.neoforged.neoforge.fluids.capability.IFluidHandler handler, int tank) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.amount(tank) : handler.getFluidInTank(tank).getAmount();
    }

    private static long fluidCapacity(net.neoforged.neoforge.fluids.capability.IFluidHandler handler, int tank) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.capacity(tank) : handler.getTankCapacity(tank);
    }

    private static long energyAmount(net.neoforged.neoforge.energy.IEnergyStorage storage) {
        return storage instanceof cn.howxu.mmcr.internal.storage.LongEnergyHandler longStorage
                ? longStorage.getAmountAsLong() : storage.getEnergyStored();
    }

    private static long energyCapacity(net.neoforged.neoforge.energy.IEnergyStorage storage) {
        return storage instanceof cn.howxu.mmcr.internal.storage.LongEnergyHandler longStorage
                ? longStorage.getCapacityAsLong() : storage.getMaxEnergyStored();
    }
}
