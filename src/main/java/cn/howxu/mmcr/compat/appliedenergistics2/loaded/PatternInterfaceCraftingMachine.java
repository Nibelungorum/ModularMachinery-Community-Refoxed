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
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue.GroupSnapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity.PendingPatternCursor;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

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
        if (pushing || settling || !hostCurrent()) return false;
        var queue = host.pendingPatterns();
        long now = host.getLevel().getGameTime();
        if (!queue.canAttempt(now)) return false;
        pushing = true;
        boolean accepted = false;
        try {
            var limits = host.pendingScheduler().admissionLimits(patternDetails, inputHolders, now);
            if (limits == null || !validInputs(patternDetails, inputHolders, host.getLevel()) || !hostCurrent()) return false;
            // Queue writes are synchronous and callback-free. Only notify after both owners agree.
            if (!queue.offer(patternDetails.getDefinition(), patternDetails.getOutputs(), inputHolders, limits, now)) {
                return false;
            }
            for (KeyCounter holder : inputHolders) holder.clear();
            accepted = true;
            host.setChanged();
            return true;
        } catch (RuntimeException exception) {
            if (accepted) MMCR.LOG.warn("Pattern admission accepted before notification failure at {}",
                    host.getBlockPos(), exception);
            return accepted;
        } finally {
            pushing = false;
        }
    }

    /** Starts one prepared prefix; native payments are never replayed or transactionally rolled back. */
    public boolean startPending(GroupSnapshot group, MachineControllerBlockEntity controller,
                                PendingPatternCursor cursor, BooleanSupplier budget) {
        if (pushing || settling || !hostCurrent()) return false;
        flushPendingReturns();
        if (pendingRequest != null || !hostCurrent()) return false;
        pushing = true;
        PendingRequest owned = null;
        PatternStartReservation reservation = null;
        long copies = 0L;
        try {
            List<MachineOutput> outputs = outputs(group.outputs());
            if (outputs == null) return false;
            // Each proposal has exactly p copies. Every resource family shares that proposal's sole scratch owner.
            Map<Long, GenericStackInv> proposals = new HashMap<>();
            reservation = controller.reservePendingPatternStart(host.getBlockPos(), outputs, group.copies(), parallelism -> {
                GenericStackInv inventory = proposals.computeIfAbsent(parallelism,
                        count -> AE2NativeAdapters.requestInventory(group.inputs(count)));
                return requestCapabilities(inventory, group.inputs(parallelism));
            }, budget, cursor);
            if (reservation.status() != PatternStartReservation.Status.RESERVED) return false;
            copies = reservation.parallelism();
            GenericStackInv inventory = proposals.get(copies);
            if (copies <= 0L || copies > group.copies() || inventory == null) return false;

            // Preparation is side-effect-free; conservatively reserve room for every possible leftover.
            GenericStackInv simulatedReturns = AE2NativeAdapters.copyForSimulation(host.getLogic().getReturnInv());
            for (int slot = 0; slot < inventory.size(); slot++) {
                GenericStack stack = inventory.getStack(slot);
                if (stack != null && simulatedReturns.insert(stack.what(), stack.amount(), Actionable.MODULATE,
                        IActionSource.ofMachine(host)) != stack.amount()) return false;
            }
            if (!hostCurrent()) return false;
            PendingRequest proposal = new PendingRequest(inventory, reservation);
            // No callback/save window may observe both the queued prefix and its scratch copy.
            host.pendingPatterns().removeCopies(group.id(), copies);
            owned = proposal;
            pendingRequest = owned;
            host.setChanged();
            if (!hostCurrent()) return false;
            boolean committed = reservation.commit(owned::accept);
            if (committed) owned.accept();
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Native pending pattern start interrupted at {}", host.getBlockPos(), exception);
        } finally {
            if (owned != null) {
                try {
                    if (owned.claimIfPaid()) {
                        // Payment (including an uncertain prefix) owns only the live scratch balance.
                        owned.accept();
                    } else {
                        // Restore first and unpublish scratch without any callback between these writes.
                        host.pendingPatterns().restoreCopies(group, copies);
                        if (pendingRequest == owned) pendingRequest = null;
                        owned.restored = true;
                        host.setChanged();
                    }
                } catch (RuntimeException exception) {
                    // A failed restoration must not invite replay of material still owned by scratch.
                    if (!owned.restored) owned.accepted = true;
                    MMCR.LOG.warn("Native pattern ownership retained before final notification failure at {}",
                            host.getBlockPos(), exception);
                }
                owned.releaseReferences();
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
        return owned != null && owned.accepted;
    }

    private boolean hostCurrent() {
        Level level = host.getLevel();
        return !host.isRemoved() && level != null && !level.isClientSide()
                && level.hasChunkAt(host.getBlockPos()) && level.getBlockEntity(host.getBlockPos()) == host;
    }

    private static List<MachineCapability> requestCapabilities(GenericStackInv inventory, KeyCounter[] inputs) {
        AppMekBridge bridge = AppMekBridge.get();
        AppMekBridge.PatternRequest chemicals = bridge.available()
                ? bridge.patternRequestFromInventory(inventory) : bridge.patternRequest(inputs);
        List<MachineCapability> capabilities = new ArrayList<>(List.of(
                new ItemBusCapability(AE2NativeAdapters.items(inventory), IOType.INPUT),
                new FluidHatchCapability(AE2NativeAdapters.fluids(inventory), IOType.INPUT)));
        capabilities.addAll(chemicals.capabilities());
        capabilities.addAll(LightningBridge.get().patternInputs(inventory));
        return List.copyOf(capabilities);
    }

    /** Settles only owned leftovers; never retries a recipe start or holds a live controller after admission. */
    public void flushPendingReturns() {
        if (host.getLevel() != null && host.getLevel().isClientSide()) return;
        if (pushing || settling || pendingRequest == null || !pendingRequest.accepted) return;
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
        // Includes the published pre-payment scratch: a native callback can save at any point during payment.
        if (pendingRequest != null) {
            pendingRequest.inventory.writeToChildTag(output, PENDING_RETURNS_KEY, registries);
        } else output.remove(PENDING_RETURNS_KEY);
    }

    public void loadPendingReturns(CompoundTag input, HolderLookup.Provider registries) {
        pendingRequest = input.contains(PENDING_RETURNS_KEY, Tag.TAG_LIST)
                ? new PendingRequest(AE2NativeAdapters.restoreRequestInventory(
                        input.getList(PENDING_RETURNS_KEY, Tag.TAG_COMPOUND), registries), null) : null;
    }

    /** Uses the same AE key drop policy as the native return inventory, including fluid/chemical wrappers. */
    public void addPendingDrops(List<ItemStack> drops) {
        if (pendingRequest == null) return;
        // Breaking the host can reenter during the pre-payment notification. Dropped ownership cannot be restored.
        pendingRequest.accepted = true;
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
        private @Nullable PatternStartReservation reservation;
        private boolean accepted;
        private boolean restored;

        private PendingRequest(GenericStackInv inventory, @Nullable PatternStartReservation reservation) {
            this.inventory = inventory;
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
            host.setChanged();
        }

        private void releaseReferences() {
            reservation = null;
        }
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    private static boolean hasSupportedKey(AEKey key) {
        return key instanceof AEItemKey || key instanceof AEFluidKey
                || AppMekBridge.get().supportsPatternInput(key)
                || LightningBridge.get().supportsPatternInput(key);
    }

    static boolean validInputs(IPatternDetails details, KeyCounter[] inputs, Level level) {
        if (!(details instanceof AEProcessingPattern pattern) || inputs == null || level == null
                || outputs(pattern.getOutputs()) == null) return false;
        var expected = pattern.getInputs();
        if (inputs.length != expected.length || inputs.length == 0) return false;
        try {
            for (int index = 0; index < inputs.length; index++) {
                if (inputs[index] == null) return false;
                long units = 0L;
                for (var entry : inputs[index]) {
                    long amount = entry.getLongValue();
                    if (amount <= 0L || !hasSupportedKey(entry.getKey())
                            || !expected[index].isValid(entry.getKey(), level)) return false;
                    long templateAmount = 0L;
                    for (var template : expected[index].getPossibleInputs()) {
                        if (entry.getKey().matches(template)) { templateAmount = template.amount(); break; }
                    }
                    if (templateAmount <= 0L || amount % templateAmount != 0L) return false;
                    units = Math.addExact(units, amount / templateAmount);
                }
                if (units != expected[index].getMultiplier()) return false;
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    static List<MachineOutput> outputs(List<GenericStack> stacks) {
        List<MachineOutput> outputs = new ArrayList<>();
        Map<AEKey, Long> lightningAmounts = new HashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0L) return null;
            if (stack.what() instanceof AEItemKey item) {
                if (stack.amount() > Integer.MAX_VALUE) return null;
                outputs.add(new MachineOutput.ItemOutput(item.toStack((int) stack.amount()), 1F));
            } else if (stack.what() instanceof AEFluidKey fluid) {
                if (stack.amount() > Integer.MAX_VALUE) return null;
                outputs.add(new MachineOutput.FluidOutput(fluid.toStack((int) stack.amount()), 1F));
            } else {
                Optional<MachineOutput> chemical = AppMekBridge.get().patternOutput(stack.what(), stack.amount());
                if (chemical.isEmpty()) chemical = LightningBridge.get().patternOutput(stack.what(), stack.amount());
                if (chemical.isEmpty()) return null;
                if (chemical.get() instanceof LightningOutput) {
                    long previous = lightningAmounts.getOrDefault(stack.what(), 0L);
                    if (stack.amount() > Integer.MAX_VALUE - previous) return null;
                    lightningAmounts.put(stack.what(), previous + stack.amount());
                }
                outputs.add(chemical.get());
            }
        }
        return outputs.isEmpty() ? null : List.copyOf(outputs);
    }

}
