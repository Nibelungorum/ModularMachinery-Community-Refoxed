package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Prepared operations and failure state for one machine requirement.
 *
 * @param requirementIndex the requirement's position in the recipe
 * @param maxParallelism the maximum parallelism supported by this plan
 * @param operations the capability operations prepared by the handler
 * @param failure the failure status, or {@code null} when planning succeeded
 * @author howxu <dev@howxu.cn>
 */
public record RequirementPlan(
        int requirementIndex,
        int maxParallelism,
        List<CapabilityOperation> operations,
        @Nullable ExecutionStatus failure,
        @Nullable OperationFactory operationFactory) {
    public RequirementPlan(int requirementIndex, int maxParallelism,
                           List<CapabilityOperation> operations, @Nullable ExecutionStatus failure) {
        this(requirementIndex, maxParallelism, operations, failure, null);
    }

    public RequirementPlan {
        operations = List.copyOf(operations);
    }

    public boolean successful() {
        return failure == null;
    }

    public RequirementPlan materialize(int parallelism, PlanningReservations reservations) {
        if (failure != null || operationFactory == null) {
            return new RequirementPlan(requirementIndex, parallelism,
                    operations.stream().map(operation -> operation.forParallelism(parallelism)).toList(), failure);
        }
        OperationPlan result = operationFactory.create(parallelism, reservations);
        if (result == null) throw new IllegalStateException("Requirement operation factory returned null");
        return new RequirementPlan(requirementIndex, parallelism, result.operations(), result.failure());
    }

    @FunctionalInterface
    public interface OperationFactory {
        OperationPlan create(int parallelism, PlanningReservations reservations);
    }

    public record OperationPlan(List<CapabilityOperation> operations, @Nullable ExecutionStatus failure) {
        public OperationPlan {
            operations = List.copyOf(operations);
        }
    }
}
