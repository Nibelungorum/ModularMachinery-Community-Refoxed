package cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.internal.item.PatternBatchData;
import cn.howxu.mmcr.registry.ModDataComponents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A bound, self-describing virtual definition; never modifies the physical pattern.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchDetails implements IPatternDetails {
    private final IPatternDetails original;
    private final PatternBatchData data;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    public PatternBatchDetails(IPatternDetails original, PatternBatchData data) {
        this.original = Objects.requireNonNull(original, "original");
        this.data = Objects.requireNonNull(data, "data");
        if (data.version() != PatternBatchData.VERSION) {
            throw new IllegalArgumentException("Unsupported pattern batch version: " + data.version());
        }
        if (original instanceof PatternBatchDetails
                || original.getDefinition().get(ModDataComponents.PATTERN_BATCH.get()) != null) {
            throw new IllegalArgumentException("Nested pattern batches are not supported");
        }
        Math.multiplyExact(data.recipeCraftsPerPattern(), data.multiplier());
        var stack = original.getDefinition().toStack();
        stack.set(ModDataComponents.PATTERN_BATCH.get(), data);
        definition = Objects.requireNonNull(AEItemKey.of(stack));

        IInput[] originalInputs = original.getInputs();
        inputs = new IInput[originalInputs.length];
        Map<AEKey, Long> inputTotals = new HashMap<>();
        for (int i = 0; i < inputs.length; i++) {
            IInput input = originalInputs[i];
            long multiplier = Math.multiplyExact(input.getMultiplier(), data.multiplier());
            if (multiplier < 1) throw new IllegalArgumentException("Pattern input multiplier must be positive");
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible.amount() < 1) throw new IllegalArgumentException("Pattern input amount must be positive");
                inputTotals.merge(possible.what(), Math.multiplyExact(possible.amount(), multiplier), Math::addExact);
            }
            inputs[i] = new BatchInput(input, multiplier);
        }
        Map<AEKey, Long> outputTotals = new HashMap<>();
        outputs = original.getOutputs().stream().map(output -> {
            long amount = Math.multiplyExact(output.amount(), data.multiplier());
            if (amount < 1) throw new IllegalArgumentException("Pattern output amount must be positive");
            outputTotals.merge(output.what(), amount, Math::addExact);
            return new GenericStack(output.what(), amount);
        }).toList();
    }

    public IPatternDetails original() {
        return original;
    }

    public PatternBatchData data() {
        return data;
    }

    public PatternBatchDetails unitPattern() {
        return scaled(1);
    }

    /** Returns an absolute multiplier, not a multiplier relative to this batch. */
    public PatternBatchDetails scaled(long multiplier) {
        if (multiplier == data.multiplier()) return this;
        return new PatternBatchDetails(original, new PatternBatchData(data.version(), data.recipePoolId(),
                data.recipeId(), data.recipeCraftsPerPattern(), multiplier));
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PatternBatchDetails batch && definition.equals(batch.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    /** @author howxu <dev@howxu.cn> */
    private record BatchInput(IInput original, long multiplier) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return original.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return original.isValid(input, level);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return original.getRemainingKey(template);
        }
    }
}
