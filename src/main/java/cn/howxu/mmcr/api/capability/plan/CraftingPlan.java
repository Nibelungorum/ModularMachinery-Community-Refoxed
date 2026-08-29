package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Executes all prepared requirement operations as one atomic transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingPlan {
    private static final ExecutionStatus UNSPECIFIED_OPERATION_FAILURE = new ExecutionStatus(
            Identifier.fromNamespaceAndPath("mmcr", "crafting_plan_operation_failure"),
            StatusSeverity.FAILURE,
            Identifier.fromNamespaceAndPath("mmcr", "crafting_plan"),
            java.util.Map.of("reason", "operation_failed_without_status"));
    private final List<RequirementPlan> requirements;
    private final long parallelism;
    private final Map<Integer, RecipeModifier.IOType> directions;
    private @Nullable ExecutionStatus failure;

    public CraftingPlan(List<RequirementPlan> requirements, long parallelism) {
        this(requirements, parallelism, Map.of());
    }

    public CraftingPlan(List<RequirementPlan> requirements, long parallelism,
                        Map<Integer, RecipeModifier.IOType> directions) {
        if (requirements == null) throw new IllegalArgumentException("requirements must not be null");
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        this.requirements = List.copyOf(requirements);
        this.parallelism = parallelism;
        this.directions = Map.copyOf(directions == null ? Map.of() : directions);
        for (RequirementPlan requirement : this.requirements) {
            if (requirement.failure() != null) {
                failure = requirement.failure();
                break;
            }
        }
    }

    public boolean commit() {
        return commit(ignored -> { });
    }

    public boolean commit(Consumer<TransactionContext> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        if (failure != null) return false;
        try (Transaction transaction = Transaction.openRoot()) {
            if (!commitOperations(transaction, ignored -> true)) return false;
            transactionWrites.accept(transaction);
            transaction.commit();
            return true;
        }
    }

    public boolean commitInputs() {
        return commit(requirementIndex -> directions.get(requirementIndex) == RecipeModifier.IOType.INPUT);
    }

    public boolean commitOutputs() {
        return commit(requirementIndex -> directions.get(requirementIndex) == RecipeModifier.IOType.OUTPUT);
    }

    public boolean commitInputsExcept(java.util.Set<Integer> excludedRequirementIndexes) {
        java.util.Set<Integer> excluded = excludedRequirementIndexes == null ? java.util.Set.of() : excludedRequirementIndexes;
        return commit(requirementIndex -> directions.get(requirementIndex) == RecipeModifier.IOType.INPUT
                && !excluded.contains(requirementIndex));
    }

    public boolean hasOperations(int requirementIndex) {
        return requirements.stream().anyMatch(requirement -> requirement.requirementIndex() == requirementIndex
                && !requirement.operations().isEmpty());
    }

    public List<OutputSimulation> outputSimulations() {
        return requirements.stream()
                .map(RequirementPlan::outputSimulation)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean commit(IntPredicate selector) {
        if (failure != null) return false;
        try (Transaction transaction = Transaction.openRoot()) {
            if (!commitOperations(transaction, selector)) return false;
            transaction.commit();
            return true;
        }
    }

    private boolean commitOperations(TransactionContext transaction, IntPredicate selector) {
        for (RequirementPlan requirement : requirements) {
            if (!selector.test(requirement.requirementIndex())) continue;
            for (CapabilityOperation operation : requirement.operations()) {
                CapabilityResult result = operation.commit(transaction);
                if (result == null || !result.success()) {
                    if (failure == null) {
                        failure = result == null || result.status() == null
                                ? UNSPECIFIED_OPERATION_FAILURE : result.status();
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public long parallelism() {
        return parallelism;
    }

    public @Nullable ExecutionStatus failure() {
        return failure;
    }

    public List<RequirementPlan> requirements() {
        return requirements;
    }
}
