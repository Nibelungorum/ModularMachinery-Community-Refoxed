package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable execution rules captured on the main thread, without live capability bindings.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CompiledRecipeProgram(ResourceLocation recipeId, int duration, long parallelism,
                                    VersionStamp versions, Stage start, Stage tick, Stage finish,
                                    boolean requiresMainThreadPreparation) {
    public CompiledRecipeProgram {
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(tick, "tick");
        Objects.requireNonNull(finish, "finish");
        if (duration <= 0 || parallelism <= 0L) {
            throw new IllegalArgumentException("duration and parallelism must be positive");
        }
    }

    /** Version fences for the catalog, effective recipe and live installation domain. */
    public record VersionStamp(long catalog, long structure, long capability, long effective,
                               long domainId, long domainGeneration) {}

    /** An execution identity scoped to one level's coordinator. */
    public record ExecutionIdentity(long controllerInstance, long lifecycleEpoch, String laneId,
                                    long executionId) {
        public ExecutionIdentity {
            Objects.requireNonNull(laneId, "laneId");
        }
    }

    /** Policies are indexed by the original stage requirement index, including fallback entries. */
    public record Stage(List<AsyncRequirementPlanner.Requirement> requirements,
                        List<Integer> fallbackIndices, List<OutputPolicy> outputPolicies) {
        public Stage {
            requirements = List.copyOf(requirements);
            fallbackIndices = List.copyOf(fallbackIndices);
            outputPolicies = List.copyOf(outputPolicies);
            Set<Integer> indices = new HashSet<>();
            for (AsyncRequirementPlanner.Requirement requirement : requirements) {
                if (!indices.add(requirement.index())) {
                    throw new IllegalArgumentException("duplicate requirement index");
                }
            }
            for (int index : fallbackIndices) {
                if (index < 0 || !indices.add(index)) {
                    throw new IllegalArgumentException("invalid or duplicate fallback index");
                }
            }
        }

        public static Stage empty() {
            return new Stage(List.of(), List.of(), List.of());
        }

        public boolean requiresCompatibility() {
            return !fallbackIndices.isEmpty();
        }
    }

    /** Captured restart rules; admission still selects a fresh legal parallelism at the boundary. */
    public record RestartRule(ResourceLocation recipeId, VersionStamp versions, long maximumParallelism,
                              Stage start, Stage tick, Stage finish) {}
}
