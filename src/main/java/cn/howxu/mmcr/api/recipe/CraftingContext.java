package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Plans recipe capability operations from an immutable capability snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingContext {
    private List<MachineCapability> capabilities;
    private List<RecipeModifier> modifiers;

    public CraftingContext(CapabilitySnapshot snapshot) {
        this(snapshot, List.of());
    }

    public CraftingContext(CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        capabilities = snapshot.capabilities();
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public PlanningResult planInputs(MachineRecipe recipe, long parallelism) {
        return planInputs(recipe, parallelism, Set.of(), Set.of());
    }

    /**
     * Captures native capability values and prepares a worker-safe requirement descriptor.
     * This method must be called on the server thread when async-capable capabilities are present.
     */
    public AsyncRequirementPlanner.PreparedPlan planAsync(List<MachineRequirement> requirements, long parallelism) {
        if (requirements == null || parallelism <= 0L) {
            throw new IllegalArgumentException("requirements must not be null and parallelism must be positive");
        }
        return prepareAsyncPlan(requirements, parallelism, captureAsyncCapabilities());
    }

    public List<AsyncRequirementPlanner.Capability> captureAsyncCapabilities() {
        return captureAsyncCapabilities(null);
    }

    public List<AsyncRequirementPlanner.Capability> captureAsyncCapabilities(@Nullable Set<ResourceLocation> capabilityIds) {
        List<AsyncRequirementPlanner.Capability> asyncCapabilities = new ArrayList<>();
        boolean chemicalFallback = capabilities.stream().anyMatch(capability ->
                capability.type().id().equals(MekanismRecipeTypes.CHEMICAL)
                        && capability.directions().supports(IOType.INPUT)
                        && capability.facet(AsyncPlanningFacet.class).isEmpty());
        for (MachineCapability capability : capabilities) {
            if (capabilityIds != null && !capabilityIds.contains(capability.type().id())) continue;
            if (chemicalFallback && capability.type().id().equals(MekanismRecipeTypes.CHEMICAL)) continue;
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElse(null);
            if (facet == null) continue;
            AsyncCapabilitySnapshot snapshot = facet.captureSnapshot();
            AsyncCapabilityPlanner planner = facet.workerPlanner();
            asyncCapabilities.add(new AsyncRequirementPlanner.Capability(planner, snapshot,
                    capability.directions().values()));
        }
        return List.copyOf(asyncCapabilities);
    }

    public static AsyncRequirementPlanner.PreparedPlan prepareAsyncPlan(List<MachineRequirement> requirements,
            long parallelism, List<AsyncRequirementPlanner.Capability> asyncCapabilities) {
        if (requirements == null || parallelism <= 0L) {
            throw new IllegalArgumentException("requirements must not be null and parallelism must be positive");
        }
        List<AsyncRequirementPlanner.Requirement> preparedRequirements = new ArrayList<>();
        List<Integer> fallback = new ArrayList<>();
        for (int index = 0; index < requirements.size(); index++) {
            AsyncRequirementPlanner.Requirement prepared = prepareAsyncRequirement(index, requirements.get(index),
                    parallelism, asyncCapabilities);
            if (prepared == null) fallback.add(index);
            else preparedRequirements.add(prepared);
        }
        return new AsyncRequirementPlanner.PreparedPlan(preparedRequirements, asyncCapabilities, fallback);
    }

    public PlanningResult planInputs(MachineRecipe recipe, long parallelism,
                                     Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return plan(startRequirements(recipe), parallelism, RecipeModifier.IOType.INPUT,
                consumedAtStart == null ? Set.of() : consumedAtStart,
                retainedInputs == null ? Set.of() : retainedInputs, Map.of());
    }

    public PlanningResult planInputs(List<MachineRequirement> requirements, long parallelism,
                                     Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return plan(requirements, parallelism, RecipeModifier.IOType.INPUT,
                consumedAtStart == null ? Set.of() : consumedAtStart,
                retainedInputs == null ? Set.of() : retainedInputs, Map.of());
    }

    public PlanningResult planOutputs(MachineRecipe recipe, long parallelism) {
        return plan(recipe, parallelism, RecipeModifier.IOType.OUTPUT, Set.of(), Set.of());
    }

    public PlanningResult planOutputs(List<MachineOutput> outputs, long parallelism) {
        if (outputs == null) throw new IllegalArgumentException("outputs must not be null");
        return planSelected(outputRequirements(outputs), parallelism, partialOutputPolicies(outputs.size()), indexes(outputs.size()));
    }

    public PlanningResult planOutputs(MachineRecipe recipe, List<MachineOutput> outputs, long parallelism) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        IndexedRequirements replacement = replaceOutputs(recipe.runtimeRequirements(modifiers), outputs);
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        for (int index = 0; index < replacement.requirements().size(); index++) {
            MachineRequirement requirement = replacement.requirements().get(index);
            if (requirement.io() != RecipeModifier.IOType.OUTPUT) continue;
            requirements.add(requirement);
            requirementIndexes.add(replacement.indexes().get(index));
        }
        return planSelected(requirements, parallelism,
                outputPoliciesForIndexes(requirementIndexes, recipe.allowPartialOutputs()), requirementIndexes);
    }

    private static List<MachineRequirement> outputRequirements(List<MachineOutput> outputs) {
        if (outputs == null) throw new IllegalArgumentException("outputs must not be null");
        List<MachineRequirement> requirements = new ArrayList<>(outputs.size());
        for (MachineOutput output : outputs) {
            requirements.add(outputRequirement(output, null));
        }
        return requirements;
    }

    private static List<Integer> indexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) indexes.add(index);
        return indexes;
    }

    public CraftingPlan planStart(MachineRecipe recipe, long requestedParallelism) {
        PlanningResult result = planStartResult(recipe, requestedParallelism);
        return result.successful() ? result.plan() : null;
    }

    public PlanningResult planStartResult(MachineRecipe recipe, long requestedParallelism) {
        List<MachineRequirement> allRequirements = recipe.runtimeRequirements(modifiers);
        List<MachineRequirement> requirements = startRequirements(allRequirements);
        PlanningResult result = plan(requirements, requestedParallelism, null, Set.of(), Set.of(),
                partialOutputPolicies(requirements, recipe.allowPartialOutputs()));
        if (!result.successful()) return result;
        List<RecipeModifier> durationModifiers = new ArrayList<>(recipe.modifiers());
        durationModifiers.addAll(modifiers);
        int duration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.round(
                IntegrationTypeHelper.applyDuration(durationModifiers, recipe.getRecipeTotalTickTime()))));
        PlanningResult prefetchFailure = validatePrefetch(allRequirements, duration, result.plan().parallelism());
        return prefetchFailure == null ? result : prefetchFailure;
    }

    private List<MachineRequirement> startRequirements(MachineRecipe recipe) {
        return startRequirements(recipe.runtimeRequirements(modifiers));
    }

    private List<MachineRequirement> startRequirements(List<MachineRequirement> requirements) {
        boolean hasPrefetch = capabilities.stream()
                .anyMatch(capability -> capability.facet(RecipeEnergyPrefetchFacet.class).isPresent());
        if (!hasPrefetch) return requirements;
        return requirements.stream().filter(requirement -> !(requirement instanceof EnergyRequirement energy
                && energy.io() == RecipeModifier.IOType.INPUT)).toList();
    }

    private @Nullable PlanningResult validatePrefetch(List<MachineRequirement> requirements, int duration,
                                                       long parallelism) {
        List<RecipeEnergyPrefetchFacet> facets = capabilities.stream()
                .map(capability -> capability.facet(RecipeEnergyPrefetchFacet.class).orElse(null))
                .filter(Objects::nonNull).toList();
        if (facets.isEmpty()) return null;
        int firstEnergyIndex = -1;
        for (int index = 0; index < requirements.size(); index++) {
            if (requirements.get(index) instanceof EnergyRequirement energy
                    && energy.io() == RecipeModifier.IOType.INPUT) {
                firstEnergyIndex = index;
                break;
            }
        }
        if (firstEnergyIndex < 0) return null;
        int energyIndex = firstEnergyIndex;
        List<Map.Entry<RecipeEnergyPrefetchFacet, RecipeEnergyPrefetchFacet.PrefetchPlan>> planned = new ArrayList<>();
        try {
            Set<String> reservationKeys = new HashSet<>();
            if (facets.stream().anyMatch(facet -> facet.reservationKey() == null
                    || facet.reservationKey().isBlank() || !reservationKeys.add(facet.reservationKey()))) {
                return prefetchFailure(requirements, energyIndex);
            }
            for (int index = 0; index < requirements.size(); index++) {
                MachineRequirement requirement = requirements.get(index);
                if (!(requirement instanceof EnergyRequirement energy)
                        || energy.io() != RecipeModifier.IOType.INPUT) continue;
                long remaining = scaled(scaled(energy.fePerTick(), duration), parallelism);
                for (RecipeEnergyPrefetchFacet facet : facets) {
                    if (remaining <= 0L) break;
                    Optional<RecipeEnergyPrefetchFacet.PrefetchPlan> candidate = facet.planPrefetch(remaining);
                    if (candidate.isEmpty()) continue;
                    RecipeEnergyPrefetchFacet.PrefetchPlan plan = candidate.get();
                    if (plan.amount() <= 0L || plan.amount() > remaining) return prefetchFailure(requirements, index);
                    planned.add(Map.entry(facet, plan));
                    remaining -= plan.amount();
                }
                if (remaining > 0L) return prefetchFailure(requirements, index);
            }
            return null;
        } finally {
            for (int index = planned.size() - 1; index >= 0; index--) {
                var entry = planned.get(index);
                entry.getKey().restoreReservation(entry.getValue().amount());
            }
        }
    }

    private static PlanningResult prefetchFailure(List<MachineRequirement> requirements, int requirementIndex) {
        int index = Math.max(0, Math.min(requirementIndex, requirements.size() - 1));
        MachineRequirement requirement = requirements.get(index);
        return new PlanningResult(null,
                RequirementHandlerSupport.blocked(requirement, BuiltinFailureReasons.MISSING_INPUT),
                List.of(), index);
    }

    public PlanningResult planStartRequirements(List<MachineRequirement> requirements, long requestedParallelism,
                                                 boolean allowPartialOutputs) {
        return planRequirements(requirements, requestedParallelism,
                partialOutputPolicies(requirements, allowPartialOutputs));
    }

    public PlanningResult planRequirements(List<MachineRequirement> requirements, long parallelism,
                                           Map<Integer, OutputPolicy> outputPolicies) {
        return plan(requirements, parallelism, null, Set.of(), Set.of(), outputPolicies);
    }

    public PlanningResult planInputRequirements(List<MachineRequirement> requirements, long parallelism,
                                                 Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return planInputs(requirements, parallelism, consumedAtStart, retainedInputs);
    }

    public PlanningResult planOutputRequirements(List<MachineRequirement> requirements, long parallelism,
                                                  boolean allowPartialOutputs) {
        return plan(requirements, parallelism, RecipeModifier.IOType.OUTPUT, Set.of(), Set.of(),
                partialOutputPolicies(requirements, allowPartialOutputs));
    }

    public PlanningResult planOutputRequirements(List<MachineRequirement> requirements, List<MachineOutput> outputs,
                                                  long parallelism, boolean allowPartialOutputs) {
        IndexedRequirements replacement = replaceOutputs(requirements, outputs);
        return planSelected(replacement.requirements(), replacement.indexes(), parallelism,
                RecipeModifier.IOType.OUTPUT,
                outputPoliciesForIndexes(replacement.indexes(), allowPartialOutputs));
    }

    public void setModifiers(List<RecipeModifier> modifiers) {
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    void resetFor(CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        capabilities = snapshot.capabilities();
        setModifiers(modifiers);
    }

    private PlanningResult plan(MachineRecipe recipe, long parallelism, RecipeModifier.IOType direction,
                                Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        List<MachineRequirement> recipeRequirements = recipe.runtimeRequirements(modifiers);
        return plan(recipeRequirements, parallelism, direction, consumedAtStart, retainedInputs,
                partialOutputPolicies(recipeRequirements, recipe.allowPartialOutputs()));
    }

    private static AsyncRequirementPlanner.Requirement prepareAsyncRequirement(int index,
                                                                                MachineRequirement requirement,
                                                                                long parallelism,
                                                                                List<AsyncRequirementPlanner.Capability> capabilities) {
        if (requirement instanceof ItemRequirement item) {
            long amount = item.io() == RecipeModifier.IOType.INPUT
                    ? scaled(item.count(), parallelism) : scaled(item.stack(null).getCount(), parallelism);
            if (amount <= 0L || (item.io() == RecipeModifier.IOType.INPUT
                    && (item.item() == null || item.consumeChance() < 1F))) return null;
            List<AsyncCapabilityRequest> requests = new ArrayList<>();
            if (item.io() == RecipeModifier.IOType.INPUT) {
                for (AsyncResourceValue value : resources(capabilities, item.type().id(), IOType.INPUT)) {
                    ItemStack resource;
                    try {
                        resource = NativeAsyncResourceValues.item(value);
                    } catch (IllegalArgumentException exception) {
                        continue;
                    }
                    ItemStack stack = resource.copyWithCount(1);
                    if (item.item().test(stack) && item.components().matches(stack)) {
                        requests.add(resourceRequest(item.type().id(), parallelism, value, amount, false));
                    }
                }
            } else if (!item.stack(null).isEmpty()) {
                requests.add(resourceRequest(item.type().id(), parallelism,
                        NativeAsyncResourceValues.item(item.stack(null)), amount, true));
            }
            return requests.isEmpty() ? null : new AsyncRequirementPlanner.Requirement(index, amount,
                    IOType.valueOf(item.io().name()), requests);
        }
        if (requirement instanceof FluidRequirement fluid) {
            long amount = fluid.io() == RecipeModifier.IOType.INPUT
                    ? scaled(fluid.amount(), parallelism) : scaled(fluid.stack().getAmount(), parallelism);
            if (amount <= 0L || (fluid.io() == RecipeModifier.IOType.INPUT
                    && (fluid.fluid() == null || fluid.consumeChance() < 1F))) return null;
            List<AsyncCapabilityRequest> requests = new ArrayList<>();
            if (fluid.io() == RecipeModifier.IOType.INPUT) {
                for (AsyncResourceValue value : resources(capabilities, fluid.type().id(), IOType.INPUT)) {
                    FluidStack resource;
                    try {
                        resource = NativeAsyncResourceValues.fluid(value);
                    } catch (IllegalArgumentException exception) {
                        continue;
                    }
                    if (fluid.fluid().test(resource.copyWithAmount(1))) {
                        requests.add(resourceRequest(fluid.type().id(), parallelism, value, amount, false));
                    }
                }
            } else if (!fluid.stack().isEmpty()) {
                requests.add(resourceRequest(fluid.type().id(), parallelism,
                        NativeAsyncResourceValues.fluid(fluid.stack()), amount, true));
            }
            return requests.isEmpty() ? null : new AsyncRequirementPlanner.Requirement(index, amount,
                    IOType.valueOf(fluid.io().name()), requests);
        }
        if (requirement instanceof LoadedChemicalRequirement chemical) {
            if (chemical.io() != RecipeModifier.IOType.INPUT || chemical.consumeChance() < 1F
                    || chemical.ingredient().kind() != ChemicalIngredient.Kind.CHEMICAL) return null;
            long amount = scaled(chemical.ingredient().amount(), parallelism);
            if (amount <= 0L) return null;
            List<AsyncCapabilityRequest> requests = new ArrayList<>();
            for (AsyncResourceValue value : resources(capabilities, chemical.type().id(), IOType.INPUT)) {
                if (chemical.ingredient().id().equals(value.resourceId())) {
                    requests.add(resourceRequest(chemical.type().id(), parallelism, value, amount, false));
                }
            }
            return requests.isEmpty() ? null : new AsyncRequirementPlanner.Requirement(index, amount, IOType.INPUT,
                    requests);
        }
        if (requirement instanceof EnergyRequirement energy && energy.fePerTick() > 0L) {
            return new AsyncRequirementPlanner.Requirement(index, scaled(energy.fePerTick(), parallelism),
                    IOType.valueOf(energy.io().name()), List.of(new AsyncCapabilityRequest.Scalar(energy.type().id(),
                    parallelism, scaled(energy.fePerTick(), parallelism), energy.io() == RecipeModifier.IOType.OUTPUT)));
        }
        if (requirement instanceof LoadedHeatRequirement heat) {
            boolean minimumTemperature = heat.heat().kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE;
            double value = minimumTemperature ? heat.heat().value() : saturatingHeatMultiply(heat.heat().value(), parallelism);
            if (value < 0D) return null;
            long accountingAmount = Math.max(1L, value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(value));
            return new AsyncRequirementPlanner.Requirement(index, accountingAmount,
                    minimumTemperature ? IOType.INPUT : IOType.OUTPUT,
                    List.of(new AsyncCapabilityRequest.Heat(MekanismRecipeTypes.HEAT, parallelism, value,
                            minimumTemperature, accountingAmount)));
        }
        return null;
    }

    private static AsyncCapabilityRequest.Resource resourceRequest(net.minecraft.resources.ResourceLocation capabilityId,
                                                                    long parallelism, AsyncResourceValue resource,
                                                                    long amount, boolean insert) {
        return new AsyncCapabilityRequest.Resource(capabilityId, parallelism,
                List.of(new AsyncResourceAction(resource, amount, insert)));
    }

    private static List<AsyncResourceValue> resources(List<AsyncRequirementPlanner.Capability> capabilities,
                                                       net.minecraft.resources.ResourceLocation capabilityId,
                                                       IOType direction) {
        List<AsyncResourceValue> values = new ArrayList<>();
        for (AsyncRequirementPlanner.Capability capability : capabilities) {
            if (!capability.directions().contains(direction)
                    || !(capability.snapshot() instanceof AsyncCapabilitySnapshot.Resource(
                    ResourceLocation id, List<AsyncCapabilitySnapshot.ResourceSlot> slots
            ))
                    || !capabilityId.equals(id)) continue;
            slots.stream().map(AsyncCapabilitySnapshot.ResourceSlot::resource)
                    .flatMap(java.util.Optional::stream).filter(value -> !values.contains(value)).forEach(values::add);
        }
        return values;
    }

    private static long scaled(long amount, long parallelism) {
        if (amount <= 0L) return 0L;
        return amount > Long.MAX_VALUE / parallelism ? Long.MAX_VALUE : amount * parallelism;
    }

    private static double saturatingHeatMultiply(double amount, long multiplier) {
        if (amount <= 0D || multiplier <= 0L) return 0D;
        return amount >= Double.MAX_VALUE / multiplier ? Double.MAX_VALUE : amount * multiplier;
    }

    private PlanningResult plan(List<MachineRequirement> source, long parallelism, RecipeModifier.IOType direction,
                                Set<Integer> consumedAtStart, Set<Integer> retainedInputs,
                                Map<Integer, OutputPolicy> outputPolicies) {
        if (source == null) throw new IllegalArgumentException("requirements must not be null");
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
            if (direction != null && requirement.io() != direction) continue;
            if (consumedAtStart.contains(index)) continue;
            if (retainedInputs.contains(index) && requirement instanceof ItemRequirement(
                    RecipeModifier.IOType io, net.minecraft.world.item.crafting.Ingredient item1, int count,
                    net.minecraft.world.item.ItemStack stack, float chance, List<String> tags,
                    cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet components, float consumeChance
            )
                    && io == RecipeModifier.IOType.INPUT && consumeChance > 0F) {
                requirement = new ItemRequirement(io, item1, count,
                        stack, chance, tags, components, 0F);
            }
            requirements.add(requirement);
            requirementIndexes.add(index);
        }
        return planSelected(requirements, parallelism, outputPolicies, requirementIndexes);
    }

    private PlanningResult planSelected(List<MachineRequirement> requirements, long parallelism,
                                        Map<Integer, OutputPolicy> outputPolicies, List<Integer> requirementIndexes) {
        if (requirements == null || requirementIndexes == null || requirements.size() != requirementIndexes.size()) {
            throw new IllegalArgumentException("requirements and indexes must match");
        }
        return new RequirementPlanner().plan(requirements, capabilities,
                new PlanningContext(parallelism, 0, outputPolicies), requirementIndexes);
    }

    private PlanningResult planSelected(List<MachineRequirement> source, List<Integer> sourceIndexes,
                                         long parallelism, RecipeModifier.IOType direction,
                                        Map<Integer, OutputPolicy> outputPolicies) {
        if (source == null || sourceIndexes == null || source.size() != sourceIndexes.size()) {
            throw new IllegalArgumentException("requirements and indexes must match");
        }
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
            if (direction != null && requirement.io() != direction) continue;
            requirements.add(requirement);
            requirementIndexes.add(sourceIndexes.get(index));
        }
        return planSelected(requirements, parallelism, outputPolicies, requirementIndexes);
    }

    private static Map<Integer, OutputPolicy> partialOutputPolicies(int size) {
        return outputPoliciesForIndexes(indexes(size), true);
    }

    private static Map<Integer, OutputPolicy> partialOutputPolicies(List<MachineRequirement> requirements,
                                                                     boolean allowPartialOutputs) {
        if (requirements == null) throw new IllegalArgumentException("requirements must not be null");
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (int index = 0; index < requirements.size(); index++) {
            if (requirements.get(index).io() == RecipeModifier.IOType.OUTPUT) {
                policies.put(index, allowPartialOutputs ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
            }
        }
        return Map.copyOf(policies);
    }

    private static Map<Integer, OutputPolicy> outputPoliciesForIndexes(List<Integer> indexes,
                                                                         boolean allowPartialOutputs) {
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (Integer index : indexes) {
            policies.put(index, allowPartialOutputs ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
        }
        return Map.copyOf(policies);
    }

    private static IndexedRequirements replaceOutputs(List<MachineRequirement> base,
                                                     List<MachineOutput> outputs) {
        if (base == null) throw new IllegalArgumentException("requirements must not be null");
        List<MachineOutput> copiedOutputs = MachineOutput.copyList(
                Objects.requireNonNull(outputs, "outputs"));
        List<MachineRequirement> result = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        int outputIndex = 0;
        int extraOutputIndex = 0;
        for (int index = 0; index < base.size(); index++) {
            MachineRequirement requirement = base.get(index);
            if (!OutputRegistry.matchesOutputRequirement(requirement)) {
                result.add(requirement);
                indexes.add(index);
                continue;
            }
            if (outputIndex < copiedOutputs.size()) {
                result.add(outputRequirement(copiedOutputs.get(outputIndex++), requirement));
                indexes.add(index);
            } else {
                outputIndex++;
            }
        }
        while (outputIndex < copiedOutputs.size()) {
            result.add(outputRequirement(copiedOutputs.get(outputIndex++), null));
            indexes.add(base.size() + extraOutputIndex++);
        }
        return new IndexedRequirements(List.copyOf(result), List.copyOf(indexes));
    }

    private static MachineRequirement outputRequirement(MachineOutput output, MachineRequirement template) {
        if (output == null || !Float.isFinite(output.chance())) {
            throw new IllegalArgumentException("outputs must contain finite, non-null values");
        }
        List<String> tags = template == null ? List.of() : template.tags();
        MachineRequirement requirement = OutputRegistry.toRequirement(output, tags);
        if (requirement == null || requirement.io() != RecipeModifier.IOType.OUTPUT) {
            throw new IllegalArgumentException("Output type must produce an output requirement: " + output.outputType().id());
        }
        return requirement;
    }

    private record IndexedRequirements(List<MachineRequirement> requirements, List<Integer> indexes) {
    }

}
