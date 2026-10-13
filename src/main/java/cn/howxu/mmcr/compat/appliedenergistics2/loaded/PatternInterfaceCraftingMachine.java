package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.networking.security.IActionSource;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.runtime.PatternStartBatchReservation;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Virtual AE2 crafting machine that forwards processing patterns to linked MMCR controllers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceCraftingMachine implements ICraftingMachine {
    private static final String PENDING_RETURNS_KEY = "pattern_pending_returns";
    private final PatternInterfaceBlockEntity host;
    private @Nullable PendingRequest pendingRequest;
    private boolean pushing;
    private boolean settling;

    public PatternInterfaceCraftingMachine(PatternInterfaceBlockEntity host) {
        this.host = host;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return PatternContainerGroup.nothing();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolders, Direction ejectionDirection) {
        return pushBatchPattern(patternDetails, inputHolders, 1L, ejectionDirection);
    }

    public long maxBatchSize(IPatternDetails patternDetails) {
        if (pushing || settling || pendingRequest != null) return 0L;
        if (!(patternDetails instanceof AEProcessingPattern pattern) || !hasSupportedInputs(pattern)) return 0L;
        List<MachineOutput> outputs = outputs(pattern);
        if (outputs == null) return 0L;
        long machineCap = host.maxPatternBatchSize();
        long recipeCap = host.maxRecipeBatchSize(outputs);
        long resourceCap = Math.min(batchResourceLimit(pattern.getSparseInputs()), batchResourceLimit(pattern.getOutputs()));
        return Math.min(resourceCap, Math.min(machineCap, recipeCap));
    }

    private static long batchResourceLimit(List<GenericStack> stacks) {
        Map<AEKey, Long> amounts = new HashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null) continue;
            if (stack.amount() <= 0L) return 0L;
            long previous = amounts.getOrDefault(stack.what(), 0L);
            if (stack.amount() > Long.MAX_VALUE - previous) return 0L;
            amounts.put(stack.what(), previous + stack.amount());
        }
        long limit = Long.MAX_VALUE;
        for (long amount : amounts.values()) limit = Math.min(limit, Long.MAX_VALUE / amount);
        return limit;
    }

    public boolean pushBatchPattern(IPatternDetails patternDetails, KeyCounter[] inputHolders, long batchSize,
                                    Direction ejectionDirection) {
        if (pushing || settling) return false;
        flushPendingReturns();
        if (pendingRequest != null) return false;
        if (!(patternDetails instanceof AEProcessingPattern pattern)) return false;
        List<MachineOutput> outputs = outputs(pattern);
        if (batchSize <= 0L || outputs == null || !hasSupportedInputs(pattern)
                || !hasSupportedHolderKeys(inputHolders)) return false;

        pushing = true;
        PendingRequest owned = null;
        PatternStartBatchReservation reservation = null;
        try {
            // Admission can reduce a lane or reject a proposal. Those proposals must not manufacture material slices.
            GenericStackInv inventory = AE2NativeAdapters.requestInventory(inputHolders);
            AppMekBridge bridge = AppMekBridge.get();
            reservation = host.reservePatternStarts(outputs, batchSize, parallelism -> {
                GenericStackInv view = AE2NativeAdapters.requestInventoryView(inventory, parallelism, batchSize);
                AppMekBridge.PatternRequest chemicalRequest = bridge.available()
                        ? bridge.patternRequestFromInventory(view) : bridge.patternRequest(inputHolders);
                LaneRequest request = new LaneRequest(view, view, chemicalRequest);
                List<MachineCapability> inputs = new ArrayList<>(List.of(
                        new ItemBusCapability(AE2NativeAdapters.items(request.itemRequest()), IOType.INPUT),
                        new FluidHatchCapability(AE2NativeAdapters.fluids(request.fluidRequest()), IOType.INPUT)));
                inputs.addAll(request.chemicalRequest().capabilities());
                return List.copyOf(inputs);
            });
            if (reservation.status() != PatternStartBatchReservation.Status.RESERVED
                    || reservation.parallelism() != batchSize) return false;

            GenericStackInv simulatedReturns = AE2NativeAdapters.copyForSimulation(host.getLogic().getReturnInv());
            for (int slot = 0; slot < inventory.size(); slot++) {
                GenericStack stack = inventory.getStack(slot);
                if (stack != null && simulatedReturns.insert(stack.what(), stack.amount(), Actionable.MODULATE,
                        IActionSource.ofMachine(host)) != stack.amount()) return false;
            }
            owned = new PendingRequest(inventory, inputHolders, reservation);
            pendingRequest = owned;
            boolean committed = reservation.commit(owned::accept);
            if (committed) owned.accept();
            return owned.claimIfPaid();
        } catch (RuntimeException exception) {
            try {
                if (owned != null) owned.claimIfPaid();
            } catch (RuntimeException ownershipFailure) {
                MMCR.LOG.warn("Native pattern ownership was claimed before notification failure at {}", host.getBlockPos(), ownershipFailure);
            }
            boolean accepted = owned != null && owned.accepted;
            if (accepted) MMCR.LOG.warn("Accepted native pattern retains pending returns at {}", host.getBlockPos(), exception);
            return accepted;
        } finally {
            if (owned != null) {
                try {
                    owned.claimIfPaid();
                } catch (RuntimeException exception) {
                    MMCR.LOG.warn("Native pattern ownership was claimed before final notification failure at {}", host.getBlockPos(), exception);
                }
                owned.releaseReferences();
                if (!owned.accepted && pendingRequest == owned) pendingRequest = null;
            }
            if (reservation != null) {
                try {
                    reservation.close();
                } catch (RuntimeException exception) {
                    MMCR.LOG.warn("Failed to close native pattern proposal at {}", host.getBlockPos(), exception);
                }
            }
            pushing = false;
            flushPendingReturns();
        }
    }

    /** Settles only owned leftovers; never retries a recipe start or holds a live controller after admission. */
    public void flushPendingReturns() {
        if (host.getLevel() != null && host.getLevel().isClientSide()) return;
        if (pushing || settling || pendingRequest == null || !pendingRequest.claimIfPaid()) return;
        PendingRequest owned = pendingRequest;
        GenericStackInv returns = host.getLogic().getReturnInv();
        settling = true;
        boolean batch = false;
        try {
            // Native return-inventory callbacks can save/reenter. Publish both inventories before notifying them.
            returns.beginBatch();
            batch = true;
            for (int slot = 0; slot < owned.inventory.size(); slot++) {
                GenericStack debt = owned.inventory.getStack(slot);
                for (int target = 0; debt != null && target < returns.size(); target++) {
                    GenericStack before = returns.getStack(target);
                    long beforeAmount = before != null && before.what().equals(debt.what()) ? before.amount() : 0L;
                    long capacity = PatternReturnInventory.slotCapacity(returns, target, debt.what(), debt.what().getType());
                    long requested = Math.min(debt.amount(), Math.max(0L, capacity - beforeAmount));
                    if (requested == 0L) continue;
                    try {
                        returns.insert(target, debt.what(), requested, Actionable.MODULATE);
                    } finally {
                        GenericStack after = returns.getStack(target);
                        long afterAmount = after != null && after.what().equals(debt.what()) ? after.amount() : 0L;
                        long moved = afterAmount - beforeAmount;
                        if (moved > 0L) {
                            long remaining = debt.amount() - moved;
                            owned.inventory.setStack(slot, remaining > 0L ? new GenericStack(debt.what(), remaining) : null);
                            host.setChanged();
                        }
                    }
                    debt = owned.inventory.getStack(slot);
                }
            }
            if (owned.inventory.isEmpty() && pendingRequest == owned) {
                pendingRequest = null;
                host.setChanged();
            }
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Native pattern return settlement will retry at {}", host.getBlockPos(), exception);
        } finally {
            try {
                if (batch) returns.endBatch();
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Native pattern returns were recorded before notification failure at {}", host.getBlockPos(), exception);
            } finally {
                settling = false;
            }
        }
    }

    public void savePendingReturns(CompoundTag output, HolderLookup.Provider registries) {
        if (pendingRequest != null && pendingRequest.claimIfPaid()) {
            pendingRequest.inventory.writeToChildTag(output, PENDING_RETURNS_KEY, registries);
        } else output.remove(PENDING_RETURNS_KEY);
    }

    public void loadPendingReturns(CompoundTag input, HolderLookup.Provider registries) {
        pendingRequest = input.contains(PENDING_RETURNS_KEY, Tag.TAG_LIST)
                ? new PendingRequest(AE2NativeAdapters.restoreRequestInventory(
                        input.getList(PENDING_RETURNS_KEY, Tag.TAG_COMPOUND), registries), null, null) : null;
    }

    /** Uses the same AE key drop policy as the native return inventory, including fluid/chemical wrappers. */
    public void addPendingDrops(List<ItemStack> drops) {
        if (pendingRequest == null || !pendingRequest.claimIfPaid()) return;
        for (int slot = 0; slot < pendingRequest.inventory.size(); slot++) {
            GenericStack stack = pendingRequest.inventory.getStack(slot);
            if (stack == null) continue;
            PatternReturnInventory.addDrops(stack, drops, host.getLevel(), host.getBlockPos());
            pendingRequest.inventory.setStack(slot, null);
        }
        pendingRequest = null;
        host.setChanged();
    }

    /** Material ownership only; runtime/lane references are released when the native start returns.
     * @author howxu <dev@howxu.cn>
     */
    private final class PendingRequest {
        private final GenericStackInv inventory;
        private @Nullable KeyCounter[] holders;
        private @Nullable PatternStartBatchReservation reservation;
        private boolean accepted;

        private PendingRequest(GenericStackInv inventory, @Nullable KeyCounter[] holders,
                               @Nullable PatternStartBatchReservation reservation) {
            this.inventory = inventory;
            this.holders = holders;
            this.reservation = reservation;
            accepted = reservation == null;
        }

        private boolean claimIfPaid() {
            if (!accepted && reservation != null && reservation.ownershipAccepted()) accept();
            return accepted;
        }

        private void accept() {
            if (accepted) return;
            accepted = true;
            for (KeyCounter holder : holders) holder.clear();
            holders = null;
            host.setChanged();
        }

        private void releaseReferences() {
            holders = null;
            reservation = null;
        }
    }

    private record LaneRequest(GenericStackInv itemRequest, GenericStackInv fluidRequest,
                               AppMekBridge.PatternRequest chemicalRequest) {
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    private static boolean hasSupportedHolderKeys(KeyCounter[] holders) {
        for (KeyCounter holder : holders) {
            for (var entry : holder) {
                if (entry.getLongValue() > 0L && !(entry.getKey() instanceof AEItemKey)
                        && !(entry.getKey() instanceof AEFluidKey)
                        && !AppMekBridge.get().supportsPatternInput(entry.getKey())) return false;
            }
        }
        return true;
    }

    private static boolean hasSupportedInputs(AEProcessingPattern pattern) {
        return pattern.getSparseInputs().stream().filter(stack -> stack != null).allMatch(stack ->
                stack.amount() > 0L && (stack.what() instanceof AEItemKey || stack.what() instanceof AEFluidKey
                        || AppMekBridge.get().supportsPatternInput(stack.what())));
    }

    private static List<MachineOutput> outputs(AEProcessingPattern pattern) {
        List<MachineOutput> outputs = new ArrayList<>();
        for (GenericStack stack : pattern.getOutputs()) {
            if (stack.amount() <= 0L) return null;
            if (stack.what() instanceof AEItemKey item) {
                if (stack.amount() > Integer.MAX_VALUE) return null;
                outputs.add(new MachineOutput.ItemOutput(item.toStack((int) stack.amount()), 1F));
            } else if (stack.what() instanceof AEFluidKey fluid) {
                if (stack.amount() > Integer.MAX_VALUE) return null;
                outputs.add(new MachineOutput.FluidOutput(fluid.toStack((int) stack.amount()), 1F));
            } else {
                Optional<MachineOutput> chemical = AppMekBridge.get().patternOutput(stack.what(), stack.amount());
                if (chemical.isEmpty()) return null;
                outputs.add(chemical.get());
            }
        }
        return List.copyOf(outputs);
    }

}
