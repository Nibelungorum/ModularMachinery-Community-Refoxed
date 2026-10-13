package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Native NeoForge handler views over AE2's key-based inventories and storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2NativeAdapters {
    private AE2NativeAdapters() {
    }

    public static IItemHandler items(GenericStackInv inventory) {
        return new InventoryItems(inventory);
    }

    public static IFluidHandler fluids(GenericStackInv inventory) {
        return new InventoryFluids(inventory);
    }

    public static IItemHandler networkItems(Supplier<MEStorage> storage, Supplier<List<AEKey>> keys,
                                            IActionSource source) {
        return new NetworkItems(storage, keys, source);
    }

    public static IFluidHandler networkFluids(Supplier<MEStorage> storage, Supplier<List<AEKey>> keys,
                                              IActionSource source) {
        return new NetworkFluids(storage, keys, source);
    }

    public static IItemHandler outputItems(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage,
                                           IActionSource source, Runnable changed) {
        return new OutputItems(inventory, storage, source, changed);
    }

    public static IFluidHandler outputFluids(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage,
                                             IActionSource source, Runnable changed) {
        return new OutputFluids(inventory, storage, source, changed);
    }

    public static GenericStackInv requestInventory(KeyCounter[] holders, AEKeyType type) {
        return createRequestInventory(holders, type);
    }

    /** One physical material inventory for an entire accepted batch, including foreign AE key types. */
    public static GenericStackInv requestInventory(KeyCounter[] holders) {
        return createRequestInventory(holders, null);
    }

    /** A proposal's input quota backed by the batch inventory, not another copy of its materials. */
    public static GenericStackInv requestInventoryView(GenericStackInv inventory, long parallelism, long batchSize) {
        return new RequestInventoryView(inventory, parallelism, batchSize);
    }

    /** Restores owned return materials without imposing normal item stack-size limits. */
    public static GenericStackInv restoreRequestInventory(ListTag saved, HolderLookup.Provider registries) {
        GenericStackInv inventory = new UnboundedRequestInventory(saved.size());
        inventory.readFromTag(saved, registries);
        return inventory;
    }

    private static GenericStackInv createRequestInventory(KeyCounter[] holders, @Nullable AEKeyType type) {
        List<GenericStack> stacks = new ArrayList<>();
        for (KeyCounter holder : holders) {
            for (var entry : holder) {
                AEKey key = entry.getKey();
                if ((type == null || type.equals(key.getType())) && entry.getLongValue() > 0L) {
                    stacks.add(new GenericStack(key, entry.getLongValue()));
                }
            }
        }
        GenericStackInv inventory = new UnboundedRequestInventory(stacks.size());
        if (type != null) inventory.setCapacity(type, Long.MAX_VALUE);
        inventory.beginBatch();
        try {
            for (int slot = 0; slot < stacks.size(); slot++) inventory.setStack(slot, stacks.get(slot));
        } finally {
            inventory.endBatchSuppressed();
        }
        return inventory;
    }

    public static boolean returnRemaining(GenericStackInv request, GenericStackInv returns, AEKeyType type,
                                          IActionSource source) {
        if (!canReturn(request, returns, type, source)) return false;
        for (int slot = 0; slot < request.size(); slot++) {
            GenericStack stack = request.getStack(slot);
            if (stack == null || stack.amount() <= 0L || !type.equals(stack.what().getType())) continue;
            long accepted = returns.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (accepted != stack.amount()) return false;
        }
        return true;
    }

    /** Creates an isolated slot-accurate view for cumulative return-inventory simulation. */
    public static GenericStackInv copyForSimulation(GenericStackInv inventory) {
        GenericStackInv copy;
        if (inventory instanceof PatternReturnInventory) {
            copy = new PatternReturnInventory(() -> {});
            for (AEKeyType type : AEKeyTypes.getAll()) copy.setCapacity(type, inventory.getCapacity(type));
        } else {
            copy = new SimulatedInventory(inventory);
        }
        copy.beginBatch();
        try {
            for (int slot = 0; slot < inventory.size(); slot++) copy.setStack(slot, inventory.getStack(slot));
        } finally {
            copy.endBatchSuppressed();
        }
        return copy;
    }

    public static boolean canReturn(GenericStackInv request, GenericStackInv returns, AEKeyType type,
                                    IActionSource source) {
        for (int slot = 0; slot < request.size(); slot++) {
            GenericStack stack = request.getStack(slot);
            if (stack == null || stack.amount() <= 0L || !type.equals(stack.what().getType())) continue;
            long accepted = returns.insert(stack.what(), stack.amount(), Actionable.SIMULATE, source);
            if (accepted != stack.amount()) return false;
        }
        return true;
    }

    public static long flush(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage, IActionSource source,
                             AEKeyType type, long limit) {
        MEStorage network = storage.get();
        if (network == null || limit <= 0L) return 0L;
        long moved = 0L;
        inventory.beginBatch();
        try {
            for (int slot = 0; slot < inventory.size() && moved < limit; slot++) {
                GenericStack stack = inventory.getStack(slot);
                if (stack == null || stack.amount() <= 0L || !type.equals(stack.what().getType())) continue;
                long requested = Math.min(stack.amount(), limit - moved);
                long simulated = network.insert(stack.what(), requested, Actionable.SIMULATE, source);
                long accepted = Math.min(requested, Math.max(0L, simulated));
                if (accepted == 0L) continue;
                long committed = network.insert(stack.what(), accepted, Actionable.MODULATE, source);
                committed = Math.min(accepted, Math.max(0L, committed));
                if (committed == 0L) continue;
                inventory.extract(slot, stack.what(), committed, Actionable.MODULATE);
                moved += committed;
            }
        } finally {
            inventory.endBatch();
        }
        return moved;
    }

    private static Actionable mode(boolean simulate) {
        return simulate ? Actionable.SIMULATE : Actionable.MODULATE;
    }

    private static int amount(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    /**
     * Keeps prepared lane allocations separate while every debit updates the sole material owner.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class RequestInventoryView extends GenericStackInv {
        private final GenericStackInv inventory;
        private final long[] remaining;

        private RequestInventoryView(GenericStackInv inventory, long parallelism, long batchSize) {
            super(null, Mode.STORAGE, inventory.size());
            this.inventory = inventory;
            remaining = new long[inventory.size()];
            for (int slot = 0; slot < inventory.size(); slot++) {
                var scaled = BigInteger.valueOf(inventory.getAmount(slot))
                        .multiply(BigInteger.valueOf(parallelism))
                        .divideAndRemainder(BigInteger.valueOf(batchSize));
                if (scaled[1].signum() != 0) throw new IllegalArgumentException("Pattern inputs cannot be split exactly");
                remaining[slot] = scaled[0].longValueExact();
            }
        }

        @Override public GenericStack getStack(int slot) {
            GenericStack stack = inventory.getStack(slot);
            long amount = stack == null ? 0L : Math.min(stack.amount(), remaining[slot]);
            return amount > 0L ? new GenericStack(stack.what(), amount) : null;
        }

        @Override public AEKey getKey(int slot) {
            GenericStack stack = getStack(slot);
            return stack == null ? null : stack.what();
        }

        @Override public long getAmount(int slot) {
            GenericStack stack = getStack(slot);
            return stack == null ? 0L : stack.amount();
        }

        @Override public long getMaxAmount(AEKey key) { return inventory.getMaxAmount(key); }
        @Override public boolean canInsert() { return false; }

        @Override public long extract(int slot, AEKey key, long amount, Actionable mode) {
            long extracted = inventory.extract(slot, key, Math.min(amount, remaining[slot]), mode);
            if (mode == Actionable.MODULATE) remaining[slot] -= extracted;
            return extracted;
        }
    }

    private static final class UnboundedRequestInventory extends GenericStackInv {
        private UnboundedRequestInventory(int size) {
            super(null, Mode.STORAGE, size);
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return Long.MAX_VALUE;
        }
    }

    private static final class SimulatedInventory extends GenericStackInv {
        private final GenericStackInv source;

        private SimulatedInventory(GenericStackInv source) {
            super(null, source.getMode(), source.size());
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override public boolean isSupportedType(AEKeyType type) { return source.isSupportedType(type); }
        @Override public boolean isAllowedIn(int slot, AEKey key) { return source.isAllowedIn(slot, key); }
        @Override public long getMaxAmount(AEKey key) { return source.getMaxAmount(key); }
        @Override public boolean canInsert() { return source.canInsert(); }
    }

    private static final class InventoryItems implements IItemHandler, NativeStackSync.Item, NativeReservationAccess {
        private final GenericStackInv inventory;

        private InventoryItems(GenericStackInv inventory) {
            this.inventory = Objects.requireNonNull(inventory, "inventory");
        }

        @Override public int getSlots() { return inventory.size(); }

        @Override public Object reservationIdentity() { return inventory; }
        @Override public Object reservationSlot(int slot) { return slot; }
        @Override public Object resourceKey(Object resource) { return AEItemKey.of((ItemStack) resource); }
        @Override public Object resource(Object key) { return key instanceof AEItemKey item ? item.toStack(1) : ItemStack.EMPTY; }
        @Override public Object storedKey(int slot) { return inventory.getKey(slot); }
        @Override public long storedAmount(int slot) { return inventory.getAmount(slot); }
        @Override public long insertionBudget(Object key) {
            return inventory instanceof PatternReturnInventory returns ? returns.remainingCapacity((AEKey) key) : -1L;
        }
        @Override public long transfer(int slot, Object resource, long amount, boolean insert, boolean simulate) {
            AEItemKey key = AEItemKey.of((ItemStack) resource);
            return insert ? inventory.insert(slot, key, amount, mode(simulate))
                    : inventory.extract(slot, key, amount, mode(simulate));
        }
        @Override public boolean supportsLargeStacks() { return inventory instanceof PatternReturnInventory; }
        @Override public boolean requiresServerOutputPlanning() { return inventory instanceof PatternReturnInventory; }

        @Override public ItemStack getStackInSlot(int slot) {
            AEKey key = inventory.getKey(slot);
            return key instanceof AEItemKey item ? item.toStack(AE2NativeAdapters.amount(inventory.getAmount(slot))) : ItemStack.EMPTY;
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            AEItemKey key = AEItemKey.of(stack);
            long inserted = inventory.insert(slot, key, stack.getCount(), mode(simulate));
            return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(AE2NativeAdapters.amount(stack.getCount() - inserted));
        }

        @Override public ItemStack extractItem(int slot, int requested, boolean simulate) {
            AEKey key = inventory.getKey(slot);
            if (!(key instanceof AEItemKey item) || requested <= 0) return ItemStack.EMPTY;
            long extracted = inventory.extract(slot, item, requested, mode(simulate));
            return item.toStack(AE2NativeAdapters.amount(extracted));
        }

        @Override public int getSlotLimit(int slot) {
            AEKey key = inventory.getKey(slot);
            return AE2NativeAdapters.amount(PatternReturnInventory.slotLimit(inventory, slot, key, AEKeyType.items()));
        }

        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && inventory.isAllowedIn(slot, AEItemKey.of(stack));
        }

        @Override public long amount(int slot) {
            return inventory.getKey(slot) instanceof AEItemKey ? inventory.getAmount(slot) : 0L;
        }

        @Override public long capacity(int slot) {
            AEKey key = inventory.getKey(slot);
            return key == null || key instanceof AEItemKey
                    ? PatternReturnInventory.slotLimit(inventory, slot, key, AEKeyType.items()) : 0L;
        }

        @Override public long capacity(int slot, ItemStack resource) {
            return PatternReturnInventory.slotLimit(inventory, slot, AEItemKey.of(resource), AEKeyType.items());
        }

        @Override public void setContents(int slot, ItemStack stack, long amount) {
            if ((stack.isEmpty() || amount <= 0L) && inventory.getKey(slot) != null
                    && !(inventory.getKey(slot) instanceof AEItemKey)) return;
            inventory.setStack(slot, stack.isEmpty() || amount <= 0L ? null
                    : new GenericStack(AEItemKey.of(stack), amount));
        }

        @Override public boolean isSyncCapacityValid(int slot, ItemStack stack, long capacity) {
            return stack.isEmpty() ? capacity == 0L
                    || capacity == PatternReturnInventory.slotLimit(inventory, slot, null, AEKeyType.items())
                    : capacity == PatternReturnInventory.slotLimit(inventory, slot, AEItemKey.of(stack), AEKeyType.items());
        }

        @Override public void setContents(ItemStack[] stacks, long[] amounts) {
            if (!(inventory instanceof PatternReturnInventory returns)) {
                NativeStackSync.Item.super.setContents(stacks, amounts);
                return;
            }
            GenericStack[] projection = new GenericStack[stacks.length];
            for (int slot = 0; slot < stacks.length; slot++) {
                if (!stacks[slot].isEmpty() && amounts[slot] > 0L) projection[slot] = new GenericStack(AEItemKey.of(stacks[slot]), amounts[slot]);
            }
            returns.replaceProjection(AEKeyType.items(), projection);
        }
    }

    private static final class InventoryFluids implements IFluidHandler, NativeStackSync.Fluid, NativeReservationAccess {
        private final GenericStackInv inventory;

        private InventoryFluids(GenericStackInv inventory) {
            this.inventory = Objects.requireNonNull(inventory, "inventory");
        }

        @Override public int getTanks() { return inventory.size(); }

        @Override public Object reservationIdentity() { return inventory; }
        @Override public Object reservationSlot(int slot) { return slot; }
        @Override public Object resourceKey(Object resource) { return AEFluidKey.of((FluidStack) resource); }
        @Override public Object resource(Object key) { return key instanceof AEFluidKey fluid ? fluid.toStack(1) : FluidStack.EMPTY; }
        @Override public Object storedKey(int slot) { return inventory.getKey(slot); }
        @Override public long storedAmount(int slot) { return inventory.getAmount(slot); }
        @Override public long insertionBudget(Object key) {
            return inventory instanceof PatternReturnInventory returns ? returns.remainingCapacity((AEKey) key) : -1L;
        }
        @Override public boolean requiresServerOutputPlanning() { return inventory instanceof PatternReturnInventory; }
        @Override public long transfer(int slot, Object resource, long amount, boolean insert, boolean simulate) {
            AEFluidKey key = AEFluidKey.of((FluidStack) resource);
            return insert ? inventory.insert(slot, key, amount, mode(simulate))
                    : inventory.extract(slot, key, amount, mode(simulate));
        }

        @Override public FluidStack getFluidInTank(int tank) {
            AEKey key = inventory.getKey(tank);
            return key instanceof AEFluidKey fluid ? fluid.toStack(AE2NativeAdapters.amount(inventory.getAmount(tank))) : FluidStack.EMPTY;
        }

        @Override public int getTankCapacity(int tank) {
            AEKey key = inventory.getKey(tank);
            return AE2NativeAdapters.amount(PatternReturnInventory.slotLimit(inventory, tank, key, AEKeyType.fluids()));
        }

        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            AEFluidKey key = AEFluidKey.of(stack);
            return key != null && inventory.isAllowedIn(tank, key);
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            AEFluidKey key = AEFluidKey.of(stack);
            if (key == null) return 0;
            if (inventory instanceof PatternReturnInventory) {
                return AE2NativeAdapters.amount(inventory.insert(key, stack.getAmount(), mode(action.simulate()), IActionSource.empty()));
            }
            long inserted = 0L;
            for (int tank = 0; tank < getTanks() && inserted < stack.getAmount(); tank++) {
                inserted += inventory.insert(tank, key, stack.getAmount() - inserted, mode(action.simulate()));
            }
            return AE2NativeAdapters.amount(inserted);
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            AEFluidKey key = AEFluidKey.of(stack);
            if (key == null) return FluidStack.EMPTY;
            long extracted = 0L;
            for (int tank = 0; tank < getTanks() && extracted < stack.getAmount(); tank++) {
                extracted += inventory.extract(tank, key, stack.getAmount() - extracted, mode(action.simulate()));
            }
            return key.toStack(AE2NativeAdapters.amount(extracted));
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            for (int tank = 0; tank < getTanks(); tank++) {
                AEKey key = inventory.getKey(tank);
                if (key instanceof AEFluidKey fluid) return drain(fluid.toStack(maxDrain), action);
            }
            return FluidStack.EMPTY;
        }

        @Override public long amount(int tank) {
            return inventory.getKey(tank) instanceof AEFluidKey ? inventory.getAmount(tank) : 0L;
        }

        @Override public long capacity(int tank) {
            AEKey key = inventory.getKey(tank);
            return key == null || key instanceof AEFluidKey
                    ? PatternReturnInventory.slotLimit(inventory, tank, key, AEKeyType.fluids()) : 0L;
        }

        @Override public void setContents(int tank, FluidStack stack, long amount) {
            if ((stack.isEmpty() || amount <= 0L) && inventory.getKey(tank) != null
                    && !(inventory.getKey(tank) instanceof AEFluidKey)) return;
            inventory.setStack(tank, stack.isEmpty() || amount <= 0L ? null
                    : new GenericStack(AEFluidKey.of(stack), amount));
        }

        @Override public boolean isSyncCapacityValid(int tank, FluidStack stack, long capacity) {
            return stack.isEmpty() ? capacity == 0L
                    || capacity == PatternReturnInventory.slotLimit(inventory, tank, null, AEKeyType.fluids())
                    : capacity == PatternReturnInventory.slotLimit(inventory, tank, AEFluidKey.of(stack), AEKeyType.fluids());
        }

        @Override public void setContents(FluidStack[] stacks, long[] amounts) {
            if (!(inventory instanceof PatternReturnInventory returns)) {
                NativeStackSync.Fluid.super.setContents(stacks, amounts);
                return;
            }
            GenericStack[] projection = new GenericStack[stacks.length];
            for (int slot = 0; slot < stacks.length; slot++) {
                if (!stacks[slot].isEmpty() && amounts[slot] > 0L) projection[slot] = new GenericStack(AEFluidKey.of(stacks[slot]), amounts[slot]);
            }
            returns.replaceProjection(AEKeyType.fluids(), projection);
        }
    }

    private static final class NetworkItems implements IItemHandler, NativeStackSync.Item, NativeReservationAccess {
        private final Supplier<MEStorage> storage;
        private final Supplier<List<AEKey>> keys;
        private final IActionSource source;
        private final java.util.Map<AEKey, Long> syncedAmounts = new java.util.HashMap<>();

        private NetworkItems(Supplier<MEStorage> storage, Supplier<List<AEKey>> keys, IActionSource source) {
            this.storage = Objects.requireNonNull(storage, "storage");
            this.keys = Objects.requireNonNull(keys, "keys");
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override public int getSlots() { return keys().size(); }

        @Override public Object reservationIdentity() { return storage.get(); }
        @Override public Object reservationSlot(int slot) { return key(slot); }
        @Override public Object resourceKey(Object resource) { return AEItemKey.of((ItemStack) resource); }
        @Override public Object resource(Object key) { return key instanceof AEItemKey item ? item.toStack(1) : ItemStack.EMPTY; }
        @Override public Object storedKey(int slot) { return key(slot); }
        @Override public long storedAmount(int slot) { return storage.get().extract(key(slot), Long.MAX_VALUE, Actionable.SIMULATE, source); }

        @Override public ItemStack getStackInSlot(int slot) {
            AEKey key = key(slot);
            if (!(key instanceof AEItemKey item)) return ItemStack.EMPTY;
            return item.toStack(AE2NativeAdapters.amount(syncedAmounts.getOrDefault(item,
                    storage.get().extract(item, Integer.MAX_VALUE, Actionable.SIMULATE, source))));
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override public ItemStack extractItem(int slot, int requested, boolean simulate) {
            AEKey key = key(slot);
            if (!(key instanceof AEItemKey item) || requested <= 0) return ItemStack.EMPTY;
            return item.toStack(AE2NativeAdapters.amount(storage.get().extract(item, requested, mode(simulate), source)));
        }

        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }

        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && AEItemKey.of(stack).equals(key(slot));
        }

        @Override public long amount(int slot) {
            AEKey key = key(slot);
            return syncedAmounts.getOrDefault(key, storage.get().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source));
        }

        @Override public long capacity(int slot) { return Long.MAX_VALUE; }

        @Override public void setContents(int slot, ItemStack stack, long amount) {
            AEKey key = key(slot);
            if (key instanceof AEItemKey item && item.matches(stack)) syncedAmounts.put(key, Math.max(0L, amount));
        }

        private List<AEKey> keys() {
            return keys.get().stream().filter(key -> key instanceof AEItemKey).distinct().toList();
        }

        private AEKey key(int slot) { return keys().get(slot); }
    }

    private static final class NetworkFluids implements IFluidHandler, NativeStackSync.Fluid, NativeReservationAccess {
        private final Supplier<MEStorage> storage;
        private final Supplier<List<AEKey>> keys;
        private final IActionSource source;
        private final java.util.Map<AEKey, Long> syncedAmounts = new java.util.HashMap<>();

        private NetworkFluids(Supplier<MEStorage> storage, Supplier<List<AEKey>> keys, IActionSource source) {
            this.storage = Objects.requireNonNull(storage, "storage");
            this.keys = Objects.requireNonNull(keys, "keys");
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override public int getTanks() { return keys().size(); }

        @Override public Object reservationIdentity() { return storage.get(); }
        @Override public Object reservationSlot(int tank) { return key(tank); }
        @Override public Object resourceKey(Object resource) { return AEFluidKey.of((FluidStack) resource); }
        @Override public Object resource(Object key) { return key instanceof AEFluidKey fluid ? fluid.toStack(1) : FluidStack.EMPTY; }
        @Override public Object storedKey(int tank) { return key(tank); }
        @Override public long storedAmount(int tank) { return storage.get().extract(key(tank), Long.MAX_VALUE, Actionable.SIMULATE, source); }

        @Override public FluidStack getFluidInTank(int tank) {
            AEKey key = key(tank);
            if (!(key instanceof AEFluidKey fluid)) return FluidStack.EMPTY;
            return fluid.toStack(AE2NativeAdapters.amount(syncedAmounts.getOrDefault(fluid,
                    storage.get().extract(fluid, Integer.MAX_VALUE, Actionable.SIMULATE, source))));
        }

        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }

        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            AEFluidKey key = AEFluidKey.of(stack);
            return key != null && key.equals(key(tank));
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            return 0;
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            AEFluidKey key = AEFluidKey.of(stack);
            if (key == null || !keys().contains(key)) return FluidStack.EMPTY;
            return key.toStack(AE2NativeAdapters.amount(storage.get().extract(key, stack.getAmount(), mode(action.simulate()), source)));
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            for (AEKey key : keys()) {
                if (key instanceof AEFluidKey fluid) return drain(fluid.toStack(maxDrain), action);
            }
            return FluidStack.EMPTY;
        }

        @Override public long amount(int tank) {
            AEKey key = key(tank);
            return syncedAmounts.getOrDefault(key, storage.get().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source));
        }

        @Override public long capacity(int tank) { return Long.MAX_VALUE; }

        @Override public void setContents(int tank, FluidStack stack, long amount) {
            AEKey key = key(tank);
            if (key instanceof AEFluidKey fluid && fluid.matches(stack)) syncedAmounts.put(key, Math.max(0L, amount));
        }

        private List<AEKey> keys() {
            return keys.get().stream().filter(key -> key instanceof AEFluidKey).distinct().toList();
        }

        private AEKey key(int tank) { return keys().get(tank); }
    }

    private static final class OutputItems implements IItemHandler, NativeStackSync.Item, NativeReservationAccess {
        private final Supplier<@Nullable MEStorage> storage;
        private final IActionSource source;
        private final Runnable changed;
        private final IItemHandler local;

        private OutputItems(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage, IActionSource source,
                            Runnable changed) {
            this.storage = storage;
            this.source = source;
            this.changed = changed;
            this.local = items(inventory);
        }

        @Override public int getSlots() { return local.getSlots(); }
        @Override public Object reservationIdentity() { return ((NativeReservationAccess) local).reservationIdentity(); }
        @Override public Object reservationSlot(int slot) { return slot; }
        @Override public Object resourceKey(Object resource) { return ((NativeReservationAccess) local).resourceKey(resource); }
        @Override public Object resource(Object key) { return ((NativeReservationAccess) local).resource(key); }
        @Override public Object storedKey(int slot) { return ((NativeReservationAccess) local).storedKey(slot); }
        @Override public long storedAmount(int slot) { return ((NativeReservationAccess) local).storedAmount(slot); }
        @Override public ItemStack getStackInSlot(int slot) { return local.getStackInSlot(slot); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return local.extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return local.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return local.isItemValid(slot, stack); }
        @Override public long amount(int slot) { return ((NativeStackSync.Item) local).amount(slot); }
        @Override public long capacity(int slot) { return ((NativeStackSync.Item) local).capacity(slot); }
        @Override public boolean isSyncCapacityValid(int slot, ItemStack stack, long capacity) {
            return ((NativeStackSync.Item) local).isSyncCapacityValid(slot, stack, capacity);
        }
        @Override public void setContents(int slot, ItemStack stack, long amount) {
            ((NativeStackSync.Item) local).setContents(slot, stack, amount);
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            AEItemKey key = AEItemKey.of(stack);
            MEStorage network = storage.get();
            long inserted = 0L;
            if (network != null) {
                long requested = stack.getCount();
                long simulated = network.insert(key, requested, Actionable.SIMULATE, source);
                inserted = simulate ? simulated : network.insert(key, Math.min(requested, Math.max(0L, simulated)),
                        Actionable.MODULATE, source);
            }
            inserted = Math.min(stack.getCount(), Math.max(0L, inserted));
            ItemStack remaining = inserted == stack.getCount() ? ItemStack.EMPTY
                    : local.insertItem(slot, stack.copyWithCount(AE2NativeAdapters.amount(stack.getCount() - inserted)), simulate);
            if (!simulate && remaining.getCount() != stack.getCount()) changed.run();
            return remaining;
        }
    }

    private static final class OutputFluids implements IFluidHandler, NativeStackSync.Fluid, NativeReservationAccess {
        private final Supplier<@Nullable MEStorage> storage;
        private final IActionSource source;
        private final Runnable changed;
        private final IFluidHandler local;

        private OutputFluids(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage, IActionSource source,
                             Runnable changed) {
            this.storage = storage;
            this.source = source;
            this.changed = changed;
            this.local = fluids(inventory);
        }

        @Override public int getTanks() { return local.getTanks(); }
        @Override public Object reservationIdentity() { return ((NativeReservationAccess) local).reservationIdentity(); }
        @Override public Object reservationSlot(int tank) { return tank; }
        @Override public Object resourceKey(Object resource) { return ((NativeReservationAccess) local).resourceKey(resource); }
        @Override public Object resource(Object key) { return ((NativeReservationAccess) local).resource(key); }
        @Override public Object storedKey(int tank) { return ((NativeReservationAccess) local).storedKey(tank); }
        @Override public long storedAmount(int tank) { return ((NativeReservationAccess) local).storedAmount(tank); }
        @Override public FluidStack getFluidInTank(int tank) { return local.getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { return local.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return local.isFluidValid(tank, stack); }
        @Override public long amount(int tank) { return ((NativeStackSync.Fluid) local).amount(tank); }
        @Override public long capacity(int tank) { return ((NativeStackSync.Fluid) local).capacity(tank); }
        @Override public boolean isSyncCapacityValid(int tank, FluidStack stack, long capacity) {
            return ((NativeStackSync.Fluid) local).isSyncCapacityValid(tank, stack, capacity);
        }
        @Override public void setContents(int tank, FluidStack stack, long amount) {
            ((NativeStackSync.Fluid) local).setContents(tank, stack, amount);
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            AEFluidKey key = AEFluidKey.of(stack);
            if (key == null) return 0;
            MEStorage network = storage.get();
            long inserted = 0L;
            if (network != null) {
                long requested = stack.getAmount();
                long simulated = network.insert(key, requested, Actionable.SIMULATE, source);
                inserted = action.simulate() ? simulated : network.insert(key,
                        Math.min(requested, Math.max(0L, simulated)), Actionable.MODULATE, source);
            }
            inserted = Math.min(stack.getAmount(), Math.max(0L, inserted));
            int localInserted = local.fill(stack.copyWithAmount(AE2NativeAdapters.amount(stack.getAmount() - inserted)), action);
            int total = AE2NativeAdapters.amount(inserted + localInserted);
            if (!action.simulate() && total > 0) changed.run();
            return total;
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return local.drain(stack, action); }
        @Override public FluidStack drain(int amount, FluidAction action) { return local.drain(amount, action); }
    }
}
