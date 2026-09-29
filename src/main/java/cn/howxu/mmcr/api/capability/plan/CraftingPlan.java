package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.MMCR;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
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
            MMCR.id("crafting_plan_operation_failure"),
            StatusSeverity.FAILURE,
            MMCR.id("crafting_plan"),
            FailureOccurrence.at(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS,
                    MMCR.id("crafting_plan"),
                    FailurePhase.CAPABILITY_COMMIT, null, null,
                    Map.of("raw_reason_id", "operation_failed_without_status")));
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
        return commit(ignored -> true);
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

    /** Commits this plan into a caller-owned transaction. */
    public boolean commit(TransactionContext transaction) {
        if (failure != null) return false;
        return commitOperations(Objects.requireNonNull(transaction, "transaction"), ignored -> true);
    }

    public boolean commitInputs() {
        return commit(requirementIndex -> directions.get(requirementIndex) == RecipeModifier.IOType.INPUT);
    }

    /** Commits only input operations into a caller-owned transaction. */
    public boolean commitInputs(TransactionContext transaction) {
        if (failure != null) return false;
        return commitOperations(Objects.requireNonNull(transaction, "transaction"),
                requirementIndex -> directions.get(requirementIndex) == RecipeModifier.IOType.INPUT);
    }

    public boolean commitOutputs() {
        return commit(requirementIndex -> directions.get(requirementIndex) == RecipeModifier.IOType.OUTPUT);
    }

    public boolean commitInputsExcept(Set<Integer> excludedRequirementIndexes) {
        Set<Integer> excluded = excludedRequirementIndexes == null ? Set.of() : excludedRequirementIndexes;
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
        return commitOperations(selector);
    }

    private boolean commitOperations(IntPredicate selector) {
        for (RequirementPlan requirement : requirements) {
            if (!selector.test(requirement.requirementIndex())) continue;
            for (CapabilityOperation operation : requirement.operations()) {
                if (!operation.supportsNativeExecution()) {
                    if (failure == null) failure = UNSPECIFIED_OPERATION_FAILURE;
                    return false;
                }
                CapabilityResult result = operation.commit();
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
