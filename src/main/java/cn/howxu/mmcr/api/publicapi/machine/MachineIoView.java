package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
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

    public List<ResourceAmount<ItemResource>> itemInputs() {
        Map<ItemResource, Long> amounts = new LinkedHashMap<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
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
            if (capability.storage() instanceof LongValueStorage storage) {
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
            ResourceStorage<?> storage = resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                long amount = storage.amount(slot);
                if (current instanceof ItemResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                long slotCapacity = storage.capacityResource(slot, resource);
                if (!(capability instanceof ItemBusCapability itemBus) || !itemBus.supportsLargeStacks()) {
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
            if (capability.storage() instanceof LongValueStorage storage) {
                capacity = saturatedAdd(capacity, Math.max(0L, storage.capacity() - storage.amount()));
            }
        }
        return capacity;
    }

    public Optional<Float> smartInterfaceValue(String name) {
        if (name == null) return Optional.empty();
        for (MachineCapability capability : snapshot.capabilities()) {
            if (!(capability.storage() instanceof FloatValueStorage storage)) continue;
            Optional<Float> value = storage.value(name);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    public Map<String, Float> smartInterfaceValues() {
        Map<String, Float> values = new LinkedHashMap<>();
        Set<FloatValueStorage> seenStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MachineCapability capability : snapshot.capabilities()) {
            if (!(capability.storage() instanceof FloatValueStorage storage)) continue;
            if (!seenStorages.add(storage)) continue;
            storage.values().forEach(values::putIfAbsent);
        }
        return Map.copyOf(values);
    }

    private List<MachineCapability> capabilities(IOType ioType) {
        return snapshot.capabilities().stream()
                .filter(capability -> capability.view().ioType() == ioType)
                .toList();
    }

    private static ResourceStorage<?> resourceStorage(MachineCapability capability, Class<?> resourceType) {
        return capability.storage() instanceof ResourceStorage<?> storage
                && storage.resourceType().equals(resourceType) ? storage : null;
    }

    private static <R> List<ResourceAmount<R>> resourceAmounts(Map<R, Long> amounts) {
        List<ResourceAmount<R>> result = new ArrayList<>(amounts.size());
        amounts.forEach((resource, amount) -> result.add(new ResourceAmount<>(resource, amount)));
        return List.copyOf(result);
    }

    private static long saturatedAdd(long first, long second) {
        return second > 0L && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
