package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeIoDeclaration;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import java.util.function.Predicate;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entry point for capability planning from a direct tick behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineIoPlan {
    private final CapabilitySnapshot capabilitySnapshot;
    private List<MachineRequirement> requirements = List.of();
    private Map<Integer, OutputPolicy> outputPolicies = Map.of();
    private @Nullable PlanningResult simulation;
    private boolean consumed;

    public record Simulation(boolean inputsSatisfied, boolean energySatisfied,
                             List<OutputSimulation> outputs, @Nullable ExecutionStatus failure) {
        public Simulation {
            outputs = List.copyOf(outputs == null ? List.of() : outputs);
        }
    }

    public record CommitResult(boolean successful, @Nullable ExecutionStatus failure) {
    }

    public MachineIoPlan(CapabilitySnapshot capabilitySnapshot) {
        this.capabilitySnapshot = Objects.requireNonNull(capabilitySnapshot, "capabilitySnapshot");
    }

    public MachineIoView view() {
        return new MachineIoView(capabilitySnapshot);
    }

    public MachineIoPlan addInput(RecipeIoDeclaration io) {
        if (io.io() != IOType.INPUT) throw new IllegalArgumentException("Requirement direction must be INPUT");
        return addRequirement(RecipeIoValidation.decodeIoRequirement(io), IOType.INPUT, null);
    }

    public MachineIoPlan addOutput(RecipeIoDeclaration io) {
        return addOutput(io, OutputPolicy.REQUIRE_FULL);
    }

    public MachineIoPlan addOutput(RecipeIoDeclaration io, OutputPolicy policy) {
        if (io.io() != IOType.OUTPUT) throw new IllegalArgumentException("Requirement direction must be OUTPUT");
        return addRequirement(RecipeIoValidation.decodeIoRequirement(io), IOType.OUTPUT,
                policy == null ? OutputPolicy.REQUIRE_FULL : policy);
    }

    public MachineIoPlan add(RecipeIoDeclaration io) {
        return io.io() == IOType.INPUT
                ? addInput(io) : addOutput(io, OutputPolicy.REQUIRE_FULL);
    }

    private MachineIoPlan addRequirement(MachineRequirement requirement, RecipeModifier.IOType expectedIo,
                                         @Nullable OutputPolicy outputPolicy) {
        Objects.requireNonNull(requirement, "requirement");
        if (requirement.io() != expectedIo) {
            throw new IllegalArgumentException("Requirement direction must be " + expectedIo);
        }
        if (consumed) throw new IllegalStateException("Machine I/O plan has already been consumed");
        int insertionIndex = requirements.size();
        if (expectedIo == RecipeModifier.IOType.INPUT) {
            for (int index = 0; index < requirements.size(); index++) {
                if (requirements.get(index).io() == RecipeModifier.IOType.OUTPUT) {
                    insertionIndex = index;
                    break;
                }
            }
        }
        List<MachineRequirement> next = new ArrayList<>(requirements);
        next.add(insertionIndex, MachineRequirement.copyOf(requirement));
        requirements = List.copyOf(next);
        int finalInsertionIndex = insertionIndex;
        Map<Integer, OutputPolicy> nextPolicies = new LinkedHashMap<>();
        outputPolicies.forEach((index, policy) -> nextPolicies.put(
                index >= finalInsertionIndex ? index + 1 : index, policy));
        if (outputPolicy != null) {
            nextPolicies.put(finalInsertionIndex, outputPolicy);
        }
        outputPolicies = Map.copyOf(nextPolicies);
        simulation = null;
        return this;
    }

    public List<MachineRequirement> requirements() {
        return MachineRequirement.copyList(requirements);
    }

    public Simulation simulate() {
        if (consumed) throw new IllegalStateException("Machine I/O plan has already been consumed");
        CraftingContext context = new CraftingContext(capabilitySnapshot);
        simulation = context.planRequirements(requirements, 1, outputPolicies);
        return simulationView(simulation);
    }

    public CommitResult commit() {
        return commit(ignored -> { });
    }

    public CommitResult commit(Consumer<cn.howxu.mmcr.api.data.DataStorage.Transaction> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        if (consumed) return new CommitResult(false, null);
        consumed = true;
        if (simulation == null || !simulation.successful() || simulation.plan() == null) {
            return new CommitResult(false, simulation == null ? null : simulation.failure());
        }
        try {
            cn.howxu.mmcr.api.data.DataStorage.Transaction transaction =
                    cn.howxu.mmcr.api.data.DataStorage.Transaction.create();
            transactionWrites.accept(transaction);
            CraftingPlan plan = simulation.plan();
            boolean successful = plan.commit();
            if (successful) transaction.commit();
            return new CommitResult(successful, successful ? null : plan.failure());
        } finally {
            consumed = true;
        }
    }

    public CommitResult commitData(Consumer<DataStorage.Transaction> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        return commit(transaction -> transactionWrites.accept(DataStorage.Transaction.view(transaction)));
    }

    public List<OutputSimulation> outputSimulations() {
        return (simulation == null ? simulate() : simulationView(simulation)).outputs();
    }

    public boolean inputsSatisfied() {
        return (simulation == null ? simulate() : simulationView(simulation)).inputsSatisfied();
    }

    public boolean energySatisfied() {
        return (simulation == null ? simulate() : simulationView(simulation)).energySatisfied();
    }

    private Simulation simulationView(PlanningResult result) {
        Integer failureIndex = result.failureRequirementIndex();
        boolean inputsSatisfied = result.failure() == null || !matchesFailure(failureIndex,
                requirement -> requirement.io() == RecipeModifier.IOType.INPUT
                        && !(requirement instanceof EnergyRequirement));
        boolean energySatisfied = result.failure() == null || !matchesFailure(failureIndex,
                requirement -> requirement instanceof EnergyRequirement
                        && requirement.io() == RecipeModifier.IOType.INPUT);
        return new Simulation(inputsSatisfied, energySatisfied, result.outputSimulations(), result.failure());
    }

    private boolean matchesFailure(@Nullable Integer failureIndex,
                                    Predicate<MachineRequirement> predicate) {
        return failureIndex != null && failureIndex >= 0 && failureIndex < requirements.size()
                && predicate.test(requirements.get(failureIndex));
    }
}
