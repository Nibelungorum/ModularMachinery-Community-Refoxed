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
 * @param operationFactory creates operations only after the final parallelism is selected
 * @param reservationFactory simulates shared-resource reservations without preparing operations
 * @param preparedParallelism the parallelism used when direct operations were prepared
 * @author howxu <dev@howxu.cn>
 */
public record RequirementPlan(
        int requirementIndex,
        int maxParallelism,
        List<CapabilityOperation> operations,
        @Nullable ExecutionStatus failure,
        @Nullable OperationFactory operationFactory,
        @Nullable ReservationFactory reservationFactory,
        int preparedParallelism) {
    public RequirementPlan(int requirementIndex, int maxParallelism,
                           List<CapabilityOperation> operations, @Nullable ExecutionStatus failure) {
        this(requirementIndex, maxParallelism, operations, failure, null, null, 0);
    }

    public RequirementPlan(int requirementIndex, int maxParallelism,
                           List<CapabilityOperation> operations, @Nullable ExecutionStatus failure,
                           @Nullable OperationFactory operationFactory) {
        this(requirementIndex, maxParallelism, operations, failure, operationFactory, null, 0);
    }

    public RequirementPlan(int requirementIndex, int maxParallelism,
                           List<CapabilityOperation> operations, @Nullable ExecutionStatus failure,
                           @Nullable OperationFactory operationFactory,
                           @Nullable ReservationFactory reservationFactory) {
        this(requirementIndex, maxParallelism, operations, failure, operationFactory, reservationFactory, 0);
    }

    public RequirementPlan {
        operations = List.copyOf(operations);
    }

    public boolean successful() {
        return failure == null;
    }

    public RequirementPlan preparedAt(int parallelism) {
        return new RequirementPlan(requirementIndex, maxParallelism, operations, failure, operationFactory,
                reservationFactory, parallelism);
    }

    public @Nullable ExecutionStatus reserve(int parallelism, PlanningReservations reservations) {
        return reservationFactory == null ? null : reservationFactory.reserve(parallelism, reservations);
    }

    public RequirementPlan materialize(int parallelism, PlanningReservations reservations,
                                       ExecutionStatus unsafeOperationFailure) {
        if (failure != null || operationFactory == null) {
            if (preparedParallelism > 0 && parallelism != preparedParallelism) {
                List<CapabilityOperation> adapted = new java.util.ArrayList<>(operations.size());
                for (CapabilityOperation operation : operations) {
                    CapabilityOperation scaled = operation.forParallelism(parallelism);
                    if (scaled == null) {
                        return new RequirementPlan(requirementIndex, parallelism, List.of(), unsafeOperationFailure);
                    }
                    adapted.add(scaled);
                }
                return new RequirementPlan(requirementIndex, parallelism, adapted, failure);
            }
            return new RequirementPlan(requirementIndex, parallelism, operations, failure);
        }
        OperationPlan result = operationFactory.create(parallelism, reservations);
        if (result == null) throw new IllegalStateException("Requirement operation factory returned null");
        return new RequirementPlan(requirementIndex, parallelism, result.operations(), result.failure());
    }

    @FunctionalInterface
    public interface OperationFactory {
        OperationPlan create(int parallelism, PlanningReservations reservations);
    }

    @FunctionalInterface
    public interface ReservationFactory {
        @Nullable ExecutionStatus reserve(int parallelism, PlanningReservations reservations);
    }

    public record OperationPlan(List<CapabilityOperation> operations, @Nullable ExecutionStatus failure) {
        public OperationPlan {
            operations = List.copyOf(operations);
        }
    }
}
