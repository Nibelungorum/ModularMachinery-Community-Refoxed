package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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

    public MachineIoPlan addInput(MachineRequirement requirement) {
        return addRequirement(requirement, RecipeModifier.IOType.INPUT, null);
    }

    public MachineIoPlan addOutput(MachineRequirement requirement, OutputPolicy policy) {
        return addRequirement(requirement, RecipeModifier.IOType.OUTPUT,
                Objects.requireNonNull(policy, "policy"));
    }

    public MachineIoPlan add(MachineRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return requirement.io() == RecipeModifier.IOType.INPUT
                ? addInput(requirement) : addOutput(requirement, OutputPolicy.REQUIRE_FULL);
    }

    private MachineIoPlan addRequirement(MachineRequirement requirement, RecipeModifier.IOType expectedIo,
                                         @Nullable OutputPolicy outputPolicy) {
        Objects.requireNonNull(requirement, "requirement");
        if (requirement.io() != expectedIo) {
            throw new IllegalArgumentException("Requirement direction must be " + expectedIo);
        }
        if (consumed) throw new IllegalStateException("Machine I/O plan has already been consumed");
        List<MachineRequirement> next = new ArrayList<>(requirements);
        next.add(requirement);
        requirements = List.copyOf(next);
        if (outputPolicy != null) {
            Map<Integer, OutputPolicy> nextPolicies = new LinkedHashMap<>(outputPolicies);
            nextPolicies.put(next.size() - 1, outputPolicy);
            outputPolicies = Map.copyOf(nextPolicies);
        }
        simulation = null;
        return this;
    }

    public List<MachineRequirement> requirements() {
        return requirements;
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

    public CommitResult commit(Consumer<TransactionContext> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        if (consumed) return new CommitResult(false, null);
        consumed = true;
        if (simulation == null || !simulation.successful() || simulation.plan() == null) {
            return new CommitResult(false, simulation == null ? null : simulation.failure());
        }
        try {
            CraftingPlan plan = simulation.plan();
            boolean successful = plan.commit(transactionWrites);
            return new CommitResult(successful, successful ? null : plan.failure());
        } finally {
            consumed = true;
        }
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
                                   java.util.function.Predicate<MachineRequirement> predicate) {
        return failureIndex != null && failureIndex >= 0 && failureIndex < requirements.size()
                && predicate.test(requirements.get(failureIndex));
    }
}
