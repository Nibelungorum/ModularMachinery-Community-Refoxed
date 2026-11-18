package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestState;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Accepts standard processing requests without changing the crafting CPU's accounting.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceCraftingMachine implements ICraftingMachine {
    private final PatternInterfaceBlockEntity host;
    private boolean accepting;

    public PatternInterfaceCraftingMachine(PatternInterfaceBlockEntity host) {
        this.host = host;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return PatternContainerGroup.nothing();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, Direction ejectionDirection) {
        if (accepting || host.isRemoved() || host.getLevel() == null || host.getLevel().isClientSide()) return false;
        if (!host.pendingPatterns().isEmpty()) return false;
        accepting = true;
        try {
            Level level = host.getLevel();
            if (!validInputs(pattern, inputs, level)) return false;
            PatternBatchDetails batch = pattern instanceof PatternBatchDetails details ? details : null;
            IPatternDetails original = batch == null ? pattern : batch.original();
            PatternBatchBinding binding = batch == null ? null : new PatternBatchBinding(batch.data().recipePoolId(),
                    batch.data().recipeId(), batch.data().recipeCraftsPerPattern());
            long parallelism = batch == null ? 1L
                    : Math.multiplyExact(batch.data().multiplier(), batch.data().recipeCraftsPerPattern());
            var boundRecipe = batch == null ? null : RecipeRegistry.getRecipe(binding.recipeId());
            if (batch != null && boundRecipe == null) return false;
            var families = AE2ResourceFamilies.all();
            PatternRequestState request = new PatternRequestState(inputs, families.size());
            List<RequestView<?>> views = new ArrayList<>();
            List<MachineCapability> capabilities = new ArrayList<>();
            for (var family : families) addRequestView(family, request, views, capabilities);
            for (BlockPos position : host.linkedControllerPositions().stream().sorted(BlockPos::compareTo).toList()) {
                if (!level.hasChunkAt(position)
                        || !(level.getBlockEntity(position) instanceof MachineControllerBlockEntity controller)) continue;
                var candidates = batch == null ? controller.recipesForMachine() : List.of(boundRecipe);
                for (var recipe : candidates) {
                    var effective = controller.effectivePatternRecipe(recipe);
                    if (effective == null) continue;
                    if (batch != null) {
                        if (!PatternBatchSupport.matches(binding, recipe, effective, original)) continue;
                    } else if (!controller.patternOutputsMatch(recipe, controller.runtimeSnapshot(),
                            outputs(original.getOutputs()))) continue;
                    // Only unit recipe outputs enter the runtime. Scaled AE amounts stay long throughout.
                    try (var reservation = controller.reserveExactPatternStart(host.getBlockPos(), recipe.id(),
                            effective.outputs(), parallelism, capabilities)) {
                        if (reservation.status() != PatternStartReservation.Status.RESERVED) continue;
                        if (batch != null && batch.data().multiplier() > 1L
                                && !PatternBatchSupport.supportsBatchInputs(reservation.effectiveRequirements())) continue;
                        if (reservation.commit(transaction -> {
                            for (var view : views) {
                                if (!view.accept(host, transaction)) throw new IllegalStateException("Pattern return inventory full");
                            }
                            if (host.isRemoved() || host.getLevel() != level
                                    || level.getBlockEntity(host.getBlockPos()) != host
                                    || level.getBlockEntity(position) != controller
                                    || !host.linkedControllerPositions().contains(position)
                                    || !host.pendingPatterns().isEmpty()) {
                                throw new IllegalStateException("Pattern source changed during admission");
                            }
                        })) return true;
                    }
                }
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            accepting = false;
        }
    }

    private <R> void addRequestView(AE2ResourceFamily<R> family, PatternRequestState request,
                                    List<RequestView<?>> views, List<MachineCapability> capabilities) {
        var storage = family.patternRequestView(request);
        views.add(new RequestView<>(family, storage));
        capabilities.add(family.capabilityView(host, storage, IOType.INPUT, false));
    }

    private record RequestView<R>(AE2ResourceFamily<R> family, PatternRequestResourceStorage<R> storage) {
        boolean accept(PatternInterfaceBlockEntity host, TransactionContext transaction) {
            return storage.accept(host.returnStorage(family), transaction);
        }
    }

    @Override
    public boolean acceptsPlans() {
        return true;
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
                    if (amount <= 0L || !AE2ResourceFamilies.supportsKey(entry.getKey())
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
        Map<AEKey, Long> totals = new HashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0L) return null;
            MachineOutput output = AE2ResourceFamilies.patternOutput(stack).orElse(null);
            if (output == null) return null;
            long limit = AE2ResourceFamilies.forKey(stack.what()).orElseThrow().adapter().maxPatternTotal();
            long previous = totals.getOrDefault(stack.what(), 0L);
            if (stack.amount() > limit - previous) return null;
            totals.put(stack.what(), previous + stack.amount());
            outputs.add(output);
        }
        return outputs.isEmpty() ? null : List.copyOf(outputs);
    }
}
