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
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.runtime.PatternStartDiagnostics;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
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
    private long nextDiagnosticTick = Long.MIN_VALUE;

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
        // Native CPU retries can arrive every tick; sample each host at most once per second.
        boolean diagnose = host.getLevel().getGameTime() >= nextDiagnosticTick;
        if (diagnose) {
            nextDiagnosticTick = host.getLevel().getGameTime() + 20L;
            MMCR.LOG.debug("[PatternBatchDiag] push entry: host={} legacyPending={} ownedPending={}",
                    host.getBlockPos(), !host.pendingPatterns().isEmpty(), pendingRequest != null);
        }
        if (!host.pendingPatterns().isEmpty()) return false;
        flushPendingReturns();
        if (pendingRequest != null || !hostCurrent()) return false;
        pushing = true;
        PendingRequest owned = null;
        PatternStartReservation reservation = null;
        KeyCounter[] originals = null;
        String rejection = "no-matching-controller-or-recipe";
        try {
            Level level = host.getLevel();
            if (!validInputs(patternDetails, inputHolders, level)) {
                rejection = "invalid-inputs";
                return false;
            }
            PatternBatchDetails batch = patternDetails instanceof PatternBatchDetails details ? details : null;
            IPatternDetails original = batch == null ? patternDetails : batch.original();
            PatternBatchBinding binding = batch == null ? null : new PatternBatchBinding(batch.data().recipePoolId(),
                    batch.data().recipeId(), batch.data().recipeCraftsPerPattern());
            long parallelism = batch == null ? 1L
                    : Math.multiplyExact(batch.data().multiplier(), batch.data().recipeCraftsPerPattern());
            if (diagnose) MMCR.LOG.debug("[PatternBatchDiag] push request: host={} patternType={} batch={} parallelism={} links={}",
                    host.getBlockPos(), patternDetails.getClass().getName(), batch == null ? null : batch.data(),
                    parallelism, host.linkedControllerPositions());
            if (diagnose) MMCR.LOG.info("[PatternBatchDiag] cpu-dispatch: host={} {} route={}",
                    host.getBlockPos(), PatternBatchDiagnostics.pattern(patternDetails), PatternBatchDiagnostics.callers());
            var boundRecipe = batch == null ? null : RecipeRegistry.getRecipe(binding.recipeId());
            if (batch != null && boundRecipe == null) {
                rejection = "missing-bound-recipe";
                return false;
            }
            GenericStackInv inventory = AE2NativeAdapters.requestInventory(inputHolders);
            List<MachineCapability> capabilities = requestCapabilities(inventory, inputHolders);
            List<MachineOutput> promised = outputs(original.getOutputs());
            findReservation:
            for (BlockPos position : host.linkedControllerPositions().stream().sorted(BlockPos::compareTo).toList()) {
                if (!level.hasChunkAt(position)
                        || !(level.getBlockEntity(position) instanceof MachineControllerBlockEntity controller)) continue;
                var candidates = batch == null ? controller.recipesForMachine() : List.of(boundRecipe);
                for (var recipe : candidates) {
                    var effective = controller.effectivePatternRecipe(recipe);
                    if (effective == null) {
                        rejection = "no-effective-recipe";
                        continue;
                    }
                    if (batch != null) {
                        if (!PatternBatchSupport.matches(binding, recipe, effective, original)) {
                            rejection = "binding-mismatch";
                            continue;
                        }
                        if (batch.data().multiplier() > 1L) {
                            var profile = PatternBatchSupport.inspectRecipe(recipe, effective, original);
                            if (profile == null || profile.safety() != PatternBatchSupport.Safety.PROVEN_LINEAR
                                    || batch.data().multiplier() > profile.maxMultiplier()) {
                                rejection = "unsafe-or-over-capacity-batch";
                                continue;
                            }
                            var snapshot = controller.currentRuntimeSnapshot();
                            var machine = snapshot.structure().machine() == null
                                    ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
                            if (machine != null && machine.behavior() instanceof RecipeBehavior behavior
                                    && behavior.hasBeforeStart()) {
                                rejection = "before-start-hook";
                                continue;
                            }
                        }
                    } else if (!controller.patternOutputsMatch(recipe, controller.runtimeSnapshot(), promised)) {
                        rejection = "output-mismatch";
                        continue;
                    }
                    reservation = PatternStartDiagnostics.trace(diagnose, host.getBlockPos(), position, recipe.id(),
                            parallelism, () -> controller.reserveExactPatternStart(host.getBlockPos(), recipe.id(),
                                    effective.outputs(), parallelism, capabilities));
                    if (diagnose) MMCR.LOG.debug("[PatternBatchDiag] push reservation: host={} controller={} recipe={} parallelism={} status={}",
                            host.getBlockPos(), position, recipe.id(), parallelism, reservation.status());
                    if (reservation.status() != PatternStartReservation.Status.RESERVED) {
                        rejection = "reservation-" + reservation.status();
                        reservation.close();
                        reservation = null;
                        continue;
                    }
                     if (!hostCurrent() || !host.linkedControllerPositions().contains(position)
                              || level.getBlockEntity(position) != controller || !host.pendingPatterns().isEmpty()) {
                         rejection = "host-or-controller-changed";
                         return false;
                     }
                    // Preparation hooks have completed. Preflight every family cumulatively before payment.
                    GenericStackInv simulatedReturns = AE2NativeAdapters.copyForSimulation(host.getLogic().getReturnInv());
                    for (int slot = 0; slot < inventory.size(); slot++) {
                        GenericStack stack = inventory.getStack(slot);
                        if (stack != null && simulatedReturns.insert(stack.what(), stack.amount(), Actionable.MODULATE,
                                IActionSource.ofMachine(host)) != stack.amount()) {
                            rejection = "insufficient-return-space";
                            return false;
                        }
                    }
                    originals = copyHolders(inputHolders);
                    owned = new PendingRequest(inventory, reservation);
                    // Callback-free ownership handoff: never expose both CPU holders and scratch to a save.
                    for (KeyCounter holder : inputHolders) holder.clear();
                    pendingRequest = owned;
                    host.setChanged();
                    rejection = "commit-not-accepted";
                    if (hostCurrent() && !owned.accepted && reservation.commit(owned::accept)) owned.accept();
                    // A failed attempt is not replayed in another lane; finally restores only zero-payment failures.
                    break findReservation;
                }
            }
        } catch (RuntimeException exception) {
            rejection = "exception";
            MMCR.LOG.warn("Native synchronous pattern start interrupted at {}", host.getBlockPos(), exception);
        } finally {
            if (owned != null) {
                try {
                    if (owned.claimIfPaid()) {
                        // Payment (including an uncertain prefix) owns only the live scratch balance.
                        owned.accept();
                    } else {
                        // No native payment: restore CPU ownership without callbacks between the two owners.
                        if (pendingRequest == owned) pendingRequest = null;
                        for (int index = 0; index < inputHolders.length; index++) {
                            inputHolders[index].addAll(originals[index]);
                        }
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
            if (diagnose) MMCR.LOG.debug("[PatternBatchDiag] push result: host={} accepted={} reason={}",
                    host.getBlockPos(), owned != null && owned.accepted,
                    owned != null && owned.accepted ? "accepted" : rejection);
        }
        return owned != null && owned.accepted;
    }

    private static KeyCounter[] copyHolders(KeyCounter[] holders) {
        KeyCounter[] copies = new KeyCounter[holders.length];
        for (int index = 0; index < holders.length; index++) {
            copies[index] = new KeyCounter();
            copies[index].addAll(holders[index]);
        }
        return copies;
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
        if (pushing || settling || pendingRequest == null || !pendingRequest.accepted || pendingRequest.carried) return;
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

    public boolean hasPendingReturns() {
        return pendingRequest != null && !pendingRequest.inventory.isEmpty();
    }

    /** A carrier cannot give ownership back to CPU holders if destruction reenters before payment. */
    public void saveCarrierReturns(CompoundTag output, HolderLookup.Provider registries) {
        if (pendingRequest != null) {
            pendingRequest.accepted = true;
            pendingRequest.carried = true;
            AE2NativeAdapters.sealRequestInventory(pendingRequest.inventory);
        }
        savePendingReturns(output, registries);
    }

    /** Material ownership only; runtime/lane references are released when the native start returns.
     * @author howxu <dev@howxu.cn>
     */
    private final class PendingRequest {
        private final GenericStackInv inventory;
        private @Nullable PatternStartReservation reservation;
        private boolean accepted;
        private boolean restored;
        private boolean carried;

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
        IPatternDetails original = details instanceof PatternBatchDetails batch ? batch.original() : details;
        if (!(original instanceof AEProcessingPattern) || inputs == null || level == null
                || outputs(original.getOutputs()) == null) return false;
        var expected = details.getInputs();
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
