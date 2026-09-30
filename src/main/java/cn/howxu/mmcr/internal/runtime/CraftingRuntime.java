package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeFinishContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeStartContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeTickContext;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeRequirement;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import cn.howxu.mmcr.internal.sync.FailureStatusMigration;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.SaturatingLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns one recipe lifecycle. Capability plans are the only mutable-resource boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingRuntime {
    private final MachineControllerBlockEntity controller;
    private final ComponentRuntime components;
    private @Nullable ControllerScreenText screenText;
    private @Nullable ActiveMachineRecipe activeRecipe;
    private @Nullable CraftingPlan startPlan;
    private @Nullable CraftingPlan finishPlan;
    private List<MachineRequirement> effectiveRequirements = List.of();
    private List<MachineOutput> effectiveOutputs = List.of();
    private @Nullable List<MachineRequirement> cachedPerTickSource;
    private @Nullable Set<Integer> cachedPerTickConsumed;
    private @Nullable Set<Integer> cachedPerTickRetained;
    private @Nullable List<ActivePrefetch> cachedPerTickPrefetches;
    private List<MachineRequirement> cachedPerTickRequirements = List.of();
    private @Nullable List<MachineRequirement> cachedPublicRequirementSource;
    private List<RecipeRequirement> cachedPublicRequirements = List.of();
    private Set<Integer> consumedAtStart = Set.of();
    private Set<Integer> retainedInputs = Set.of();
    private @Nullable ExecutionStatus failure;
    private long structureVersion = Long.MIN_VALUE;
    private long capabilityVersion = Long.MIN_VALUE;
    private long modifierVersion = Long.MIN_VALUE;
    private long componentStateVersion = Long.MIN_VALUE;
    private long upgradeContentRevision = Long.MIN_VALUE;
    private @Nullable StructureClaimRegistry.ResourceDomain resourceDomain;
    private CraftingStatus status = CraftingStatus.IDLE;
    private boolean patternStartReserved;
    private @Nullable PreparedStart pendingPatternStart;
    private @Nullable ExecutionStatus capabilityTickFailure;
    private @Nullable AsyncTickPreparation asyncTickPreparation;
    private @Nullable RecipeFinishContext preparedAsyncFinishContext;
    private List<ActivePrefetch> activePrefetches = List.of();
    private long prefetchedEnergyPerTick;
    private long prefetchedEnergyRemaining;
    private static final String PREFETCH_RESERVATION_KEY = "prefetched_energy_reservation";
    private static final String PREFETCH_ALLOCATIONS_KEY = "prefetched_energy_allocations";
    private static final String PREFETCH_ALLOCATION_KEY = "key";
    private static final String PREFETCH_ALLOCATION_AMOUNT = "amount";

    public CraftingRuntime(MachineControllerBlockEntity controller, ComponentRuntime components) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (components == null) throw new IllegalArgumentException("components must not be null");
        this.controller = controller;
        this.components = components;
    }

    public void setScreenText(@Nullable ControllerScreenText screenText) {
        this.screenText = screenText;
    }

    /** Prepares a pattern start without consuming resources or occupying this runtime. */
    public @Nullable PreparedStart preparePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                       List<MachineCapability> requestCapabilities) {
        if (recipe == null || requestedParallelism <= 0 || active() || patternStartReserved
                || pendingPatternStart != null) return null;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(recipe, runtime)
                || !runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) return null;
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return null;
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> recipeRequirements = effectiveRecipe.requirements();
        List<MachineOutput> outputs = effectiveRecipe.outputs();
        RecipeStartContext.ExecutionSnapshot effective;
        if (behavior.hasBeforeStart()) {
            MachineBehaviorContext machineContext = behaviorContext();
            RecipeStartContext startContext = new RecipeStartContext(machineContext, recipe, requestedParallelism,
                    effectiveParallelism, effectiveRecipe.duration(),
                    MachineRecipeConverter.toPublicRequirements(recipeRequirements), outputs);
            try {
                behavior.beforeStart().accept(startContext);
            } catch (RuntimeException exception) {
                logCallbackFailure("beforeStart", runtime, recipe, exception);
                return null;
            } finally {
                flushScreenTextReplacements(machineContext.screenText());
            }
            if (startContext.cancelled()) return null;
            effective = startContext.snapshot();
        } else {
            effective = new RecipeStartContext.ExecutionSnapshot(effectiveRecipe.duration(),
                    MachineRecipeConverter.toPublicRequirements(recipeRequirements), outputs);
        }
        List<MachineRequirement> requirements = effective.requirements().stream()
                .map(MachineRecipeConverter::toRequirement).toList();
        List<RecipeEnergyPrefetchFacet> facets = prefetchFacets(requestCapabilities);
        PlanningResult result = context(runtime, requestCapabilities).planInputs(startRequirements(requirements, facets), effectiveParallelism,
                Set.of(), Set.of());
        CraftingPlan plan = result.plan();
        if (!result.successful() || plan == null) return null;
        List<PreparedPrefetch> prefetches = planPrefetches(requirements, effective.duration(), plan.parallelism(), facets);
        if (prefetches == null) return null;
        pendingPatternStart = new PreparedStart(effectiveRecipe, runtime,
                catalogVersion(effectiveRecipe.source().recipePoolId()), effective, plan,
                prefetches);
        return pendingPatternStart;
    }

    public boolean reservePatternStart() {
        if (active() || patternStartReserved) return false;
        patternStartReserved = true;
        return true;
    }

    public void releasePatternStart() {
        if (pendingPatternStart != null) releasePreparedPrefetches(pendingPatternStart);
        pendingPatternStart = null;
        patternStartReserved = false;
    }

    public @Nullable ResourceLocation pendingPatternRecipeId() {
        return pendingPatternStart == null ? null : pendingPatternStart.recipe().source().id();
    }

    public boolean commitPatternStart(PreparedStart prepared) {
        if (!patternStartReserved || active() || prepared == null || prepared != pendingPatternStart) return false;
        if (!preparedStartCurrent(prepared)) {
            discardPatternStart(prepared);
            return false;
        }
        boolean committed = false;
        try {
            ExecutionStatus commitFailure = commitPreparedStart(prepared);
            if (commitFailure != null) {
                fail(commitFailure);
                return false;
            }
            committed = true;
        } finally {
            if (!committed) discardPatternStart(prepared);
        }
        activatePatternStart(prepared);
        return true;
    }

    boolean commitPatternPlan(PreparedStart prepared) {
        if (!patternStartReserved || active() || prepared == null || prepared != pendingPatternStart) return false;
        if (!preparedStartCurrent(prepared)) {
            discardPatternStart(prepared);
            return false;
        }
        ExecutionStatus commitFailure = commitPreparedStart(prepared);
        if (commitFailure == null) return true;
        fail(commitFailure);
        return false;
    }

    void releasePreparedPrefetches(PreparedStart prepared) {
        if (prepared == null) return;
        for (PreparedPrefetch prefetch : prepared.prefetches()) {
            prefetch.facet().restoreReservation(prefetch.plan().amount());
        }
    }

    private void discardPatternStart(PreparedStart prepared) {
        if (pendingPatternStart == prepared) releasePatternStart();
        else releasePreparedPrefetches(prepared);
    }

    private boolean preparedStartCurrent(PreparedStart prepared) {
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return prepared.runtime().structure().version() == current.structure().version()
                && prepared.runtime().capabilityVersion() == current.capabilityVersion()
                && prepared.runtime().modifierVersion() == current.modifierVersion()
                && prepared.runtime().stateVersion() == current.stateVersion()
                && prepared.catalogVersion() == catalogVersion(prepared.recipe().source().recipePoolId());
    }

    private static long catalogVersion(ResourceLocation recipePoolId) {
        return RecipeRegistry.catalogForPool(recipePoolId).version();
    }

    void activatePatternStart(PreparedStart prepared) {
        activeRecipe = new ActiveMachineRecipe(prepared.recipe().source(), prepared.plan().parallelism(), prepared.effective());
        activeRecipe.setParallelism(prepared.plan().parallelism());
        startPlan = prepared.plan();
        finishPlan = null;
        effectiveRequirements = MachineRequirement.copyList(prepared.effective().requirements().stream()
                .map(MachineRecipeConverter::toRequirement).toList());
        effectiveOutputs = MachineOutput.copyList(prepared.effective().outputs());
        activatePrefetches(prepared.prefetches(), effectiveRequirements, activeRecipe.getParallelism());
        captureInputState(effectiveRequirements, prepared.plan(), !prepared.prefetches().isEmpty());
        captureVersions(prepared.runtime());
        pendingPatternStart = null;
        patternStartReserved = false;
        status = CraftingStatus.working();
        failure = null;
        controller.onPatternStartCommitted();
    }

    public record PreparedStart(EffectiveRecipe recipe, ControllerRuntimeSnapshot runtime, long catalogVersion,
                                RecipeStartContext.ExecutionSnapshot effective, CraftingPlan plan,
                                List<PreparedPrefetch> prefetches) {
        public PreparedStart {
            prefetches = List.copyOf(prefetches);
        }

        public PreparedStart(EffectiveRecipe recipe, ControllerRuntimeSnapshot runtime,
                             RecipeStartContext.ExecutionSnapshot effective, CraftingPlan plan,
                             List<PreparedPrefetch> prefetches) {
            this(recipe, runtime, CraftingRuntime.catalogVersion(recipe.source().recipePoolId()), effective, plan,
                    prefetches);
        }
    }

    record PreparedPrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet,
                            RecipeEnergyPrefetchFacet.PrefetchPlan plan) {
    }

    public CraftingStatus start(MachineRecipe recipe, long requestedParallelism) {
        return start(recipe, requestedParallelism, null);
    }

    /** Executes the start behavior callback before a shared-IO transaction is requested. */
    public @Nullable RecipeStartContext.ExecutionSnapshot prepareAsyncStart(MachineRecipe recipe, long requestedParallelism) {
        if (recipe == null || requestedParallelism <= 0 || active() || patternStartReserved) return null;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(recipe, runtime)
                || !runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) return null;
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return null;
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> requirements = effectiveRecipe.requirements();
        if (!behavior.hasBeforeStart()) {
            return new RecipeStartContext.ExecutionSnapshot(effectiveRecipe.duration(),
                    MachineRecipeConverter.toPublicRequirements(requirements), effectiveRecipe.outputs());
        }
        MachineBehaviorContext machineContext = behaviorContext();
        RecipeStartContext startContext = new RecipeStartContext(machineContext, recipe, requestedParallelism,
                effectiveParallelism, effectiveRecipe.duration(),
                MachineRecipeConverter.toPublicRequirements(requirements), effectiveRecipe.outputs());
        try {
            behavior.beforeStart().accept(startContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeStart", runtime, recipe, exception);
            fail(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.RECIPE_START, Map.of()));
            return null;
        }
        if (startContext.cancelled()) {
            failure = null;
            status = CraftingStatus.IDLE;
            return null;
        }
        return startContext.snapshot();
    }

    public CraftingStatus start(MachineRecipe recipe, long requestedParallelism,
                                @Nullable RecipeStartContext.ExecutionSnapshot preparedStart) {
        if (recipe == null || requestedParallelism <= 0) {
            return fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()));
        }
        if (active() || patternStartReserved) return status;

        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(recipe, runtime)) {
            return fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START,
                    Map.of("reason", "recipe_pool")));
        }
        if (!runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) {
            return fail(failure(BuiltinFailureReasons.MODULE_CONNECTION, FailurePhase.RECIPE_START, Map.of()));
        }
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            return fail(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.RECIPE_START, Map.of()));
        }
        RecipeStartContext.ExecutionSnapshot effective = preparedStart == null
                ? prepareAsyncStart(recipe, requestedParallelism) : preparedStart;
        if (effective == null) return status;
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> requirements = effective.requirements().stream()
                .map(MachineRecipeConverter::toRequirement).toList();
        List<RecipeEnergyPrefetchFacet> facets = prefetchFacets(List.of());
        CraftingContext context = context(runtime);
        PlanningResult result = context.planInputs(startRequirements(requirements, facets), effectiveParallelism,
                Set.of(), Set.of());
        CraftingPlan plan = result.plan();
        if (!result.successful() || plan == null) {
            return fail(result.failure());
        }
        List<PreparedPrefetch> prefetches = planPrefetches(requirements, effective.duration(), plan.parallelism(), facets);
        if (prefetches == null) return fail(missingInputStatus());
        PreparedStart prepared = new PreparedStart(effectiveRecipe, runtime,
                catalogVersion(effectiveRecipe.source().recipePoolId()), effective, plan, prefetches);
        boolean committed = false;
        try {
            ExecutionStatus commitFailure = commitPreparedStart(prepared);
            if (commitFailure != null) return fail(commitFailure);
            committed = true;
        } finally {
            if (!committed) releasePreparedPrefetches(prepared);
        }

        activeRecipe = new ActiveMachineRecipe(recipe, plan.parallelism(), effective);
        activeRecipe.setParallelism(plan.parallelism());
        startPlan = plan;
        finishPlan = null;
        effectiveRequirements = MachineRequirement.copyList(effective.requirements().stream()
                .map(MachineRecipeConverter::toRequirement).toList());
        effectiveOutputs = MachineOutput.copyList(effective.outputs());
        activatePrefetches(prefetches, effectiveRequirements, activeRecipe.getParallelism());
        captureInputState(effectiveRequirements, plan, !prefetches.isEmpty());
        captureVersions(runtime);
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    public CraftingStatus tick() {
        if (!active()) return status;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        if (activeRecipe.isFinishPending()) return status;

        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            return waiting(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.PER_TICK, Map.of()));
        }
        MachineBehaviorContext machineContext = behaviorContext();
        RecipeTickContext recipeTickContext = new RecipeTickContext(machineContext, activeRecipe.getRecipe(),
                activeRecipe.getTick(), activeRecipe.getTotalTick(), activeRecipe.getParallelism(),
                MachineRecipeConverter
                        .toPublicRequirements(effectiveRequirements()), activeOutputs(),
                new CapabilitySnapshot(components.capabilities()));
        if (!executeTickPhase(CapabilityTickPhase.BEFORE_RECIPE, machineContext, recipeTickContext)) return status;
        if (behavior.hasRecipeTick()) {
            try {
                behavior.recipeTick().accept(recipeTickContext);
            } catch (RuntimeException exception) {
                logCallbackFailure("recipeTick", runtime, activeRecipe.getRecipe(), exception);
            } finally {
                flushScreenTextReplacements(machineContext.screenText());
            }
        }
        CraftingContext context = context(runtime);
        PlanningResult result = planPerTick(context);
        CraftingPlan tickPlan = result.plan();
        if (!result.successful() || tickPlan == null || tickPlan.parallelism() < activeRecipe.getParallelism()) {
            return waiting(result.failure());
        }
        try {
            if (!tickPlan.commit()) return waiting(tickPlan.failure());
        } catch (RuntimeException exception) {
            logTickFailure("commit", runtime, activeRecipe.getRecipe(), exception);
            return waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
        }
        ExecutionStatus prefetchFailure = consumePrefetchedEnergy();
        if (prefetchFailure != null) return waiting(prefetchFailure);
        if (!executeTickPhase(CapabilityTickPhase.AFTER_INPUTS, machineContext, recipeTickContext)) {
            if (activeRecipe != null) activeRecipe.applyTickGrant(true, false, currentGameTime());
            return status;
        }

        int gameTime = currentGameTime();
        if (activeRecipe.needsFinishCommit()) {
            if (!executeTickPhase(CapabilityTickPhase.AFTER_RECIPE, machineContext, recipeTickContext)) {
                if (activeRecipe != null) activeRecipe.applyTickGrant(true, false, gameTime);
                return status;
            }
            activeRecipe.beginFinishCommit();
            status = CraftingStatus.working();
            return status;
        }
        activeRecipe.applyTickGrant(true, false, gameTime);
        if (!executeTickPhase(CapabilityTickPhase.AFTER_RECIPE, machineContext, recipeTickContext)) return status;
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    /** Captures the main-thread recipe tick context before any worker-side planning begins. */
    public boolean prepareAsyncTick(ControllerRuntimeSnapshot runtime) {
        asyncTickPreparation = null;
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        if (!active()) return false;
        if (!versionsCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return false;
        }
        if (activeRecipe.isFinishPending()) return false;
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            waiting(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.PER_TICK, Map.of()));
            return false;
        }
        CapabilitySnapshot capabilitySnapshot = new CapabilitySnapshot(components.capabilities());
        MachineBehaviorContext machineContext = behaviorContext(capabilitySnapshot);
        if (cachedPublicRequirementSource != effectiveRequirements) {
            cachedPublicRequirementSource = effectiveRequirements;
            cachedPublicRequirements = MachineRecipeConverter.toPublicRequirements(effectiveRequirements);
        }
        RecipeTickContext tickContext = new RecipeTickContext(machineContext, activeRecipe.getRecipe(),
                activeRecipe.getTick(), activeRecipe.getTotalTick(), activeRecipe.getParallelism(),
                cachedPublicRequirements, effectiveOutputs, capabilitySnapshot);
        asyncTickPreparation = new AsyncTickPreparation(runtime, machineContext, tickContext);
        return true;
    }

    /** Executes one deferred capability tick phase against the captured recipe tick context. */
    public boolean executeAsyncCapabilityTick(CapabilityTickPhase phase) {
        AsyncTickPreparation preparation = asyncTickPreparation;
        return preparation != null && executeAsyncTickPhase(phase, preparation.machineContext(), preparation.tickContext());
    }

    public void discardAsyncTickPreparation() {
        asyncTickPreparation = null;
    }

    /** Runs main-thread tick preparation and captures the worker-safe native requirement plan. */
    public @Nullable AsyncRequirementPlanner.PreparedPlan prepareAsyncTickPlan(
            ControllerRuntimeSnapshot runtimeSnapshot) {
        if (!prepareAsyncTick(runtimeSnapshot)) return null;
        if (!executeAsyncCapabilityTick(CapabilityTickPhase.BEFORE_RECIPE)) {
            discardAsyncTickPreparation();
            flushAsyncScreenText();
            return null;
        }
        AsyncTickPreparation preparation = asyncTickPreparation;
        RecipeBehavior behavior = preparation == null ? null : recipeBehavior(preparation.runtime());
        if (preparation == null || behavior == null) return null;
        if (behavior.hasRecipeTick()) {
            try {
                behavior.recipeTick().accept(preparation.tickContext());
            } catch (RuntimeException exception) {
                logCallbackFailure("recipeTick", preparation.runtime(), activeRecipe.getRecipe(), exception);
            } finally {
                flushAsyncScreenText();
            }
        }
        List<MachineRequirement> requirements = perTickRequirements();
        if (requirements.isEmpty()) return new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), List.of());
        return context(preparation.runtime()).planAsync(requirements, activeRecipe.getParallelism());
    }

    /** Flushes recipe behavior screen text after its callback has run on the server thread. */
    public void flushAsyncScreenText() {
        AsyncTickPreparation preparation = asyncTickPreparation;
        flushScreenTextReplacements(preparation == null ? behaviorContext().screenText() : preparation.machineContext().screenText());
    }

    /** Commits worker-planned native operations inside the shared-IO arbitration transaction. */
    public boolean commitAsyncTick(AsyncRequirementPlanner.PlanResult planned) {
        AsyncTickPreparation preparation = asyncTickPreparation;
        if (preparation == null || !active()) return false;
        if (!versionsCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return false;
        }
        return commitAsyncTickPlan(planned, preparation.runtime());
    }

    /** Runs the AFTER_INPUTS capability phase after the shared-IO transaction has committed. */
    public boolean completeAsyncTickAfterInputs() {
        AsyncTickPreparation preparation = asyncTickPreparation;
        if (preparation == null || !active()) return false;
        if (!versionsCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return false;
        }
        if (!executeAsyncTickPhase(CapabilityTickPhase.AFTER_INPUTS,
                preparation.machineContext(), preparation.tickContext())) {
            if (activeRecipe != null) activeRecipe.applyTickGrant(true, false, currentGameTime());
            asyncTickPreparation = null;
            return false;
        }
        return true;
    }

    /** Runs the AFTER_RECIPE capability phase and publishes tick progress after the previous explicit steps succeed. */
    public CraftingStatus completeAsyncTickAfterRecipe() {
        AsyncTickPreparation preparation = asyncTickPreparation;
        asyncTickPreparation = null;
        if (preparation == null || !active()) return status;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        int gameTime = currentGameTime();
        if (activeRecipe.needsFinishCommit()) {
            if (!executeAsyncTickPhase(CapabilityTickPhase.AFTER_RECIPE,
                    preparation.machineContext(), preparation.tickContext())) {
                if (activeRecipe != null) activeRecipe.applyTickGrant(true, false, gameTime);
                return status;
            }
            activeRecipe.beginFinishCommit();
            status = CraftingStatus.working();
            return status;
        }
        activeRecipe.applyTickGrant(true, false, gameTime);
        if (!executeAsyncTickPhase(CapabilityTickPhase.AFTER_RECIPE,
                preparation.machineContext(), preparation.tickContext())) return status;
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    public CapabilityTickResult tickIdle() {
        if (components.capabilities().stream().noneMatch(capability -> capability.facet(TickFacet.class).isPresent())) {
            return handleCapabilityTickResult(CapabilityTickResult.empty());
        }
        MachineBehaviorContext machineContext = behaviorContext();
        return handleCapabilityTickResult(components.executeTickPhase(new CapabilityTickContext(capabilityGameTime(), CapabilityTickPhase.IDLE,
                null, 1L, new CapabilitySnapshot(components.capabilities()), machineContext)));
    }

    public CapabilityTickResult handleCapabilityTickResult(CapabilityTickResult result) {
        if (result == null) throw new IllegalArgumentException("result must not be null");
        if (result.failure() != null) {
            capabilityTickFailure = result.failure();
            waiting(result.failure());
        } else if (capabilityTickFailure != null && capabilityTickFailure.equals(failure)) {
            capabilityTickFailure = null;
            failure = null;
            status = active() ? CraftingStatus.working() : CraftingStatus.IDLE;
        }
        if (result.failure() != null || result.stateChanged()) controller.syncRecipeRuntimeFailure(this);
        return result;
    }

    public CraftingStatus finish() {
        if (!active()) return status;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        if (!activeRecipe.isFinishPending()) return status;
        if (!activeRecipe.shouldRetryFinish(currentGameTime())) return status;

        RecipeFinishContext finishContext = preparedAsyncFinishContext;
        preparedAsyncFinishContext = null;
        if (finishContext == null) {
            finishContext = prepareFinishContext();
            if (finishContext == null) return status;
        }
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        CraftingContext context = context(runtime);
        if (finishContext.cancelled()) {
            return finishBlocked(failure(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH_CANCELLED,
                    FailurePhase.FINISH, Map.of()));
        }
        if (finishContext.outputsDiscarded()) {
            activeRecipe.applyTickGrant(true, true, currentGameTime());
            releaseActivePrefetches();
            activeRecipe = null;
            startPlan = null;
            finishPlan = null;
            clearEffectiveRecipe();
            consumedAtStart = Set.of();
            retainedInputs = Set.of();
            resourceDomain = null;
            failure = null;
            status = CraftingStatus.IDLE;
            return status;
        }
        PlanningResult result;
        try {
            result = context.planOutputRequirements(finishRequirements(), finishOutputs(finishContext),
                    activeRecipe.getParallelism(), activeRecipe.getRecipe().allowPartialOutputs());
        } catch (IllegalArgumentException exception) {
            logCallbackFailure("beforeFinish.output_validation", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure(BuiltinFailureReasons.INVALID_OUTPUTS, FailurePhase.FINISH, Map.of()));
        } catch (RuntimeException exception) {
            logFinishFailure("planning", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure(BuiltinFailureReasons.FINISH, FailurePhase.FINISH, Map.of()));
        }
        finishPlan = result.plan();
        if (!result.successful() || finishPlan == null) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return finishBlocked(result.failure());
        }
        boolean committed;
        try {
            committed = finishPlan.commit();
        } catch (RuntimeException exception) {
            logFinishFailure("commit", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure(BuiltinFailureReasons.FINISH, FailurePhase.FINISH, Map.of()));
        }
        if (!committed) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return finishBlocked(finishPlan.failure());
        }
        activeRecipe.applyTickGrant(true, true, currentGameTime());
        releaseActivePrefetches();
        activeRecipe = null;
        startPlan = null;
        finishPlan = null;
        preparedAsyncFinishContext = null;
        clearEffectiveRecipe();
        consumedAtStart = Set.of();
        retainedInputs = Set.of();
        resourceDomain = null;
        failure = null;
        status = CraftingStatus.IDLE;
        return status;
    }

    /** Executes the finish behavior callback before a shared-IO output transaction is requested. */
    public boolean prepareAsyncFinish() {
        if (!active() || !versionsCurrent() || !activeRecipe.isFinishPending()
                || !activeRecipe.shouldRetryFinish(currentGameTime())) return false;
        preparedAsyncFinishContext = prepareFinishContext();
        return preparedAsyncFinishContext != null;
    }

    private @Nullable RecipeFinishContext prepareFinishContext() {
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            finishBlocked(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.FINISH, Map.of()));
            return null;
        }
        RecipeFinishContext finishContext;
        if (!behavior.hasBeforeFinish()) {
            return new RecipeFinishContext(activeRecipe.getRecipe(), activeRecipe.getMaxParallelism(),
                    activeRecipe.getParallelism(), activeOutputs());
        }
        MachineBehaviorContext machineContext = behaviorContext();
        finishContext = new RecipeFinishContext(machineContext, activeRecipe.getRecipe(),
                activeRecipe.getMaxParallelism(), activeRecipe.getParallelism(), activeOutputs());
        try {
            behavior.beforeFinish().accept(finishContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeFinish", runtime, activeRecipe.getRecipe(), exception);
            finishBlocked(failure(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH, FailurePhase.FINISH, Map.of()));
            return null;
        }
        if (finishContext.cancelled()) {
            finishBlocked(failure(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH_CANCELLED, FailurePhase.FINISH, Map.of()));
            return null;
        }
        return finishContext;
    }

    public boolean active() {
        return activeRecipe != null;
    }

    private boolean executeTickPhase(CapabilityTickPhase phase, MachineBehaviorContext machineContext,
                                     RecipeTickContext recipeTickContext) {
        CapabilityTickResult result;
        try {
            result = handleCapabilityTickResult(components.executeTickPhase(new CapabilityTickContext(
                    capabilityGameTime(), phase,
                    recipeTickContext, activeRecipe.getParallelism(), new CapabilitySnapshot(components.capabilities()),
                    machineContext)));
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Machine recipe tick capability phase failed: phase={} controller={}", phase,
                    controller.getBlockPos(), exception);
            handleCapabilityTickResult(new CapabilityTickResult(List.of(),
                    failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()), false));
            return false;
        }
        return result.failure() == null;
    }

    private boolean executeAsyncTickPhase(CapabilityTickPhase phase, MachineBehaviorContext machineContext,
                                          RecipeTickContext recipeTickContext) {
        return executeTickPhase(phase, machineContext, recipeTickContext);
    }

    public void pause() {
        if (active()) status = CraftingStatus.paused();
    }

    public void resume() {
        if (active()) status = CraftingStatus.working();
    }

    public @Nullable ExecutionStatus failure() {
        return failure;
    }

    public CraftingStateSnapshot snapshot() {
        ActiveMachineRecipe recipe = activeRecipe;
        return new CraftingStateSnapshot(activeRecipe == null ? null : activeRecipe.getRecipe().id(), status, failure,
                structureVersion == Long.MIN_VALUE ? 0L : structureVersion,
                capabilityVersion == Long.MIN_VALUE ? 0L : capabilityVersion,
                modifierVersion == Long.MIN_VALUE ? 0L : modifierVersion,
                tickCount(), totalTick(), parallelism(), recipe == null ? 1 : recipe.getMaxParallelism());
    }

    public @Nullable MachineRecipe recipe() {
        return activeRecipe == null ? null : activeRecipe.getRecipe();
    }

    public @Nullable ActiveMachineRecipe activeRecipe() {
        return activeRecipe;
    }

    /**
     * Temporary string presentation boundary for unchanged Task 7 packet/menu callers.
     * Remove it when those callers consume the typed failure directly.
     */
    public @Nullable String failureUnloc() {
        return failure == null ? null : failureUnloc(failure);
    }

    public int tickCount() {
        return activeRecipe == null ? 0 : activeRecipe.getTick();
    }

    public int totalTick() {
        return activeRecipe == null ? 0 : activeRecipe.getTotalTick();
    }

    public long parallelism() {
        return activeRecipe == null ? 0 : activeRecipe.getParallelism();
    }

    public long maxParallelism() {
        return activeRecipe == null ? 1L : activeRecipe.getMaxParallelism();
    }

    public boolean finishPending() {
        return activeRecipe != null && activeRecipe.isFinishPending();
    }

    public boolean shouldRetryFinish() {
        return activeRecipe != null && activeRecipe.shouldRetryFinish(currentGameTime());
    }

    public void recordSearchFailure(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.RECIPE_SEARCH, FailurePhase.RECIPE_SEARCH, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    public boolean versionsCurrent() {
        if (!active()) return true;
        StructureSnapshot structure = controller.currentStructureSnapshot();
        ComponentRuntime components = controller.componentRuntime();
        return structureVersion == structure.version()
                && capabilityVersion == components.capabilityVersion()
                && recipeBelongsToMachine(activeRecipe.getRecipe(), controller.currentRuntimeSnapshot())
                && controller.currentRuntimeSnapshot().moduleConnectionStatus()
                .canRunRecipe(activeRecipe.getRecipe().requiredHostIds());
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        return resourceDomain;
    }

    public void invalidate() {
        releasePatternStart();
        releaseActivePrefetches();
        activeRecipe = null;
        startPlan = null;
        finishPlan = null;
        preparedAsyncFinishContext = null;
        clearEffectiveRecipe();
        consumedAtStart = Set.of();
        retainedInputs = Set.of();
        resourceDomain = null;
        patternStartReserved = false;
        failure = null;
        status = CraftingStatus.IDLE;
    }

    public void invalidateForSmartInterfaceChange() {
        if (!active()) releasePatternStart();
    }

    public void invalidateForCatalogChange() {
        if (active()) invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
    }

    public void restore(ActiveMachineRecipe restored, @Nullable StructureClaimRegistry.ResourceDomain domain,
                        long restoredStructureVersion, long restoredCapabilityVersion,
                        long restoredModifierVersion, long restoredComponentStateVersion) {
        if (restored == null || restored.getRecipe() == null) {
            failLoad();
            return;
        }
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(restored.getRecipe(), runtime)) {
            failLoad();
            return;
        }
        List<MachineRequirement> requirements = restored.hasEffectiveExecutionSnapshot()
                ? restored.effectiveRequirements()
                : restored.getRecipe().runtimeRequirements(contextModifiers(runtime));
        if (!restored.hasValidInputConsumptionPlan(requirements)) {
            failLoad();
            return;
        }
        List<MachineOutput> outputs = restored.hasEffectiveExecutionSnapshot()
                ? restored.effectiveOutputs()
                : restored.getRecipe().runtimeMachineOutputs(contextModifiers(runtime));
        if (!restored.hasEffectiveExecutionSnapshot()) {
            restored.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(
                    duration(restored.getRecipe(), runtime),
                    MachineRecipeConverter.toPublicRequirements(requirements), outputs));
            if (restored.getTotalTick() < 1 || restored.getTick() < 0
                    || restored.getTick() > restored.getTotalTick()
                    || (restored.isFinishPending() && restored.getTick() != restored.getTotalTick() - 1)) {
                failLoad();
                return;
            }
        }
        if (!restorePrefetches(restored, requirements)) {
            failLoad();
            return;
        }
        activeRecipe = restored;
        startPlan = null;
        finishPlan = null;
        resourceDomain = domain;
        structureVersion = restoredStructureVersion;
        capabilityVersion = restoredCapabilityVersion;
        modifierVersion = restoredModifierVersion;
        componentStateVersion = restoredComponentStateVersion;
        upgradeContentRevision = runtime.upgradeContentRevision();
        effectiveRequirements = MachineRequirement.copyList(requirements);
        effectiveOutputs = MachineOutput.copyList(outputs);
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(ItemRequirement.TYPE.equals(requirement.type()) || FluidRequirement.TYPE.equals(requirement.type())
                    || LoadedChemicalRequirement.TYPE.equals(requirement.type()))
                    || requirement.io() != RecipeModifier.IOType.INPUT) continue;
            if (restored.inputConsumptionPlan().consumedBatches(index) > 0) consumed.add(index);
            else retained.add(index);
        }
        consumedAtStart = Set.copyOf(consumed);
        retainedInputs = Set.copyOf(retained);
        List<Integer> consumedBatches = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            consumedBatches.add(consumed.contains(index) ? 1 : 0);
        }
        activeRecipe.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(consumedBatches));
        status = CraftingStatus.working();
        failure = null;
    }

    public void rebindCurrentVersions() {
        if (!active()) return;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(activeRecipe.getRecipe(), runtime)) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return;
        }
        captureVersions(runtime);
    }

    public void save(CompoundTag output, HolderLookup.Provider registries) {
        boolean present = activeRecipe != null && activeRecipe.getRecipe() != null;
        output.putBoolean("active", present);
        CompoundTag failureOutput = new CompoundTag();
        FailureStatusCodec.write(failureOutput, failure);
        output.put("failure", failureOutput);
        if (present) {
            output.putLong("structure_version", structureVersion);
            output.putLong("capability_version", capabilityVersion);
            output.putLong("modifier_version", modifierVersion);
            output.putLong("component_state_version", componentStateVersion);
            output.putLong("upgrade_content_revision", upgradeContentRevision);
            savePrefetchAllocations();
            CompoundTag recipeOutput = new CompoundTag();
            activeRecipe.serialize(recipeOutput, registries);
            output.put("recipe", recipeOutput);
        }
    }

    public void load(CompoundTag input, @Nullable StructureClaimRegistry.ResourceDomain domain,
                     HolderLookup.Provider registries) {
        boolean active = input.getBoolean("active");
        if (!active) {
            invalidate();
            restoreFailure(readFailure(input, null));
            return;
        }
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId == null) {
            failLoad();
            return;
        }
        ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.loadForPool(
                input.getCompound("recipe"), registries, recipePoolId);
        if (!loaded.successful()) {
            failLoad();
            return;
        }
        long restoredUpgradeContentRevision = input.contains("upgrade_content_revision")
                ? input.getLong("upgrade_content_revision") : Long.MIN_VALUE;
        restore(loaded.recipe(), domain,
                input.contains("structure_version") ? input.getLong("structure_version") : Long.MIN_VALUE,
                input.contains("capability_version") ? input.getLong("capability_version") : Long.MIN_VALUE,
                input.contains("modifier_version") ? input.getLong("modifier_version") : Long.MIN_VALUE,
                input.contains("component_state_version") ? input.getLong("component_state_version") : Long.MIN_VALUE);
        if (active()) {
            if (restoredUpgradeContentRevision != Long.MIN_VALUE) {
                upgradeContentRevision = restoredUpgradeContentRevision;
            }
            restoreFailure(readFailure(input, loaded.recipe().getRecipe().id()));
        }
    }

    private static @Nullable ExecutionStatus readFailure(CompoundTag input, @Nullable ResourceLocation recipeId) {
        if (input.contains("failure")) return FailureStatusCodec.read(input.getCompound("failure"));
        if (!input.getBoolean("has_failure")) return null;
        return FailureStatusMigration.craftingFailure(input.getString("failure_reason"), recipeId);
    }

    private void failLoad() {
        invalidate();
        failure = failure(BuiltinFailureReasons.RECIPE_LOAD, FailurePhase.RECIPE_LOAD, Map.of());
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    private void restoreFailure(@Nullable ExecutionStatus restoredFailure) {
        if (restoredFailure == null) return;
        failure = restoredFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    private CraftingStatus waiting(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        if (activeRecipe != null && activeRecipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
            releaseActivePrefetches();
            activeRecipe = null;
            startPlan = null;
            finishPlan = null;
            clearEffectiveRecipe();
            resourceDomain = null;
        }
        return status;
    }

    private CraftingStatus finishBlocked(@Nullable ExecutionStatus nextFailure) {
        if (activeRecipe != null) activeRecipe.markFinishBlocked(currentGameTime());
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.FINISH, FailurePhase.FINISH, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private CraftingStatus invalidate(FailureReason reason, FailurePhase phase) {
        failure = failure(reason, phase, Map.of());
        releaseActivePrefetches();
        activeRecipe = null;
        startPlan = null;
        finishPlan = null;
        preparedAsyncFinishContext = null;
        clearEffectiveRecipe();
        consumedAtStart = Set.of();
        retainedInputs = Set.of();
        resourceDomain = null;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private CraftingStatus fail(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private MachineBehaviorContext behaviorContext() {
        return screenText == null ? controller.behaviorContext() : controller.behaviorContext(screenText);
    }

    private MachineBehaviorContext behaviorContext(CapabilitySnapshot capabilities) {
        return screenText == null
                ? controller.behaviorContext(capabilities)
                : controller.behaviorContext(capabilities, screenText);
    }

    private static void flushScreenTextReplacements(ControllerScreenText screenText) {
        if (screenText instanceof ControllerScreenTextState state) state.flushReplacements();
    }

    private CraftingContext context(ControllerRuntimeSnapshot runtime) {
        return new CraftingContext(new CapabilitySnapshot(components.capabilities()), contextModifiers(runtime));
    }

    private CraftingContext context(ControllerRuntimeSnapshot runtime, List<MachineCapability> requestCapabilities) {
        List<MachineCapability> capabilities = new ArrayList<>(components.capabilities());
        if (requestCapabilities != null) capabilities.addAll(requestCapabilities);
        return new CraftingContext(new CapabilitySnapshot(capabilities), contextModifiers(runtime));
    }

    private List<RecipeEnergyPrefetchFacet> prefetchFacets(List<MachineCapability> requestCapabilities) {
        List<MachineCapability> capabilities = new ArrayList<>(components.capabilities());
        if (requestCapabilities != null) capabilities.addAll(requestCapabilities);
        return new CapabilitySnapshot(capabilities).facets(RecipeEnergyPrefetchFacet.class);
    }

    private static List<MachineRequirement> startRequirements(List<MachineRequirement> requirements,
                                                               List<RecipeEnergyPrefetchFacet> facets) {
        if (facets.isEmpty()) return requirements;
        return requirements.stream().filter(requirement -> !(requirement instanceof EnergyRequirement energy
                && energy.io() == RecipeModifier.IOType.INPUT)).toList();
    }

    private @Nullable List<PreparedPrefetch> planPrefetches(List<MachineRequirement> requirements, int duration,
                                                             long parallelism, List<RecipeEnergyPrefetchFacet> facets) {
        if (facets.isEmpty()) return List.of();
        boolean hasEnergyInput = requirements.stream().anyMatch(requirement -> requirement instanceof EnergyRequirement energy
                && energy.io() == RecipeModifier.IOType.INPUT);
        if (!hasEnergyInput) return List.of();
        Set<String> reservationKeys = new HashSet<>();
        for (RecipeEnergyPrefetchFacet facet : facets) {
            String reservationKey = facet.reservationKey();
            if (reservationKey == null || reservationKey.isBlank() || !reservationKeys.add(reservationKey)) return null;
        }
        List<PreparedPrefetch> prefetches = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (!(requirement instanceof EnergyRequirement energy)
                    || energy.io() != RecipeModifier.IOType.INPUT) continue;
            long remaining = SaturatingLong.multiply(SaturatingLong.multiply(energy.fePerTick(), duration), parallelism);
            for (RecipeEnergyPrefetchFacet facet : facets) {
                if (remaining <= 0L) break;
                var planned = facet.planPrefetch(remaining);
                if (planned.isEmpty()) continue;
                RecipeEnergyPrefetchFacet.PrefetchPlan plan = planned.get();
                if (plan.amount() > remaining) {
                    facet.restoreReservation(plan.amount());
                    for (PreparedPrefetch prefetch : prefetches) {
                        prefetch.facet().restoreReservation(prefetch.plan().amount());
                    }
                    return null;
                }
                long accepted = plan.amount();
                prefetches.add(new PreparedPrefetch(facet.reservationKey(), facet, plan));
                remaining -= accepted;
            }
            if (remaining > 0L) {
                for (PreparedPrefetch prefetch : prefetches) {
                    prefetch.facet().restoreReservation(prefetch.plan().amount());
                }
                return null;
            }
        }
        return List.copyOf(prefetches);
    }

    private @Nullable ExecutionStatus commitPreparedStart(PreparedStart prepared) {
        if (!prepared.plan().commitInputs()) {
            ExecutionStatus failure = prepared.plan().failure();
            return failure == null ? missingInputStatus() : failure;
        }
        for (PreparedPrefetch prefetch : prepared.prefetches()) {
            CapabilityResult result = prefetch.plan().operation().commit();
            if (result == null || !result.success()) {
                return result == null || result.status() == null ? missingInputStatus() : result.status();
            }
        }
        return null;
    }

    private ExecutionStatus missingInputStatus() {
        return failure(BuiltinFailureReasons.MISSING_INPUT, FailurePhase.RECIPE_START, Map.of());
    }

    private void activatePrefetches(List<PreparedPrefetch> prefetches, List<MachineRequirement> requirements,
                                    long parallelism) {
        Map<String, ActivePrefetch> allocations = new LinkedHashMap<>();
        for (PreparedPrefetch prefetch : prefetches) {
            ActivePrefetch existing = allocations.get(prefetch.reservationKey());
            long remaining = existing == null ? prefetch.plan().amount()
                    : SaturatingLong.add(existing.remaining(), prefetch.plan().amount());
            allocations.put(prefetch.reservationKey(), new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    remaining));
        }
        activePrefetches = List.copyOf(allocations.values());
        prefetchedEnergyPerTick = prefetchedEnergyPerTick(requirements, parallelism);
        prefetchedEnergyRemaining = activePrefetches.stream()
                .mapToLong(ActivePrefetch::remaining).reduce(0L, SaturatingLong::add);
    }

    private boolean restorePrefetches(ActiveMachineRecipe restored, List<MachineRequirement> requirements) {
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        CompoundTag data = restored.getDataCompound();
        if (!data.contains(PREFETCH_ALLOCATIONS_KEY)) {
            return !data.getBoolean(PREFETCH_RESERVATION_KEY);
        }
        ListTag allocationList = data.getList(PREFETCH_ALLOCATIONS_KEY, Tag.TAG_COMPOUND);
        if (allocationList.isEmpty()) return false;
        List<StoredPrefetch> allocations = new ArrayList<>();
        Set<String> storedKeys = new HashSet<>();
        for (int index = 0; index < allocationList.size(); index++) {
            CompoundTag allocation = allocationList.getCompound(index);
            if (allocation.isEmpty()) return false;
            String reservationKey = allocation.getString(PREFETCH_ALLOCATION_KEY);
            if (!(allocation.get(PREFETCH_ALLOCATION_AMOUNT) instanceof LongTag longTag)) return false;
            long amount = longTag.getAsLong();
            if (reservationKey.isBlank() || !storedKeys.add(reservationKey) || amount < 0L) return false;
            allocations.add(new StoredPrefetch(reservationKey, amount));
        }
        if (allocations.isEmpty()) return false;

        Map<String, RecipeEnergyPrefetchFacet> facetsByKey = new LinkedHashMap<>();
        for (RecipeEnergyPrefetchFacet facet : prefetchFacets(List.of())) {
            String reservationKey;
            try {
                reservationKey = facet.reservationKey();
            } catch (RuntimeException exception) {
                return false;
            }
            if (reservationKey == null || reservationKey.isBlank() || facetsByKey.putIfAbsent(reservationKey, facet) != null) {
                return false;
            }
        }
        long perTick = prefetchedEnergyPerTick(requirements, restored.getParallelism());
        long total = SaturatingLong.multiply(perTick, restored.getTotalTick());
        int committedTicks = restored.getTick() + (restored.isFinishPending() ? 1 : 0);
        long remaining = Math.max(0L, total - SaturatingLong.multiply(perTick, committedTicks));
        long allocationTotal = 0L;
        List<ActivePrefetch> restoredPrefetches = new ArrayList<>(allocations.size());
        for (StoredPrefetch allocation : allocations) {
            if (!facetsByKey.containsKey(allocation.reservationKey())) return false;
            try {
                allocationTotal = Math.addExact(allocationTotal, allocation.amount());
            } catch (ArithmeticException exception) {
                return false;
            }
        }
        if (allocationTotal != remaining) return false;
        try {
            for (StoredPrefetch allocation : allocations) {
                RecipeEnergyPrefetchFacet facet = facetsByKey.get(allocation.reservationKey());
                ActivePrefetch restoredPrefetch = new ActivePrefetch(allocation.reservationKey(), facet,
                        allocation.amount());
                restoredPrefetches.add(restoredPrefetch);
                if (allocation.amount() > 0L) facet.restoreReservation(allocation.amount());
            }
        } catch (RuntimeException exception) {
            rollbackRestoredPrefetches(restoredPrefetches);
            return false;
        }
        activePrefetches = List.copyOf(restoredPrefetches);
        prefetchedEnergyPerTick = perTick;
        prefetchedEnergyRemaining = allocationTotal;
        return true;
    }

    private void savePrefetchAllocations() {
        if (activeRecipe == null) return;
        CompoundTag data = activeRecipe.getDataCompound();
        data.remove(PREFETCH_RESERVATION_KEY);
        data.remove(PREFETCH_ALLOCATIONS_KEY);
        if (activePrefetches.isEmpty()) return;
        ListTag allocations = new ListTag();
        for (ActivePrefetch prefetch : activePrefetches) {
            CompoundTag allocation = new CompoundTag();
            allocation.putString(PREFETCH_ALLOCATION_KEY, prefetch.reservationKey());
            allocation.putLong(PREFETCH_ALLOCATION_AMOUNT, prefetch.remaining());
            allocations.add(allocation);
        }
        data.put(PREFETCH_ALLOCATIONS_KEY, allocations);
    }

    private static void rollbackRestoredPrefetches(List<ActivePrefetch> restoredPrefetches) {
        for (ActivePrefetch prefetch : restoredPrefetches) {
            if (prefetch.remaining() <= 0L) continue;
            try {
                prefetch.facet().releaseReservation(prefetch.remaining());
            } catch (RuntimeException ignored) {
                // Continue releasing the remaining facets after one rollback failure.
            }
        }
    }

    private static long prefetchedEnergyPerTick(List<MachineRequirement> requirements, long parallelism) {
        long total = 0L;
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof EnergyRequirement energy && energy.io() == RecipeModifier.IOType.INPUT) {
                total = SaturatingLong.add(total, SaturatingLong.multiply(energy.fePerTick(), parallelism));
            }
        }
        return total;
    }

    private @Nullable ExecutionStatus consumePrefetchedEnergy() {
        long remaining = Math.min(prefetchedEnergyPerTick, prefetchedEnergyRemaining);
        if (remaining <= 0L) return null;
        List<ActivePrefetch> updated = new ArrayList<>(activePrefetches.size());
        for (ActivePrefetch prefetch : activePrefetches) {
            long consumed = Math.min(remaining, prefetch.remaining());
            updated.add(new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    prefetch.remaining() - consumed));
            remaining -= consumed;
        }
        if (remaining > 0L) return missingInputStatus();
        activePrefetches = List.copyOf(updated);
        prefetchedEnergyRemaining = activePrefetches.stream()
                .mapToLong(ActivePrefetch::remaining).reduce(0L, SaturatingLong::add);
        return null;
    }

    private void releaseActivePrefetches() {
        List<ActivePrefetch> prefetches = activePrefetches;
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        for (ActivePrefetch prefetch : prefetches) {
            if (prefetch.remaining() <= 0L) continue;
            try {
                prefetch.facet().releaseReservation(prefetch.remaining());
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Failed to release prefetched recipe energy: controller={} key={}",
                        controller.getBlockPos(), prefetch.reservationKey(), exception);
            }
        }
    }

    private PlanningResult planPerTick(CraftingContext context) {
        List<MachineRequirement> requirements = perTickRequirements();
        Map<Integer, OutputPolicy> outputPolicies = new LinkedHashMap<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (isPerTickOutput(requirement)) {
                outputPolicies.put(index, activeRecipe.getRecipe().allowPartialOutputs()
                        ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
            }
        }
        return context.planRequirements(requirements, activeRecipe.getParallelism(), outputPolicies);
    }

    private boolean commitAsyncTickPlan(AsyncRequirementPlanner.PlanResult planned,
                                        ControllerRuntimeSnapshot runtime) {
        if (planned == null || !planned.mainThreadRequirements().isEmpty()) {
            PlanningResult fallback = planPerTick(context(runtime));
            CraftingPlan plan = fallback.plan();
            if (!fallback.successful() || plan == null || plan.parallelism() < activeRecipe.getParallelism()) {
                waiting(fallback.failure());
                return false;
            }
            try {
                if (!plan.commit()) {
                    waiting(plan.failure());
                    return false;
                }
                ExecutionStatus prefetchFailure = consumePrefetchedEnergy();
                if (prefetchFailure != null) {
                    waiting(prefetchFailure);
                    return false;
                }
                return true;
            } catch (RuntimeException exception) {
                logTickFailure("fallback_commit", runtime, activeRecipe.getRecipe(), exception);
                waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
                return false;
            }
        }
        if (planned.operations().isEmpty()) {
            try {
                ExecutionStatus prefetchFailure = consumePrefetchedEnergy();
                if (prefetchFailure != null) {
                    waiting(prefetchFailure);
                    return false;
                }
                return true;
            } catch (RuntimeException exception) {
                logTickFailure("async_commit", runtime, activeRecipe.getRecipe(), exception);
                waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
                return false;
            }
        }
        List<AsyncPlanningFacet> facets = components.capabilities().stream()
                .map(capability -> capability.facet(AsyncPlanningFacet.class).orElse(null))
                .toList();
        try {
            for (AsyncRequirementPlanner.PlannedOperation operation : planned.operations()) {
                if (operation.capabilityIndex() >= facets.size()
                        || facets.get(operation.capabilityIndex()) == null) {
                    waiting(failure(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.PER_TICK, Map.of()));
                    return false;
                }
                CapabilityResult result = facets.get(operation.capabilityIndex()).commit(operation.operation());
                if (result == null || !result.success()) {
                    waiting(result == null ? failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of())
                            : result.status());
                    return false;
                }
            }
            ExecutionStatus prefetchFailure = consumePrefetchedEnergy();
            if (prefetchFailure != null) {
                waiting(prefetchFailure);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            logTickFailure("async_commit", runtime, activeRecipe.getRecipe(), exception);
            waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
            return false;
        }
    }

    private List<MachineRequirement> perTickRequirements() {
        if (cachedPerTickSource == effectiveRequirements && cachedPerTickConsumed == consumedAtStart
                && cachedPerTickRetained == retainedInputs && cachedPerTickPrefetches == activePrefetches) {
            return cachedPerTickRequirements;
        }
        List<MachineRequirement> requirements = new ArrayList<>();
        List<MachineRequirement> source = effectiveRequirements;
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                if (!activePrefetches.isEmpty() && requirement instanceof EnergyRequirement) continue;
                if (consumedAtStart.contains(index)) continue;
                if (retainedInputs.contains(index) && requirement instanceof ItemRequirement(
                        RecipeModifier.IOType io, net.minecraft.world.item.crafting.Ingredient item1, int count,
                        net.minecraft.world.item.ItemStack stack, float chance, List<String> tags,
                        cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet components1, float consumeChance
                )
                        && consumeChance > 0F) {
                    requirement = new ItemRequirement(io, item1, count, stack,
                            chance, tags, components1, 0F);
                }
                requirements.add(requirement);
            } else if (isPerTickOutput(requirement)) {
                requirements.add(requirement);
            }
        }
        cachedPerTickSource = effectiveRequirements;
        cachedPerTickConsumed = consumedAtStart;
        cachedPerTickRetained = retainedInputs;
        cachedPerTickPrefetches = activePrefetches;
        cachedPerTickRequirements = List.copyOf(requirements);
        return cachedPerTickRequirements;
    }

    private List<MachineRequirement> finishRequirements() {
        return effectiveRequirements().stream()
                .filter(requirement -> !isPerTickOutput(requirement))
                .toList();
    }

    private List<MachineOutput> finishOutputs(RecipeFinishContext finishContext) {
        return finishContext.outputs().stream()
                .filter(output -> !isPerTickOutput(output))
                .toList();
    }

    private static boolean isPerTickOutput(MachineRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.OUTPUT
                && (requirement instanceof EnergyRequirement
                || MekanismRecipeTypes.HEAT.equals(requirement.type().id()));
    }

    private static boolean isPerTickOutput(MachineOutput output) {
        return MekanismRecipeTypes.HEAT.equals(output.outputType().id());
    }

    private RecipeBehavior recipeBehavior(ControllerRuntimeSnapshot runtime) {
        MachineBehavior behavior = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() == null ? null : runtime.structure().configuredMachine().behavior()
                : runtime.structure().machine().behavior();
        return behavior instanceof RecipeBehavior recipeBehavior ? recipeBehavior : null;
    }

    private boolean recipeBelongsToMachine(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        return recipePoolId != null && recipePoolId.equals(recipe.recipePoolId());
    }

    private void logCallbackFailure(String phase, ControllerRuntimeSnapshot runtime, MachineRecipe recipe,
                                    RuntimeException exception) {
        MMCR.LOG.warn("Machine behavior callback failed: phase={} machine={} recipe={} controller={}", phase,
                runtime.machineId(), recipe.id(), controller.getBlockPos(), exception);
    }

    private void logFinishFailure(String phase, ControllerRuntimeSnapshot runtime, MachineRecipe recipe,
                                   RuntimeException exception) {
        MMCR.LOG.warn("Machine recipe finish failed: phase={} machine={} recipe={} controller={}", phase,
                runtime.machineId(), recipe.id(), controller.getBlockPos(), exception);
    }

    private void logTickFailure(String phase, ControllerRuntimeSnapshot runtime, MachineRecipe recipe,
                                 RuntimeException exception) {
        MMCR.LOG.warn("Machine recipe tick failed: phase={} machine={} recipe={} controller={}", phase,
                runtime.machineId(), recipe.id(), controller.getBlockPos(), exception);
    }

    private List<RecipeModifier> contextModifiers(ControllerRuntimeSnapshot runtime) {
        return MachineModifier.recipeModifiers(components.modifierList());
    }

    private EffectiveRecipe resolve(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        return new EffectiveRecipeResolver().resolve(recipe, runtime, components.modifierList());
    }

    /** Returns outputs using the same runtime modifier context as pattern-start preparation. */
    public List<MachineOutput> runtimeMachineOutputs(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        return recipe.runtimeMachineOutputs(contextModifiers(runtime));
    }

    private void captureVersions(ControllerRuntimeSnapshot runtime) {
        structureVersion = runtime.structure().version();
        capabilityVersion = runtime.capabilityVersion();
        modifierVersion = runtime.modifierVersion();
        componentStateVersion = runtime.stateVersion();
        upgradeContentRevision = runtime.upgradeContentRevision();
        resourceDomain = controller.resourceDomain();
    }

    private void captureInputState(List<MachineRequirement> requirements, CraftingPlan plan) {
        captureInputState(requirements, plan, false);
    }

    private void captureInputState(List<MachineRequirement> requirements, CraftingPlan plan, boolean prefetchesEnergy) {
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        int planIndex = 0;
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            boolean prefetchedEnergy = prefetchesEnergy && requirement instanceof EnergyRequirement
                    && requirement.io() == RecipeModifier.IOType.INPUT;
            if (!(ItemRequirement.TYPE.equals(requirement.type())
                    || FluidRequirement.TYPE.equals(requirement.type())
                    || LoadedChemicalRequirement.TYPE.equals(requirement.type()))
                    || requirement.io() != RecipeModifier.IOType.INPUT) {
                if (!prefetchedEnergy) planIndex++;
                continue;
            }
            if (plan.hasOperations(planIndex)) consumed.add(index);
            else retained.add(index);
            planIndex++;
        }
        consumedAtStart = Set.copyOf(consumed);
        retainedInputs = Set.copyOf(retained);
        List<Integer> consumedBatches = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            consumedBatches.add(consumed.contains(index) ? 1 : 0);
        }
        activeRecipe.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(consumedBatches));
    }

    private void clearEffectiveRecipe() {
        effectiveRequirements = List.of();
        effectiveOutputs = List.of();
    }

    private List<MachineRequirement> effectiveRequirements() {
        return activeRecipe != null && activeRecipe.hasEffectiveExecutionSnapshot()
                ? activeRecipe.effectiveRequirements() : effectiveRequirements;
    }

    public List<MachineOutput> activeOutputs() {
        List<MachineOutput> source = activeRecipe != null && activeRecipe.hasEffectiveExecutionSnapshot()
                ? activeRecipe.effectiveOutputs() : effectiveOutputs;
        return List.copyOf(source);
    }

    public List<MachineRequirement> activeRequirements() {
        return List.copyOf(effectiveRequirements());
    }

    private int duration(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        List<RecipeModifier> modifiers = new ArrayList<>(recipe.modifiers());
        modifiers.addAll(contextModifiers(runtime));
        return Math.max(1, IntegrationTypeHelper.asInt(
                IntegrationTypeHelper.applyDuration(modifiers, recipe.getRecipeTotalTickTime())));
    }

    private int currentGameTime() {
        if (controller.getLevel() == null) return 0;
        long gameTime = controller.getLevel().getGameTime();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime));
    }

    private long capabilityGameTime() {
        return controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
    }

    private ExecutionStatus failure(FailureReason reason, FailurePhase phase, Map<String, String> details) {
        ResourceLocation source = MMCR.id("crafting_runtime");
        return ExecutionStatus.blocked(source, source,
                FailureOccurrence.at(reason, source, phase,
                        activeRecipe == null ? null : activeRecipe.getRecipe().id(), null, details));
    }

    private record AsyncTickPreparation(ControllerRuntimeSnapshot runtime, MachineBehaviorContext machineContext,
                                        RecipeTickContext tickContext) {
    }

    private record StoredPrefetch(String reservationKey, long amount) {
    }

    private record ActivePrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet, long remaining) {
    }

    private static String failureUnloc(ExecutionStatus status) {
        if (status == null) return "";
        FailureReason reason = status.reason();
        return (reason == null ? BuiltinFailureReasons.UNKNOWN : reason).translationKey();
    }
}
