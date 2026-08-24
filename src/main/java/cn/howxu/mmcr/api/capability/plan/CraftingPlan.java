package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;

/**
 * Executes all prepared requirement operations as one atomic transaction.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingPlan {
    private final List<RequirementPlan> requirements;
    private final int parallelism;
    private @Nullable ExecutionStatus failure;

    public CraftingPlan(List<RequirementPlan> requirements, int parallelism) {
        if (requirements == null) throw new IllegalArgumentException("requirements must not be null");
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        this.requirements = List.copyOf(requirements);
        this.parallelism = parallelism;
        for (RequirementPlan requirement : this.requirements) {
            if (requirement.failure() != null) {
                failure = requirement.failure();
                break;
            }
        }
    }

    public boolean commit() {
        if (failure != null) return false;
        try (Transaction transaction = Transaction.openRoot()) {
            for (RequirementPlan requirement : requirements) {
                for (CapabilityOperation operation : requirement.operations()) {
                    CapabilityResult result = operation.commit(transaction);
                    if (result == null || !result.success()) {
                        if (failure == null && result != null) failure = result.status();
                        return false;
                    }
                }
            }
            transaction.commit();
            return true;
        }
    }

    public int parallelism() {
        return parallelism;
    }

    public @Nullable ExecutionStatus failure() {
        return failure;
    }

    public List<RequirementPlan> requirements() {
        return requirements;
    }
}
