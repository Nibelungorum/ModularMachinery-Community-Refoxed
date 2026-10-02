package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplayRegistry;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalViewFacet;
import cn.howxu.mmcr.api.compat.mekanism.HeatViewFacet;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
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

    /**
     * State of one heat capability in snapshot order.
     *
     * @param heat stored heat
     * @param temperature current temperature in kelvin
     * @param heatCapacity heat capacity, not a maximum storage amount
     * @author howxu <dev@howxu.cn>
     */
    public record HeatState(double heat, double temperature, double heatCapacity) {
        public HeatState {
            if (!Double.isFinite(heat) || heat < 0D || !Double.isFinite(temperature) || temperature < 0D
                    || !Double.isFinite(heatCapacity) || heatCapacity < 0D) {
                throw new IllegalArgumentException("invalid heat state");
            }
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

    public List<ResourceAmount<ItemStack>> itemInputs() {
        List<ResourceAmount<ItemStack>> amounts = new ArrayList<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            ItemHandlerFacet nativeFacet = capability.facet(ItemHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.itemHandler() != null) {
                var handler = nativeFacet.itemHandler();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = itemResource(handler, slot);
                    long amount = itemAmount(handler, slot);
                    if (!stack.isEmpty() && amount > 0L) {
                        mergeItem(amounts, stack, amount);
                    }
                }
                continue;
            }
        }
        return List.copyOf(amounts);
    }

    public List<ResourceAmount<FluidStack>> fluidInputs() {
        List<ResourceAmount<FluidStack>> amounts = new ArrayList<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            FluidHandlerFacet nativeFacet = capability.facet(FluidHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.fluidHandler() != null) {
                var handler = nativeFacet.fluidHandler();
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack stack = fluidResource(handler, tank);
                    long amount = fluidAmount(handler, tank);
                    if (!stack.isEmpty() && amount > 0L) {
                        mergeFluid(amounts, stack, amount);
                    }
                }
                continue;
            }
        }
        return List.copyOf(amounts);
    }

    public List<ResourceAmount<ResourceLocation>> chemicalInputs() {
        Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();
        Map<Object, Set<ResourceLocation>> counted = new IdentityHashMap<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            ChemicalViewFacet facet = capability.facet(ChemicalViewFacet.class).orElse(null);
            if (facet == null) continue;
            Set<ResourceLocation> seen = counted.computeIfAbsent(facet.queryIdentity(), ignored -> new HashSet<>());
            for (ChemicalViewFacet.ChemicalAmount entry : facet.contents()) {
                if (entry.amount() > 0L && seen.add(entry.id())) {
                    amounts.merge(entry.id(), entry.amount(), MachineIoView::saturatedAdd);
                }
            }
        }
        return resourceAmounts(amounts);
    }

    public long chemicalAmount(ResourceLocation chemicalId) {
        Objects.requireNonNull(chemicalId, "chemicalId");
        long amount = 0L;
        for (ResourceAmount<ResourceLocation> input : chemicalInputs()) {
            if (chemicalId.equals(input.resource())) amount = saturatedAdd(amount, input.amount());
        }
        return amount;
    }

    public long chemicalTagAmount(ResourceLocation tagId) {
        Objects.requireNonNull(tagId, "tagId");
        long amount = 0L;
        Map<Object, Set<ResourceLocation>> counted = new IdentityHashMap<>();
        for (MachineCapability capability : capabilities(IOType.INPUT)) {
            ChemicalViewFacet facet = capability.facet(ChemicalViewFacet.class).orElse(null);
            if (facet == null) continue;
            Set<ResourceLocation> seen = counted.computeIfAbsent(facet.queryIdentity(), ignored -> new HashSet<>());
            for (ChemicalViewFacet.ChemicalAmount entry : facet.tagContents(tagId)) {
                if (entry.amount() > 0L && seen.add(entry.id())) amount = saturatedAdd(amount, entry.amount());
            }
        }
        return amount;
    }

    public long chemicalOutputCapacity(ResourceLocation chemicalId) {
        Objects.requireNonNull(chemicalId, "chemicalId");
        long capacity = 0L;
        for (MachineCapability capability : capabilities(IOType.OUTPUT)) {
            ChemicalViewFacet facet = capability.facet(ChemicalViewFacet.class).orElse(null);
            if (facet != null) capacity = saturatedAdd(capacity, Math.max(0L, facet.outputCapacity(chemicalId)));
        }
        return capacity;
    }

    public List<HeatState> heatInputs() {
        return heatStates(IOType.INPUT);
    }

    public List<HeatState> heatOutputs() {
        return heatStates(IOType.OUTPUT);
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
        for (ResourceAmount<ItemStack> input : itemInputs()) {
            ItemStack resource = input.resource();
            if (ingredient.test(resource)) {
                amount = saturatedAdd(amount, input.amount());
            }
        }
        return amount;
    }

    public long fluidAmount(FluidIngredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        long amount = 0L;
        for (ResourceAmount<FluidStack> input : fluidInputs()) {
            FluidStack resource = input.resource();
            if (ingredient.test(resource.copyWithAmount((int) Math.min(input.amount(), Integer.MAX_VALUE)))) {
                amount = saturatedAdd(amount, input.amount());
            }
        }
        return amount;
    }

    public long itemOutputCapacity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        long capacity = 0L;
        for (MachineCapability capability : capabilities(IOType.OUTPUT)) {
            ItemHandlerFacet nativeFacet = capability.facet(ItemHandlerFacet.class).orElse(null);
            if (nativeFacet != null && nativeFacet.itemHandler() != null) {
                var handler = nativeFacet.itemHandler();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack current = itemResource(handler, slot);
                    if (handler.isItemValid(slot, stack) && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack))) {
                        long slotCapacity = itemCapacity(handler, slot);
                        if (!nativeFacet.supportsLargeStacks()) {
                            slotCapacity = Math.min(slotCapacity, stack.getMaxStackSize());
                        }
                        capacity = saturatedAdd(capacity, Math.max(0L, slotCapacity - itemAmount(handler, slot)));
                    }
                }
                continue;
            }
        }
        return capacity;
    }

    public long fluidOutputCapacity(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
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

    @SuppressWarnings("unchecked")
    private static <S extends CapabilityStorage> S valueStorage(
            MachineCapability capability, Class<S> storageType) {
        cn.howxu.mmcr.api.capability.facet.ValueFacet<?> facet = capability == null ? null : capability.facet(cn.howxu.mmcr.api.capability.facet.ValueFacet.class).orElse(null);
        return facet != null && storageType.isInstance(facet.storage())
                ? (S) facet.storage() : null;
    }

    private static <R> List<ResourceAmount<R>> resourceAmounts(Map<R, Long> amounts) {
        List<ResourceAmount<R>> result = new ArrayList<>(amounts.size());
        amounts.forEach((resource, amount) -> result.add(new ResourceAmount<>(resource, amount)));
        return List.copyOf(result);
    }

    private static void mergeItem(List<ResourceAmount<ItemStack>> amounts, ItemStack stack, long amount) {
        for (int index = 0; index < amounts.size(); index++) {
            ResourceAmount<ItemStack> existing = amounts.get(index);
            if (ItemStack.isSameItemSameComponents(existing.resource(), stack)) {
                amounts.set(index, new ResourceAmount<>(existing.resource(),
                        saturatedAdd(existing.amount(), amount)));
                return;
            }
        }
        amounts.add(new ResourceAmount<>(stack.copyWithCount(1), amount));
    }

    private static void mergeFluid(List<ResourceAmount<FluidStack>> amounts, FluidStack stack, long amount) {
        for (int index = 0; index < amounts.size(); index++) {
            ResourceAmount<FluidStack> existing = amounts.get(index);
            if (FluidStack.isSameFluidSameComponents(existing.resource(), stack)) {
                amounts.set(index, new ResourceAmount<>(existing.resource(),
                        saturatedAdd(existing.amount(), amount)));
                return;
            }
        }
        amounts.add(new ResourceAmount<>(stack.copyWithAmount(1), amount));
    }

    private List<HeatState> heatStates(IOType ioType) {
        List<HeatState> states = new ArrayList<>();
        for (MachineCapability capability : capabilities(ioType)) {
            HeatViewFacet facet = capability.facet(HeatViewFacet.class).orElse(null);
            if (facet != null) states.add(new HeatState(facet.heat(), facet.temperature(), facet.heatCapacity()));
        }
        return List.copyOf(states);
    }

    private static long saturatedAdd(long first, long second) {
        return second > 0L && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static long itemAmount(net.neoforged.neoforge.items.IItemHandler handler, int slot) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage
                ? storage.amount(slot) : handler instanceof NativeStackSync.Item sync ? sync.amount(slot)
                : handler.getStackInSlot(slot).getCount();
    }

    private static ItemStack itemResource(net.neoforged.neoforge.items.IItemHandler handler, int slot) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage
                ? storage.resource(slot) : handler.getStackInSlot(slot);
    }

    private static long itemCapacity(net.neoforged.neoforge.items.IItemHandler handler, int slot) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage
                ? storage.capacity(slot) : handler instanceof NativeStackSync.Item sync ? sync.capacity(slot)
                : handler.getSlotLimit(slot);
    }

    private static long fluidAmount(net.neoforged.neoforge.fluids.capability.IFluidHandler handler, int tank) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.amount(tank) : handler instanceof NativeStackSync.Fluid sync ? sync.amount(tank)
                : handler.getFluidInTank(tank).getAmount();
    }

    private static FluidStack fluidResource(net.neoforged.neoforge.fluids.capability.IFluidHandler handler, int tank) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.resource(tank) : handler.getFluidInTank(tank);
    }

    private static long fluidCapacity(net.neoforged.neoforge.fluids.capability.IFluidHandler handler, int tank) {
        return handler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                ? storage.capacity(tank) : handler instanceof NativeStackSync.Fluid sync ? sync.capacity(tank)
                : handler.getTankCapacity(tank);
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
