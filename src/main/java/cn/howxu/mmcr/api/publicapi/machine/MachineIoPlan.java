package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Entry point for capability planning from a direct tick behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineIoPlan {
    private final CapabilitySnapshot capabilitySnapshot;
    private List<MachineRequirement> requirements = List.of();
    private @Nullable PlanningResult simulation;

    public MachineIoPlan(CapabilitySnapshot capabilitySnapshot) {
        this.capabilitySnapshot = Objects.requireNonNull(capabilitySnapshot, "capabilitySnapshot");
    }

    public MachineIoView view() {
        return new MachineIoView(capabilitySnapshot);
    }

    public MachineIoPlan add(MachineRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        List<MachineRequirement> next = new ArrayList<>(requirements);
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            int firstOutput = 0;
            while (firstOutput < next.size() && next.get(firstOutput).io() == RecipeModifier.IOType.INPUT) {
                firstOutput++;
            }
            next.add(firstOutput, requirement);
        } else {
            next.add(requirement);
        }
        requirements = List.copyOf(next);
        simulation = null;
        return this;
    }

    public List<MachineRequirement> requirements() {
        return requirements;
    }

    public PlanningResult simulate() {
        List<Integer> indexes = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) indexes.add(index);
        simulation = new RequirementPlanner().plan(requirements, capabilitySnapshot.capabilities(),
                new PlanningContext(1, 0), indexes);
        return simulation;
    }

    public boolean commit() {
        PlanningResult result = simulation == null ? simulate() : simulation;
        return result.successful() && result.plan().commit();
    }

    public List<OutputSimulation> outputSimulations() {
        return (simulation == null ? simulate() : simulation).outputSimulations();
    }

    public boolean inputsSatisfied() {
        return satisfied(requirement -> requirement.io() == RecipeModifier.IOType.INPUT
                && !(requirement instanceof EnergyRequirement));
    }

    public boolean energySatisfied() {
        return satisfied(requirement -> requirement instanceof EnergyRequirement
                && requirement.io() == RecipeModifier.IOType.INPUT);
    }

    private boolean satisfied(Predicate<MachineRequirement> predicate) {
        PlanningResult result = simulation == null ? simulate() : simulation;
        Integer failureIndex = result.failureRequirementIndex();
        return failureIndex == null || failureIndex < 0
                || failureIndex >= requirements.size() || !predicate.test(requirements.get(failureIndex));
    }
}
