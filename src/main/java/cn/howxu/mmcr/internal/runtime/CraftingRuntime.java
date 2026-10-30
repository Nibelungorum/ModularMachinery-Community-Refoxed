package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
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
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeFinishContext;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.machine.definition.RecipeTickContext;
import cn.howxu.mmcr.api.controller.ControllerScreenText;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.create.StressRequirementHandler;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport.Sampling;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.Stage;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.VersionStamp;
import cn.howxu.mmcr.internal.recipe.RecipeProgramCompiler;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import cn.howxu.mmcr.internal.sync.FailureStatusMigration;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.SaturatingLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

/**
 * Owns one recipe lifecycle. Capability plans are the only mutable-resource boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingRuntime {
    private static final AtomicLong NEXT_CONTROLLER_ID = new AtomicLong();
    private static final AtomicLong NEXT_EXECUTION_ID = new AtomicLong();
    private static final Map<MachineControllerBlockEntity, Long> CONTROLLER_IDS = new WeakHashMap<>();
    private final MachineControllerBlockEntity controller;
    private final ComponentRuntime components;
    private StressSession stressSession = new StressSession();
    private final long controllerIdentity;
    private String laneId = "base";
    private Runnable installedCompletion = () -> { };
    private RecipeCommitLedger ledger = new RecipeCommitLedger();
    private @Nullable InstalledRecipeProgram installedProgram;
    private @Nullable ExecutionIdentity pendingCompilationIdentity;
    private @Nullable ActiveMachineRecipe acceptedRecipe;
    private @Nullable List<MachineRequirement> installedFinishRequirements;
    private @Nullable RecipeBehavior installedBehavior;
    private long preparedInstalledRound = Long.MIN_VALUE;
    private long preparedInstalledGameTime = Long.MIN_VALUE;
    private long lastInstalledPreparationGameTime = Long.MIN_VALUE;
    private long lastInstalledAttemptGameTime = Long.MIN_VALUE;
    private boolean installedPreparationReady;
    private int installedLightweightTicksForTesting;
    private int programCompileCountForTesting;
    private int installedPlanningContextBuildCountForTesting;
    private @Nullable EffectiveRecipe cachedEffectiveRecipe;
    private long cachedEffectiveStructure = Long.MIN_VALUE;
    private long cachedEffectiveCapability = Long.MIN_VALUE;
    private long cachedEffectiveModifier = Long.MIN_VALUE;
    private long cachedEffectiveState = Long.MIN_VALUE;
    private long cachedEffectiveUpgrade = Long.MIN_VALUE;
    private long cachedEffectiveCatalog = Long.MIN_VALUE;
    private long cachedEffectiveMaxParallelism = Long.MIN_VALUE;
    private int cachedEffectiveLaneLimit;
    private @Nullable MachineRecipe restartRecipe;
    private @Nullable CompiledRecipeProgram.RestartRule restartRule;
    private boolean restartProgramAuthorized;
    private long restartModifierVersion = Long.MIN_VALUE;
    private long restartStateVersion = Long.MIN_VALUE;
    private long restartUpgradeRevision = Long.MIN_VALUE;
    private @Nullable ControllerScreenText screenText;
    private @Nullable ActiveMachineRecipe activeRecipe;
    private @Nullable CraftingPlan startPlan;
    private @Nullable InputScope activeInputScope;
    private @Nullable InputScope checkedInputScope;
    private @Nullable InputRoutingFacet checkedInputRouting;
    private boolean inputScopeSourcesValid;
    private int inputScopeValidationCountForTesting;
    private @Nullable CraftingPlan finishPlan;
    private List<MachineRequirement> effectiveRequirements = List.of();
    private List<MachineOutput> effectiveOutputs = List.of();
    private @Nullable ControllerRecipePresentation cachedRecipePresentation;
    private @Nullable ActiveMachineRecipe cachedPresentationRecipe;
    private long presentationEpoch;
    private int snapshotBuildCountForTesting;
    private int recipeTickContextBuildCountForTesting;
    private int capabilityTickContextBuildCountForTesting;
    private @Nullable CraftingStateSnapshot cachedStateSnapshot;
    private @Nullable CraftingStatus.Status cachedSnapshotStatus;
    private @Nullable String cachedSnapshotStatusMessage;
    private @Nullable ExecutionStatus cachedSnapshotFailure;
    private @Nullable ResourceLocation cachedRecipeId;
    private String cachedRecipeIdText = "";
    private long cachedPresentationEpoch = Long.MIN_VALUE;
    private long cachedActiveExecutionRevision = Long.MIN_VALUE;
    private long cachedPresentationParallelism;
    private int cachedPresentationDuration;
    private @Nullable List<MachineRequirement> cachedPerTickSource;
    private @Nullable Set<Integer> cachedPerTickConsumed;
    private @Nullable Set<Integer> cachedPerTickRetained;
    private @Nullable List<ActivePrefetch> cachedPerTickPrefetches;
    private List<MachineRequirement> cachedPerTickRequirements = List.of();
    private List<Integer> cachedPerTickRequirementIndexes = List.of();
    private @Nullable List<MachineRequirement> cachedPublicRequirementSource;
    private List<MachineRequirement> cachedPublicRequirements = List.of();
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
    private boolean pendingExactPatternStart;
    private @Nullable BlockEntity pendingPatternSourceOwner;
    private boolean requiresPatternRequest;
    private @Nullable BlockPos pendingPatternSource;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingPatternDomain;
    private long pendingPatternLifecycle;
    private @Nullable PreparedStart pendingPaymentStart;
    private @Nullable PendingPrefetchStart pendingPrefetchStart;
    private @Nullable ExecutionStatus capabilityTickFailure;
    private @Nullable AsyncTickPreparation asyncTickPreparation;
    private boolean asyncTickPowerWait;
    private @Nullable TickAttempt tickAttempt;
    private boolean attemptExecuting;
    private boolean finishExecuting;
    private @Nullable ControllerScreenText lastBehaviorScreenText;
    private @Nullable RecipeFinishContext preparedAsyncFinishContext;
    private List<ActivePrefetch> activePrefetches = List.of();
    private long prefetchedEnergyPerTick;
    private long prefetchedEnergyRemaining;
    private long prefetchedEnergyTickConsumed;
    private int prefetchedEnergyConsumedTick = -1;
    private static final String PREFETCH_RESERVATION_KEY = "prefetched_energy_reservation";
    private static final String PREFETCH_ALLOCATIONS_KEY = "prefetched_energy_allocations";
    private static final String PREFETCH_ALLOCATION_KEY = "key";
    private static final String PREFETCH_ALLOCATION_AMOUNT = "amount";
    private static final String PREFETCH_BATCH_AMOUNT = "batch_amount";
    private static final String PREFETCH_UNCERTAIN_AMOUNT = "uncertain_amount";
    private static final String PREFETCH_TICK_CONSUMED_KEY = "prefetched_energy_tick_consumed";

    public CraftingRuntime(MachineControllerBlockEntity controller, ComponentRuntime components) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (components == null) throw new IllegalArgumentException("components must not be null");
        this.controller = controller;
        this.components = components;
        synchronized (CONTROLLER_IDS) {
            controllerIdentity = CONTROLLER_IDS.computeIfAbsent(controller, ignored -> NEXT_CONTROLLER_ID.incrementAndGet());
        }
    }

    public MachineControllerBlockEntity controller() { return controller; }

    public RecipeCommitLedger ledger() { return ledger; }

    public void bindRecipeLane(String laneId, Runnable completion) {
        this.laneId = Objects.requireNonNull(laneId, "laneId");
        installedCompletion = Objects.requireNonNull(completion, "completion");
    }

    public @Nullable InstalledRecipeProgram installedProgram() { return installedProgram; }

    /** A worker-compiled single-recipe template authorizes boundary instantiation at a fresh admitted parallelism. */
    public boolean restartProgramAuthorized() { return restartProgramAuthorized; }

    /** Single-recipe authorization from the previous installed program; admission still uses current resources. */
    public boolean restartProgramReady(MachineRecipe recipe) {
        return !active() && restartRecipe == recipe && restartRuleCurrent(recipe, controller.executionRuntimeSnapshot());
    }

    private boolean restartRuleCurrent(MachineRecipe recipe, ControllerRuntimeSnapshot snapshot) {
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        return restartRule != null && restartRule.recipeId().equals(recipe.id())
                && restartRule.versions().catalog() == catalogVersion(recipe.recipePoolId())
                && restartRule.versions().structure() == snapshot.structure().version()
                && restartRule.versions().capability() == snapshot.capabilityVersion()
                && restartRule.versions().domainId() == (domain == null ? 0L : domain.id())
                && restartRule.versions().domainGeneration() == (domain == null ? 0L : domain.generation())
                && restartModifierVersion == snapshot.modifierVersion() && restartStateVersion == snapshot.stateVersion()
                && restartUpgradeRevision == snapshot.upgradeContentRevision();
    }

    /** Captures live values once at an execution boundary; the compiler only receives immutable rules. */
    public @Nullable InstalledRecipeProgram ensureInstalledProgram() {
        if (!active() || errorPaused() || ledger.state().outputPaid()) return null;
        if (installedProgram != null && installedProgram.current()) return installedProgram;
        cancelInstalledProgram();
        if (!versionsCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return null;
        }
        ProgramCompilation captured = captureProgramCompilation();
        if (captured == null) return null;
        return installCompiledProgram(captured.identity(), captured.compile());
    }

    /** Pure values cross the worker boundary. @author howxu <dev@howxu.cn> */
    public record ProgramCompilation(ExecutionIdentity identity, ResourceLocation recipeId, int duration, long parallelism,
                                     VersionStamp versions, Stage start, Stage tick, Stage finish, boolean preparation) {
        public CompiledRecipeProgram compile() {
            return new RecipeProgramCompiler().compile(recipeId, duration, parallelism, versions, start, tick, finish, preparation);
        }
    }

    public @Nullable ProgramCompilation captureProgramCompilation() {
        if (!active() || errorPaused() || !versionsCurrent()) return null;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        installedBehavior = recipeBehavior(snapshot);
        if (installedBehavior == null) return null;
        // Reloaded executions keep their paid effective snapshot; restart admission uses the current source definition.
        resolve(activeRecipe.getRecipe(), snapshot);
        effectiveRequirements = MachineRequirement.copyList(activeRequirements());
        CraftingContext context = context(snapshot);
        List<MachineRequirement> tickRequirements = perTickRequirements();
        List<Integer> startIndexes = startInputIndexes(effectiveRequirements, prefetchFacets(List.of()));
        AsyncRequirementPlanner.PreparedPlan preparedStart = context.planAsync(
                startIndexes.stream().map(effectiveRequirements::get).toList(), parallelism(), startIndexes);
        Stage start = new Stage(preparedStart.requirements(), preparedStart.initialMainThreadRequirements(),
                effectiveRequirements.stream().map(ignored -> OutputPolicy.REQUIRE_FULL).toList());
        Stage tick = context.captureCompiledStage(tickRequirements, parallelism(), tickOutputPolicies(tickRequirements));
        installedFinishRequirements = null;
        if (!installedBehavior.hasBeforeFinish()) {
            // Output adapters are optional, and restored outputs may no longer be executable.
            // Defer only the finish to its existing validation/failure boundary, never omit an output.
            try {
                effectiveOutputs = MachineOutput.copyList(activeOutputs());
                installedFinishRequirements = compiledFinishRequirements(effectiveOutputs);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                // The compatibility finish will report the cause if the output is still invalid.
            }
        }
        Stage finish = installedFinishRequirements == null
                ? new Stage(List.of(), List.of(0), List.of(OutputPolicy.REQUIRE_FULL))
                : context.captureCompiledStage(installedFinishRequirements, parallelism(), outputPolicies(installedFinishRequirements));
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        VersionStamp versions = new VersionStamp(catalogVersion(activeRecipe.getRecipe().recipePoolId()),
                structureVersion, capabilityVersion, activeRecipe.effectiveExecutionRevision(),
                domain == null ? 0L : domain.id(), domain == null ? 0L : domain.generation());
        ExecutionIdentity admitted = controller.getLevel() instanceof ServerLevel level
                ? SharedIoCoordinator.get(level).acceptedIdentity(this, laneId) : null;
        pendingCompilationIdentity = admitted != null ? admitted
                : new ExecutionIdentity(controllerIdentity, controller.lifecycleEpoch(), laneId, NEXT_EXECUTION_ID.incrementAndGet());
        return new ProgramCompilation(pendingCompilationIdentity, activeRecipe.getRecipe().id(), totalTick(), parallelism(), versions,
                scopedStage(start), scopedStage(tick), scopedStage(finish),
                installedBehavior.hasRecipeTick() || components.hasTickFacets());
    }

    private Stage scopedStage(Stage stage) {
        return new Stage(stage.requirements(), stage.fallbackIndices(), stage.outputPolicies(),
                activeInputScope == null ? InputScope.legacy() : activeInputScope);
    }

    public @Nullable InstalledRecipeProgram installCompiledProgram(ExecutionIdentity identity, CompiledRecipeProgram program) {
        if (!active() || errorPaused() || !versionsCurrent() || !identity.equals(pendingCompilationIdentity) || identity.controllerInstance() != controllerIdentity
                || identity.lifecycleEpoch() != controller.lifecycleEpoch() || !identity.laneId().equals(laneId)
                || installedProgram != null || !program.recipeId().equals(activeRecipe.getRecipe().id())
                || program.parallelism() != parallelism() || program.duration() != totalTick()
                || !program.tick().inputScope().equals(activeInputScope)
                || program.versions().effective() != activeRecipe.effectiveExecutionRevision()
                || program.versions().catalog() != catalogVersion(activeRecipe.getRecipe().recipePoolId())
                || program.versions().structure() != structureVersion || program.versions().capability() != capabilityVersion) return null;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (program.versions().domainId() != (domain == null ? 0L : domain.id())
                || program.versions().domainGeneration() != (domain == null ? 0L : domain.generation())) return null;
        programCompileCountForTesting++;
        restartRule = new CompiledRecipeProgram.RestartRule(program.recipeId(), program.versions(),
                cachedEffectiveRecipe != null && cachedEffectiveRecipe.parallelized() ? Long.MAX_VALUE : 1L,
                program.start(), program.tick(), program.finish());
        restartModifierVersion = components.modifierVersion();
        restartStateVersion = components.stateVersion();
        restartUpgradeRevision = controller.currentRuntimeSnapshot().upgradeContentRevision();
        installedProgram = new InstalledRecipeProgram(identity, program, this);
        pendingCompilationIdentity = null;
        return installedProgram;
    }

    public void cancelInstalledProgram() {
        InstalledRecipeProgram previous = installedProgram;
        installedProgram = null;
        pendingCompilationIdentity = null;
        installedPreparationReady = false;
        preparedInstalledRound = Long.MIN_VALUE;
        discardAsyncTickPreparation();
        if (previous != null) previous.cancel();
    }

    boolean installedCurrent(InstalledRecipeProgram execution) {
        return execution == installedProgram && active() && !controller.isRemoved()
                && execution.identity().lifecycleEpoch() == controller.lifecycleEpoch()
                && versionsCurrent() && execution.program().parallelism() == parallelism()
                && execution.program().versions().effective() == activeRecipe.effectiveExecutionRevision()
                && execution.program().versions().catalog() == catalogVersion(activeRecipe.getRecipe().recipePoolId())
                && Objects.equals(resourceDomain, controller.resourceDomain());
    }

    /** BEFORE_RECIPE and recipeTick belong to the BE, including for both non-SYNC modes. */
    public boolean prepareInstalledTick(long roundId) {
        if (attemptExecuting || ledger.state().phase() != RecipeCommitLedger.Phase.READY
                || tickAttempt != null && !installedPreparationReady) return false;
        attemptExecuting = true;
        try {
            return prepareInstalledTickAttempt(roundId);
        } finally {
            attemptExecuting = false;
            if (!installedPreparationReady) discardAsyncTickPreparation();
        }
    }

    private boolean prepareInstalledTickAttempt(long roundId) {
        InstalledRecipeProgram execution = installedProgram;
        if (execution == null || !execution.current()) {
            execution = ensureInstalledProgram();
            if (execution == null || !execution.current()) return false;
        }
        if (finishPending()) return false;
        long gameTime = capabilityGameTime();
        if (preparedInstalledRound == roundId && preparedInstalledGameTime == gameTime) return installedPreparationReady;
        installedPreparationReady = false;
        boolean callbackFree = asyncTickPreparation == null && capabilityTickFailure == null
                && !installedBehavior.hasRecipeTick() && components.tickFacetsKnownAbsent();
        discardAsyncTickPreparation();
        if (lastInstalledPreparationGameTime == gameTime) return false;
        lastInstalledPreparationGameTime = gameTime;
        preparedInstalledRound = roundId;
        preparedInstalledGameTime = gameTime;
        if (!beginTickAttempt()) return false;
        // A custom capability may expose its facet on the next live query, including at BEFORE_RECIPE.
        if (!callbackFree && (needsRecipeTickContext(installedBehavior) || components.hasTickFacets())) {
            ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
            if (!prepareAsyncTickContext(snapshot)) return false;
            if (!executeTickPhase(CapabilityTickPhase.BEFORE_RECIPE, asyncTickPreparation) || !execution.current()) return false;
            if (installedBehavior.hasRecipeTick()) {
                ActiveMachineRecipe preparedRecipe = activeRecipe;
                try {
                    installedBehavior.recipeTick().accept(recipeTickContext(asyncTickPreparation));
                } catch (RuntimeException exception) {
                    logCallbackFailure("recipeTick", snapshot, preparedRecipe.getRecipe(), exception);
                } finally {
                    flushAsyncScreenText();
                }
            }
        } else if (capabilityTickFailure != null) {
            handleCapabilityTickResult(CapabilityTickResult.empty());
        }
        // The initial current() is reusable only across this callback-free main-thread preparation.
        // Post admission still validates again; never carry this observation across the BE→Post interval.
        installedPreparationReady = tickAttempt != null && tickAttempt.installed() == execution
                && (callbackFree ? attemptBound(tickAttempt) : attemptCurrent(tickAttempt));
        return installedPreparationReady;
    }

    boolean claimInstalledAttempt(long gameTime) {
        return !attemptExecuting && (tickAttempt == null || installedPreparationReady) && claimTickAttempt(gameTime);
    }

    private boolean claimTickAttempt(long gameTime) {
        if (lastInstalledAttemptGameTime == gameTime
                || ledger.state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID) return false;
        lastInstalledAttemptGameTime = gameTime;
        return true;
    }

    boolean installedPreparationCurrent(long roundId, long gameTime) {
        return installedPreparationReady && preparedInstalledRound == roundId && preparedInstalledGameTime == gameTime;
    }

    boolean advanceInstalled(InstalledRecipeProgram execution, boolean currentValidated) {
        if (attemptExecuting || tickAttempt != null && !installedPreparationReady) return false;
        attemptExecuting = true;
        try {
            return advanceInstalledAttempt(execution, currentValidated);
        } finally {
            installedPreparationReady = false;
            discardAsyncTickPreparation();
            attemptExecuting = false;
        }
    }

    private boolean advanceInstalledAttempt(InstalledRecipeProgram execution, boolean currentValidated) {
        if (finishPending()) {
            if (!execution.finishAttemptReady()) return false;
            finish();
            installedCompletion.run();
            return execution.ledger().state().outputPaid();
        }
        if ((!currentValidated && !execution.current()) || errorPaused()) return false;
        if (controller.activeWorkMode() == MachineWorkMode.SYNC) {
            long round = controller.getLevel() instanceof ServerLevel level
                    ? SharedIoCoordinator.get(level).executionRoundId() : 0L;
            if (!prepareInstalledTickAttempt(round)) return false;
        }
        TickAttempt attempt = tickAttempt;
        // Preparation validated live versions; prefetch and executeContinuous validate after/before native callbacks.
        if (!installedPreparationReady || preparedInstalledGameTime != capabilityGameTime()
                || !attemptBound(attempt) || attempt.installed() != execution
                || execution.ledger().state().phase() != RecipeCommitLedger.Phase.READY) return false;
        RecipeCommitLedger payment = execution.ledger();
        ActiveMachineRecipe recipe = activeRecipe;
        ExecutionStatus previousFailure = failure;
        if (canAdvanceWithoutEffects(execution, recipe)) {
            // No native operation or callback lies between the fresh admission and this grant.
            recipe.applyTickGrant(true, false, currentGameTime());
            payment.settleNoEffectsTick(recipe.getTick());
            installedLightweightTicksForTesting++;
            installedPreparationReady = false;
            status = CraftingStatus.working();
            flushAsyncScreenText();
            installedCompletion.run();
            syncInstalledProgress(recipe, payment, previousFailure);
            return true;
        }
        if (!consumePaidPrefetch(payment)) return false;
        ExecutionStatus rejected;
        try {
            rejected = execution.executeContinuous();
        } catch (RuntimeException exception) {
            pausePaymentFailure(payment, -1L, exception);
            return false;
        }
        if (!attemptCurrent(attempt)) {
            // Keep the old native payment fenced even when its callback replaced the live binding/ledger.
            if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
            return false;
        }
        if (rejected != null) {
            if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
            waiting(rejected, isPowerWait(rejected));
            return false;
        }
        if (!commitStressOutputs()) {
            if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
            return false;
        }
        payment.markResourcePaid(recipe.getTick(), recipe.needsFinishCommit());
        installedPreparationReady = false;
        completePaidTick(asyncTickPreparation, recipe, payment, attempt);
        flushAsyncScreenText();
        if (execution.finishAttemptReady()) finish();
        installedCompletion.run();
        syncInstalledProgress(recipe, payment, previousFailure);
        return true;
    }

    private boolean canAdvanceWithoutEffects(InstalledRecipeProgram execution, ActiveMachineRecipe recipe) {
        return !recipe.needsFinishCommit() && !recipe.isFinishPending() && failure == null
                && capabilityTickFailure == null && asyncTickPreparation == null && activePrefetches.isEmpty()
                && !installedBehavior.hasRecipeTick() && components.tickFacetsKnownAbsent()
                && execution.hasNoContinuousRequirements() && perTickRequirements().isEmpty();
    }

    private void syncInstalledProgress(ActiveMachineRecipe recipe, RecipeCommitLedger payment,
                                       @Nullable ExecutionStatus previousFailure) {
        if (activeRecipe == recipe && ledger == payment && !recipe.isFinishPending()
                && failure == null && previousFailure == null) controller.syncRecipeRuntimeProgress(this);
        else controller.syncRecipeRuntimeFailure(this);
    }

    int installedLightweightTicksForTesting() { return installedLightweightTicksForTesting; }

    private boolean errorPaused() { return ledger.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED; }

    int programCompileCountForTesting() { return programCompileCountForTesting; }

    int installedPlanningContextBuildCountForTesting() { return installedPlanningContextBuildCountForTesting; }

    CraftingContext bindInstalledPlanningContext() {
        installedPlanningContextBuildCountForTesting++;
        return context(controller.executionRuntimeSnapshot());
    }

    Map<Integer, OutputPolicy> installedTickOutputPolicies(List<MachineRequirement> requirements) {
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        List<Integer> indexes = installedTickIndexes();
        for (int index = 0; index < requirements.size(); index++) {
            if (isPerTickOutput(requirements.get(index))) policies.put(indexes.get(index), activeRecipe.getRecipe().allowPartialOutputs()
                    ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
        }
        return Map.copyOf(policies);
    }

    List<MachineRequirement> installedTickRequirements() {
        return perTickRequirements().stream().filter(requirement -> !isStressOutput(requirement)).toList();
    }

    List<Integer> installedTickIndexes() {
        List<MachineRequirement> requirements = perTickRequirements();
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < requirements.size(); index++) {
            if (!isStressOutput(requirements.get(index))) indexes.add(cachedPerTickRequirementIndexes.get(index));
        }
        return List.copyOf(indexes);
    }

    @Nullable List<MachineRequirement> installedFinishRequirements() { return installedFinishRequirements; }

    ExecutionStatus executeFixedTick(CraftingContext planningContext, List<MachineRequirement> requirements,
                                    List<Integer> indexes, Map<Integer, OutputPolicy> policies) {
        PlanningResult result = planningContext.planFixedRequirements(requirements, parallelism(), policies, indexes);
        if (tickAttempt != null && !attemptCurrent(tickAttempt)) return invalidInstalledStatus();
        if (!result.successful() || result.plan() == null) return result.failure() == null
                ? failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()) : result.failure();
        RecipeCommitLedger payment = ledger;
        TickAttempt attempt = tickAttempt;
        ActiveMachineRecipe recipe = activeRecipe;
        RecipeCommitLedger.State admission = payment.state();
        StressSession owner = stressSession;
        List<MachineRequirement> originalRequirements = effectiveRequirements;
        try {
            if (result.plan().commitRuntime(payment::beginNativeExecution, () -> attempt == null
                    ? installedProgram != null && installedProgram.current() : attemptCurrent(attempt))) {
                if (!hasOrdinaryNativeEffects(result.plan(), originalRequirements)) payment.completePrefetchDebit(admission);
                return null;
            }
        } catch (RuntimeException exception) {
            if (!settleStressOnlyFailure(result.plan(), originalRequirements, payment, admission, owner)) throw exception;
            logTickFailure("stress_input_commit", controller.executionRuntimeSnapshot(), recipe.getRecipe(), exception);
            return failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of());
        }
        settleStressOnlyFailure(result.plan(), originalRequirements, payment, admission, owner);
        return result.plan().failure() == null ? failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of())
                : result.plan().failure();
    }

    ExecutionStatus invalidInstalledStatus() {
        return failure(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME, Map.of());
    }

    ExecutionStatus resourceCommitFailure(boolean output, int index, long required, long available) {
        ResourceLocation source = EnergyRequirement.TYPE.id();
        return ExecutionStatus.blocked(source, source, FailureOccurrence.at(output ? BuiltinFailureReasons.MISSING_OUTPUT
                : BuiltinFailureReasons.MISSING_INPUT, source, FailurePhase.CAPABILITY_COMMIT,
                recipe() == null ? null : recipe().id(), index, Map.of("required", Long.toString(required),
                        "available", Long.toString(available), "shortfall", Long.toString(Math.max(0L, required - available)))));
    }

    private Map<Integer, OutputPolicy> tickOutputPolicies(List<MachineRequirement> requirements) {
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (int index = 0; index < requirements.size(); index++) {
            if (isPerTickOutput(requirements.get(index))) policies.put(index, activeRecipe.getRecipe().allowPartialOutputs()
                    ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
        }
        return policies;
    }

    private Map<Integer, OutputPolicy> outputPolicies(List<MachineRequirement> requirements) {
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (int index = 0; index < requirements.size(); index++) {
            if (requirements.get(index).io() == IOType.OUTPUT) policies.put(index, activeRecipe.getRecipe().allowPartialOutputs()
                    ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
        }
        return policies;
    }

    private List<MachineRequirement> compiledFinishRequirements(List<MachineOutput> sourceOutputs) {
        List<MachineRequirement> requirements = new ArrayList<>();
        List<MachineOutput> outputs = sourceOutputs.stream()
                .filter(output -> output == null || output.outputType() == null || !isPerTickOutput(output)).toList();
        int outputIndex = 0;
        for (MachineRequirement requirement : effectiveRequirements()) {
            if (requirement.io() != IOType.OUTPUT || requirement instanceof AirRequirement || isPerTickOutput(requirement)) continue;
            if (!OutputRegistry.matchesOutputRequirement(requirement)) requirements.add(requirement);
            else if (outputIndex < outputs.size()) {
                requirements.add(finishRequirement(outputs.get(outputIndex++), requirement.tags()));
            }
        }
        while (outputIndex < outputs.size()) {
            requirements.add(finishRequirement(outputs.get(outputIndex++), List.of()));
        }
        return List.copyOf(requirements);
    }

    private MachineRequirement finishRequirement(MachineOutput output, List<String> tags) {
        if (output == null || !Float.isFinite(output.chance()) || output.chance() < 0F || output.chance() > 1F
                || output instanceof MachineOutput.ItemOutput item && item.stack().isEmpty()
                || output instanceof MachineOutput.FluidOutput fluid && fluid.stack().isEmpty()) {
            throw new IllegalArgumentException("Recipe outputs must contain valid, non-empty values");
        }
        MachineRequirement requirement = OutputRegistry.toRequirement(output, tags);
        if (requirement == null || requirement.io() != IOType.OUTPUT
                || requirement instanceof ItemRequirement item && item.stack().isEmpty()
                || requirement instanceof FluidRequirement fluid && fluid.stack().isEmpty()) {
            throw new IllegalArgumentException("Output type must produce an output requirement: " + output.outputType().id());
        }
        return requirement;
    }

    private void pausePaymentFailure(RecipeCommitLedger payment, long previousSequence, RuntimeException exception) {
        boolean current = ledger == payment;
        payment.pauseAfterCommitFailure();
        MMCR.LOG.warn("Native recipe payment is uncertain: controller={}", controller.getBlockPos(), exception);
        if (!current) return;
        releaseStressContributions();
        failure = failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of());
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    private boolean consumePaidPrefetch(RecipeCommitLedger payment) {
        if (activePrefetches.isEmpty()) return true;
        TickAttempt attempt = tickAttempt;
        ExecutionStatus rejected;
        try {
            rejected = consumePrefetchedEnergy();
        } catch (RuntimeException exception) {
            // A successful debit publishes its credit before invoking reservation-owner callbacks.
            // A later callback failure must neither erase that credit nor replay an ordinary I/O batch.
            if (ledger != payment || activeRecipe != attempt.recipe()) return false;
            if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
            logTickFailure("prefetch_notification", controller.currentRuntimeSnapshot(), attempt.recipe().getRecipe(), exception);
            waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()), false);
            return false;
        }
        if (!attemptIdentityCurrent(attempt)) return false;
        if (rejected == null) return attemptCurrent(attempt);
        // Per-allocation credit already records every successful debit. Retry only unpaid credit.
        waiting(rejected, false);
        return false;
    }

    private void settleGrant(ActiveMachineRecipe recipe, RecipeCommitLedger payment) {
        if (recipe.isFinishPending()) stressSession.onRecipeFinished();
        payment.settleTick(recipe.getTick(), recipe.isFinishPending());
        if (asyncTickPreparation != null && asyncTickPreparation.recipe == recipe
                && asyncTickPreparation.attempt.payment() == payment) asyncTickPreparation.paidTickSettled = true;
    }

    private void completePaidTick(@Nullable AsyncTickPreparation preparation, ActiveMachineRecipe recipe,
                                  RecipeCommitLedger payment, TickAttempt attempt) {
        // Both callers validated immediately before entering; no external operation intervenes.
        if (!attemptBound(attempt)) return;
        if (preparation == null && components.hasTickFacets()) {
            preparation = prepareTick(controller.currentRuntimeSnapshot(), installedBehavior);
        }
        if (preparation != null && !executeTickPhase(CapabilityTickPhase.AFTER_INPUTS, preparation)) {
            if (attemptCurrent(attempt)) {
                recipe.applyTickGrant(true, false, currentGameTime());
                settleGrant(recipe, payment);
            }
            return;
        }
        if (!attemptCurrent(attempt)) return;
        int gameTime = currentGameTime();
        boolean finalTick = recipe.needsFinishCommit();
        if (!finalTick) {
            recipe.applyTickGrant(true, false, gameTime);
            settleGrant(recipe, payment);
        }
        if (preparation != null && !executeTickPhase(CapabilityTickPhase.AFTER_RECIPE, preparation)) {
            if (attemptCurrent(attempt)) {
                if (finalTick) recipe.applyTickGrant(true, false, gameTime);
                settleGrant(recipe, payment);
            }
            return;
        }
        if (preparation != null && !attemptCurrent(attempt)) return;
        if (finalTick) recipe.beginFinishCommit();
        if (recipe.isFinishPending()) stressSession.onRecipeFinished();
        settleGrant(recipe, payment);
        failure = null;
        status = CraftingStatus.working();
    }

    ExecutionStatus resourceFailure(boolean output, int index, long required, long available) {
        ResourceLocation source = EnergyRequirement.TYPE.id();
        return ExecutionStatus.blocked(source, source, FailureOccurrence.at(output ? BuiltinFailureReasons.MISSING_OUTPUT
                : BuiltinFailureReasons.MISSING_ENERGY, source, FailurePhase.REQUIREMENT_PLAN,
                recipe() == null ? null : recipe().id(), index, Map.of("required", Long.toString(required),
                        "available", Long.toString(available), "shortfall", Long.toString(Math.max(0L, required - available)))));
    }

    public void setScreenText(@Nullable ControllerScreenText screenText) {
        this.screenText = screenText;
    }

    /** Prepares a pattern start without consuming resources or occupying this runtime. */
    public @Nullable PreparedStart preparePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                       List<MachineCapability> requestCapabilities) {
        return preparePatternStart(recipe, requestedParallelism, requestCapabilities, null);
    }

    /** Plans the whole external request on this runtime, never a prefix or another lane. */
    public @Nullable PreparedStart prepareExactPatternStart(MachineRecipe recipe, long recipeParallelism,
                                                            List<MachineCapability> requestCapabilities,
                                                            @Nullable BlockPos requestSource) {
        PreparedStart prepared = preparePatternStart(recipe, recipeParallelism, requestCapabilities, requestSource);
        if (prepared == null) return null;
        if (prepared.plan().parallelism() != recipeParallelism
                || !strictPatternAmounts(prepared.effective(), recipeParallelism)
                || !samePromisedOutputs(prepared.recipe().outputs(), prepared.effective().outputs(), recipeParallelism)
                || !exactPatternOutputsFit(prepared, recipeParallelism)
                || pendingPatternStart != prepared || !preparedStartCurrent(prepared)) {
            releasePatternStart(prepared);
            return null;
        }
        pendingExactPatternStart = true;
        return prepared;
    }

    private boolean exactPatternOutputsFit(PreparedStart prepared, long parallelism) {
        try {
            PlanningResult output = new CraftingContext(components.capabilitySnapshot(), contextModifiers(prepared.runtime()))
                    .withReservationOwner(stressSession)
                    .planOutputs(prepared.recipe().source(), prepared.effective().outputs().stream()
                            .map(value -> value.withChance(1F)).toList(), parallelism);
            return output.successful() && output.plan() != null && output.plan().parallelism() == parallelism
                    && output.plan().outputSimulations().stream().allMatch(result -> result.accepted() == result.requested());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean requiresPatternRequest() { return requiresPatternRequest; }

    /** Compares nominal output totals and probabilities without constructing scaled stacks or sampling. */
    public static boolean samePromisedOutputs(List<MachineOutput> expected, List<MachineOutput> actual,
                                              long parallelism) {
        if (parallelism <= 0L) return false;
        try {
            return promisedOutputTotals(expected, parallelism).equals(promisedOutputTotals(actual, parallelism));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Map<Object, Long> promisedOutputTotals(List<MachineOutput> outputs, long parallelism) {
        Map<Object, Long> totals = new LinkedHashMap<>();
        for (MachineOutput output : outputs) {
            if (output instanceof MachineOutput.ItemOutput item && item.components().hasNonExactValues()) {
                throw new IllegalArgumentException("Non-exact output components");
            }
            long amount = MachineOutput.scaledAmount(output);
            if (amount <= 0L) throw new IllegalArgumentException("Invalid output amount");
            Object key;
            if (output instanceof LoadedChemicalOutput chemical) {
                key = List.of(chemical.outputType().id(), chemical.id());
            } else if (output instanceof LightningOutput lightning) {
                key = List.of(lightning.outputType().id(), lightning.tier());
            } else {
                key = MachineOutput.aggregationKey(output);
                if (key.equals(MachineOutput.AggregationKey.EMPTY)) {
                    throw new IllegalArgumentException("Unknown output identity");
                }
            }
            // Two independent chance rolls are not interchangeable with one larger chance stack.
            if (output.chance() != 1F) key = List.of(key, amount);
            totals.merge(List.of(key, output.chance()), Math.multiplyExact(amount, parallelism), Math::addExact);
        }
        return totals;
    }

    private boolean strictPatternAmounts(RecipeStartContext.ExecutionSnapshot effective, long parallelism) {
        try {
            Map<Object, Long> totals = new LinkedHashMap<>();
            var registries = registryAccess();
            var ops = registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);
            for (MachineRequirement requirement : effective.requirements()) {
                long amount;
                Object key;
                if (requirement instanceof ItemRequirement item) {
                    amount = item.io() == IOType.INPUT ? item.count() : item.stack().getCount();
                    if (item.io() == IOType.INPUT) {
                        if (item.item() == null) return false;
                        key = List.of(item.type().id(), item.io(), Ingredient.CODEC.encodeStart(ops, item.item()).getOrThrow(),
                                item.components(), item.tags());
                    } else key = MachineOutput.aggregationKey(new MachineOutput.ItemOutput(item.stack(), 1F, item.components()));
                } else if (requirement instanceof FluidRequirement fluid) {
                    amount = fluid.io() == IOType.INPUT ? fluid.amount() : fluid.stack().getAmount();
                    if (fluid.io() == IOType.INPUT) {
                        if (fluid.fluid() == null) return false;
                        key = List.of(fluid.type().id(), fluid.io(), FluidIngredient.CODEC.encodeStart(ops, fluid.fluid()).getOrThrow(),
                                fluid.tags());
                    } else key = MachineOutput.aggregationKey(new MachineOutput.FluidOutput(fluid.stack(), 1F));
                } else if (requirement instanceof EnergyRequirement energy) {
                    amount = Math.multiplyExact(energy.fePerTick(), (long) effective.duration());
                    key = List.of(energy.type().id(), energy.io());
                } else if (requirement instanceof LightningRequirement lightning) {
                    amount = lightning.amount();
                    key = List.of(lightning.type().id(), lightning.io(), lightning.tier());
                } else if (requirement instanceof LoadedChemicalRequirement chemical) {
                    amount = chemical.ingredient().amount();
                    key = List.of(chemical.type().id(), chemical.io(), chemical.ingredient().kind(), chemical.ingredient().id(), chemical.tags());
                } else continue;
                if (amount < 0L) return false;
                totals.merge(key, Math.multiplyExact(amount, parallelism), Math::addExact);
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public @Nullable PreparedStart preparePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                       List<MachineCapability> requestCapabilities,
                                                       @Nullable BlockPos requestSource) {
        return preparePatternStart(recipe, requestedParallelism, requestCapabilities, requestSource, null);
    }

    /** Rebuilds transient inputs at most at q, the admitted p, and finally one copy. */
    public @Nullable PreparedStart preparePendingPatternStart(MachineRecipe recipe, long requestedParallelism,
                                                              LongFunction<List<MachineCapability>> capabilitiesForParallelism,
                                                              @Nullable BlockPos requestSource) {
        if (capabilitiesForParallelism == null) return null;
        return preparePatternStart(recipe, requestedParallelism, null, requestSource, capabilitiesForParallelism);
    }

    private @Nullable PreparedStart preparePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                        List<MachineCapability> requestCapabilities,
                                                        @Nullable BlockPos requestSource,
                                                        @Nullable LongFunction<List<MachineCapability>> capabilitiesForParallelism) {
        if (recipe == null || requestedParallelism <= 0 || active() || errorPaused() || patternStartReserved
                || pendingPatternStart != null || !patternOwnerCurrent()) return null;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        long lifecycle = controller.lifecycleEpoch();
        RecipeCommitLedger originalLedger = ledger;
        BlockEntity sourceOwner = requestSource == null || controller.getLevel() == null ? null
                : controller.getLevel().getBlockEntity(requestSource);
        long isolationChangeVersion = components.inputIsolationChangeVersion();
        long catalogVersion = catalogVersion(recipe.recipePoolId());
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (!recipeBelongsToMachine(recipe)
                || requestSource != null && !runtime.linkedPortPositions().contains(requestSource)
                || !runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) return null;
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return null;
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        if (AsyncRequirementPlanner.capturedRequirementFailure(runtime, effectiveRecipe.requirements()) != null) return null;
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> recipeRequirements = effectiveRecipe.requirements();
        List<MachineOutput> outputs = effectiveRecipe.outputs();
        RecipeStartContext.ExecutionSnapshot effective;
        if (behavior.hasBeforeStart()) {
            MachineBehaviorContext machineContext = behaviorContext();
            RecipeStartContext startContext = new RecipeStartContext(machineContext, recipe, requestedParallelism,
                    effectiveParallelism, effectiveRecipe.duration(),
                    MachineRequirement.copyList(recipeRequirements), outputs);
            try {
                behavior.beforeStart().accept(startContext);
            } catch (RuntimeException exception) {
                logCallbackFailure("beforeStart", runtime, recipe, exception);
                return null;
            } finally {
                flushScreenTextReplacements(machineContext.screenText());
            }
            if (startContext.cancelled()) {
                stopEnergyPrefetch();
                for (RecipeEnergyPrefetchFacet facet : prefetchFacets(requestCapabilities)) facet.onPatternPrefetchCancelled();
                return null;
            }
            effective = startContext.snapshot();
        } else {
            effective = new RecipeStartContext.ExecutionSnapshot(effectiveRecipe.duration(),
                    MachineRequirement.copyList(recipeRequirements), outputs);
        }
        if (!startAttemptCurrent(lifecycle, originalLedger) || !patternOwnerCurrent() || !patternSourceCurrent(sourceOwner)
                || pendingPatternStart != null || pendingPaymentStart != null) return null;
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        if (current.structure().version() != runtime.structure().version()
                || current.capabilityVersion() != runtime.capabilityVersion()
                || current.modifierVersion() != runtime.modifierVersion()
                || current.upgradeContentRevision() != runtime.upgradeContentRevision()
                || current.stateVersion() - runtime.stateVersion()
                != components.inputIsolationChangeVersion() - isolationChangeVersion
                || catalogVersion != catalogVersion(recipe.recipePoolId())
                || !Objects.equals(domain, controller.resourceDomain())
                || !recipeBelongsToMachine(recipe)
                || !current.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())
                || requestSource != null && !current.linkedPortPositions().contains(requestSource)) return null;
        runtime = current;
        List<MachineRequirement> requirements = effective.requirements().stream()
                .map(MachineRequirement::copyOf).toList();
        if (AsyncRequirementPlanner.capturedRequirementFailure(runtime, requirements) != null) return null;
        long viewParallelism = effectiveParallelism;
        for (int attempt = 0; attempt < (capabilitiesForParallelism == null ? 1 : 3); attempt++) {
            if (capabilitiesForParallelism != null) {
                requestCapabilities = capabilitiesForParallelism.apply(viewParallelism);
                if (requestCapabilities == null) return null;
            }
            List<RecipeEnergyPrefetchFacet> facets = prefetchFacets(requestCapabilities);
            CraftingContext patternContext = new CraftingContext(components.capabilitySnapshot(), contextModifiers(runtime))
                    .withReservationOwner(stressSession);
            if (requestSource == null) {
                if (requestCapabilities != null && !requestCapabilities.isEmpty()) {
                    if (patternContext.inputRouting() != null) return null;
                    patternContext = legacyPatternContext(runtime, requestCapabilities);
                }
            } else {
                if (patternContext.inputRouting() != null && patternContext.inputRouting().inputPorts().stream()
                        .noneMatch(port -> port.position().equals(requestSource))) return null;
                patternContext = patternContext.forPatternStart(requestCapabilities, requestSource);
            }
            PlanningResult result = planStartInputs(patternContext, requirements, facets, viewParallelism);
            if (ledger != originalLedger || lifecycle != controller.lifecycleEpoch() || active() || patternStartReserved) return null;
            CraftingPlan plan = result.plan();
            if (capabilitiesForParallelism != null
                    && (!result.successful() || plan == null || plan.parallelism() != viewParallelism)) {
                if (viewParallelism == 1L) {
                    restoreFailure(result.failure() == null ? missingInputStatus() : result.failure());
                    return null;
                }
                viewParallelism = attempt == 0 && result.successful() && plan != null
                        && plan.parallelism() > 0L && plan.parallelism() < viewParallelism ? plan.parallelism() : 1L;
                continue;
            }
            if (!result.successful() || plan == null) {
                restoreFailure(result.failure() == null ? missingInputStatus() : result.failure());
                return null;
            }
            for (RecipeEnergyPrefetchFacet facet : facets) {
                facet.onRecipeSearchStarted(this);
                facet.onRecipeSearchCandidate(this, true);
            }
            List<PreparedPrefetch> prefetches = null;
            boolean published = false;
            try {
                try {
                    prefetches = planPrefetches(requirements, effective.duration(), plan.parallelism(), facets);
                } finally {
                    for (RecipeEnergyPrefetchFacet facet : facets) {
                        facet.onRecipeSearchFinished(this, prefetches != null, true);
                    }
                }
                if (prefetches == null) {
                    if (capabilitiesForParallelism == null || viewParallelism == 1L || attempt >= 2) return null;
                    viewParallelism = 1L;
                    continue;
                }
                PreparedStart prepared = new PreparedStart(effectiveRecipe, runtime, catalogVersion, effective, plan, prefetches,
                        requestCapabilities == null ? List.of() : requestCapabilities, !facets.isEmpty());
                if (!startAttemptCurrent(lifecycle, originalLedger)
                        || !patternOwnerCurrent() || !patternSourceCurrent(sourceOwner) || pendingPatternStart != null || pendingPaymentStart != null
                        || patternStartReserved || !Objects.equals(domain, controller.resourceDomain())
                        || !preparedStartCurrent(prepared)
                        || requestSource != null && !controller.currentRuntimeSnapshot().linkedPortPositions().contains(requestSource)) {
                    return null;
                }
                pendingExactPatternStart = false;
                pendingPatternSourceOwner = sourceOwner;
                pendingPatternSource = requestSource == null ? null : requestSource.immutable();
                pendingPatternDomain = domain;
                pendingPatternLifecycle = lifecycle;
                ledger = new RecipeCommitLedger();
                pendingPatternStart = prepared;
                published = true;
                return pendingPatternStart;
            } finally {
                if (!published && prefetches != null) releaseUnpaidPrefetches(prefetches);
            }
        }
        return null;
    }

    /** Request-only retained materials are checked before their transient views are returned to AE. */
    private static Set<Integer> requestValidatedInputs(List<MachineRequirement> requirements, long parallelism,
                                                       List<MachineCapability> requestCapabilities) {
        if (requestCapabilities == null || requestCapabilities.isEmpty()) return Set.of();
        var context = new CraftingContext(new CapabilitySnapshot(requestCapabilities));
        Set<Integer> validated = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (requirement.io() != IOType.INPUT
                     || !(requirement instanceof ItemRequirement item && item.consumeChance() == 0F
                    || requirement instanceof FluidRequirement fluid && fluid.consumeChance() == 0F
                    || requirement instanceof LoadedChemicalRequirement chemical && chemical.consumeChance() == 0F)) continue;
            PlanningResult result = context.planInputs(List.of(requirement), parallelism, Set.of(), Set.of());
            if (result.successful() && result.plan() != null && result.plan().parallelism() == parallelism) validated.add(index);
        }
        return Set.copyOf(validated);
    }

    public boolean reservePatternStart() {
        if (active() || errorPaused() || patternStartReserved || !reserveStartSlot()) return false;
        patternStartReserved = true;
        return true;
    }

    public void releasePatternStart() {
        PreparedStart prepared = pendingPatternStart;
        pendingPatternStart = null;
        pendingExactPatternStart = false;
        pendingPatternSourceOwner = null;
        if (pendingPaymentStart == prepared) pendingPaymentStart = null;
        pendingPatternSource = null;
        pendingPatternDomain = null;
        patternStartReserved = false;
        releaseStartSlot();
        if (!active()) releaseStressContributions();
        if (prepared != null) releasePreparedPrefetches(prepared);
    }

    /** Returns false when a replacement pattern owns the slot; an already cleared slot needs no runtime release. */
    public boolean releasePatternStart(PreparedStart prepared) {
        if (prepared == null) return false;
        if (pendingPatternStart != prepared) return pendingPatternStart == null;
        releasePatternStart();
        return true;
    }

    public @Nullable ResourceLocation pendingPatternRecipeId() {
        return pendingPatternStart == null ? null : pendingPatternStart.recipe().source().id();
    }

    public boolean commitPatternStart(PreparedStart prepared, Runnable ownershipWrites) {
        Objects.requireNonNull(ownershipWrites, "ownershipWrites");
        if (!patternStartReserved || active() || prepared == null || prepared != pendingPatternStart) return false;
        RecipeCommitLedger payment = ledger;
        boolean complete = false;
        try {
            complete = commitPatternPlan(prepared);
        } catch (RuntimeException exception) {
            if (payment.state().phase() == RecipeCommitLedger.Phase.READY) throw exception;
            payment.pauseAfterCommitFailure();
            if (ledger == payment) quarantineNativeStart(prepared, payment);
        }
        boolean accepted = complete || payment.state().phase() != RecipeCommitLedger.Phase.READY;
        if (!accepted) {
            discardPatternStart(prepared);
            return false;
        }
        try {
            ownershipWrites.run();
        } catch (RuntimeException exception) {
            payment.pauseAfterCommitFailure();
            if (ledger == payment) quarantineNativeStart(prepared, payment);
            MMCR.LOG.warn("Native pattern ownership callback failed: controller={}", controller.getBlockPos(), exception);
        }
        if (complete && ledger == payment && payment.state().phase() != RecipeCommitLedger.Phase.ERROR_PAUSED) {
            activatePatternStart(prepared);
        } else if (pendingPatternStart == prepared) releasePatternStart(prepared);
        else releasePreparedPrefetches(prepared);
        return true;
    }

    public boolean commitPatternStart(PreparedStart prepared) {
        return commitPatternStart(prepared, () -> { });
    }

    boolean commitPatternPlan(PreparedStart prepared) {
        return commitPatternPlan(prepared, () -> true);
    }

    boolean commitPatternPlan(PreparedStart prepared, BooleanSupplier admissionCurrent) {
        if (!patternStartReserved || active() || prepared == null || prepared != pendingPatternStart) return false;
        if (!preparedStartCurrent(prepared) || pendingExactPatternStart
                && !exactPatternOutputsFit(prepared, prepared.plan().parallelism())) {
            discardPatternStart(prepared);
            return false;
        }
        // Simulation can invoke native handlers: recheck identity and lane/core admission afterwards.
        if (!patternStartReserved || active() || pendingPatternStart != prepared || !preparedStartCurrent(prepared)
                || !admissionCurrent.getAsBoolean()) {
            discardPatternStart(prepared);
            return false;
        }
        pendingPaymentStart = prepared;
        RecipeCommitLedger payment = ledger;
        long lifecycle = controller.lifecycleEpoch();
        try {
            ExecutionStatus commitFailure = commitPreparedStart(prepared);
            if (commitFailure == null) return true;
            if (ledger != payment || lifecycle != controller.lifecycleEpoch()) return false;
            quarantineNativeStart(prepared, payment);
            fail(commitFailure);
            return false;
        } finally {
            // External Pattern callers retain this frame independently of the runtime's current slot.
            if (ledger != payment || lifecycle != controller.lifecycleEpoch() || pendingPaymentStart != prepared) {
                releasePreparedPrefetches(prepared);
            }
        }
    }

    /** The caller retains this journal through notifications, even if cancellation replaces the runtime ledger. */
    @Nullable RecipeCommitLedger patternStartPayment(PreparedStart prepared) {
        PendingPrefetchStart payment = pendingPrefetchStart;
        return payment != null && payment.prepared() == prepared ? payment.payment() : null;
    }

    void releasePreparedPrefetches(PreparedStart prepared) {
        if (prepared == null) return;
        PendingPrefetchStart payment = pendingPrefetchStart;
        if (payment != null && payment.prepared() == prepared) {
            if (payment.payment().state().paidAttempts() > 0L) {
                releasePaidStartPrefetches(prepared);
                return;
            }
            pendingPrefetchStart = null;
        }
        releaseUnpaidPrefetches(prepared.prefetches());
    }

    private void releaseUnpaidPrefetches(List<PreparedPrefetch> prefetches) {
        for (PreparedPrefetch prefetch : prefetches) {
            if (prefetch.released || prefetch.transferred || prefetch.inFlight) continue;
            try {
                long acquired = prefetch.confirmedAmount();
                // An opaque throwing operation cannot authorize releasing somebody else's reservation.
                if (acquired < 0L) continue;
                prefetch.released = true;
                if (acquired > 0L && prefetch.facet().releaseReservation(acquired) != acquired) prefetch.releaseFailed = true;
                if (acquired < prefetch.plan().amount()) prefetch.facet().restoreReservation(prefetch.plan().amount() - acquired);
            } catch (RuntimeException exception) {
                prefetch.releaseFailed = true;
                MMCR.LOG.warn("Failed to release native start ownership: controller={} key={}",
                        controller.getBlockPos(), prefetch.reservationKey(), exception);
            }
        }
    }

    private void releasePaidStartPrefetches(PreparedStart prepared) {
        if (pendingPrefetchStart != null && pendingPrefetchStart.prepared() == prepared) pendingPrefetchStart = null;
        releasePreparedPrefetches(prepared);
    }

    private void discardPatternStart(PreparedStart prepared) {
        if (pendingPatternStart == prepared) releasePatternStart();
        else releasePreparedPrefetches(prepared);
    }

    boolean preparedStartCurrent(PreparedStart prepared) {
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return prepared.runtime().structure().version() == current.structure().version()
                && (prepared != pendingPatternStart || patternOwnerCurrent()
                && pendingPatternLifecycle == controller.lifecycleEpoch()
                && patternSourceCurrent(pendingPatternSourceOwner)
                && Objects.equals(pendingPatternDomain, controller.resourceDomain())
                && (pendingPatternSource == null || current.linkedPortPositions().contains(pendingPatternSource)))
                && prepared.runtime().capabilityVersion() == current.capabilityVersion()
                && prepared.runtime().modifierVersion() == current.modifierVersion()
                && prepared.runtime().stateVersion() == current.stateVersion()
                && (prepared.plan().inputRoutingRevision() < 0L
                || prepared.plan().inputRoutingRevision() == components.inputRoutingRevision())
                && recipeBelongsToMachine(prepared.recipe().source())
                && current.moduleConnectionStatus().canRunRecipe(prepared.recipe().source().requiredHostIds())
                && prepared.catalogVersion() == catalogVersion(prepared.recipe().source().recipePoolId());
    }

    private boolean patternOwnerCurrent() {
        return !controller.isRemoved() && (!(controller.getLevel() instanceof ServerLevel level)
                || level.getBlockEntity(controller.getBlockPos()) == controller);
    }

    private boolean patternSourceCurrent(@Nullable BlockEntity owner) {
        return owner == null || !owner.isRemoved() && controller.getLevel() != null
                && controller.getLevel().getBlockEntity(owner.getBlockPos()) == owner;
    }

    private static long catalogVersion(ResourceLocation recipePoolId) {
        return RecipeRegistry.catalogForPool(recipePoolId).version();
    }

    boolean activatePatternStart(PreparedStart prepared) {
        PendingPrefetchStart payment = pendingPrefetchStart;
        if (payment == null || payment.prepared() != prepared || payment.payment().state().paidAttempts() == 0L) return false;
        if (payment.payment() != ledger || payment.lifecycle() != controller.lifecycleEpoch()) {
            releasePaidStartPrefetches(prepared);
            return false;
        }
        pendingPrefetchStart = null;
        cancelInstalledProgram();
        pendingPaymentStart = null;
        activeRecipe = acceptedRecipe;
        requiresPatternRequest = pendingExactPatternStart;
        activeRecipe.setParallelism(prepared.plan().parallelism());
        startPlan = prepared.plan();
        setActiveInputScope(prepared.plan().inputScope());
        finishPlan = null;
        effectiveRequirements = MachineRequirement.copyList(prepared.effective().requirements().stream()
                .map(MachineRequirement::copyOf).toList());
        effectiveOutputs = MachineOutput.copyList(prepared.effective().outputs());
        presentationEpoch++;
        ActiveMachineRecipe activatingRecipe = activeRecipe;
        activatePrefetches(prepared.prefetches(), effectiveRequirements, activeRecipe.getParallelism());
        if (ledger != payment.payment() || activeRecipe != activatingRecipe
                || controller.lifecycleEpoch() != payment.lifecycle()) return false;
        Set<Integer> admittedInputs = new HashSet<>();
        for (int index = 0; index < effectiveRequirements.size(); index++) {
            if (activeRecipe.inputConsumptionPlan().consumedBatches(index) > 0) admittedInputs.add(index);
        }
        captureInputState(effectiveRequirements, prepared.plan(), admittedInputs);
        captureVersions(prepared.runtime());
        pendingPatternStart = null;
        pendingExactPatternStart = false;
        pendingPatternSourceOwner = null;
        pendingPatternSource = null;
        pendingPatternDomain = null;
        patternStartReserved = false;
        status = CraftingStatus.working();
        failure = null;
        ledger.settleTick(0, false);
        if (errorPaused()) restoreFailure(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
        controller.onPatternStartCommitted();
        return true;
    }

    public record PreparedStart(EffectiveRecipe recipe, ControllerRuntimeSnapshot runtime, long catalogVersion,
                                RecipeStartContext.ExecutionSnapshot effective, CraftingPlan plan,
                                List<PreparedPrefetch> prefetches, List<MachineCapability> requestCapabilities,
                                boolean energyInputsExcluded) {
        public PreparedStart {
            prefetches = List.copyOf(prefetches);
            requestCapabilities = List.copyOf(requestCapabilities);
        }

        public PreparedStart(EffectiveRecipe recipe, ControllerRuntimeSnapshot runtime, long catalogVersion,
                             RecipeStartContext.ExecutionSnapshot effective, CraftingPlan plan,
                             List<PreparedPrefetch> prefetches, List<MachineCapability> requestCapabilities) {
            this(recipe, runtime, catalogVersion, effective, plan, prefetches, requestCapabilities, !prefetches.isEmpty());
        }

        public PreparedStart(EffectiveRecipe recipe, ControllerRuntimeSnapshot runtime, long catalogVersion,
                             RecipeStartContext.ExecutionSnapshot effective, CraftingPlan plan,
                             List<PreparedPrefetch> prefetches) {
            this(recipe, runtime, catalogVersion, effective, plan, prefetches, List.of());
        }

        public PreparedStart(EffectiveRecipe recipe, ControllerRuntimeSnapshot runtime,
                             RecipeStartContext.ExecutionSnapshot effective, CraftingPlan plan,
                             List<PreparedPrefetch> prefetches) {
            this(recipe, runtime, CraftingRuntime.catalogVersion(recipe.source().recipePoolId()), effective, plan,
                    prefetches);
        }
    }

    static final class PreparedPrefetch {
        private final String reservationKey;
        private final RecipeEnergyPrefetchFacet facet;
        private final RecipeEnergyPrefetchFacet.PrefetchPlan plan;
        private boolean committed;
        private boolean attempted;
        private boolean inFlight;
        private boolean released;
        private boolean releaseFailed;
        private boolean transferred;

        PreparedPrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet,
                         RecipeEnergyPrefetchFacet.PrefetchPlan plan) {
            this.reservationKey = reservationKey;
            this.facet = facet;
            this.plan = plan;
        }

        String reservationKey() { return reservationKey; }

        RecipeEnergyPrefetchFacet facet() { return facet; }

        RecipeEnergyPrefetchFacet.PrefetchPlan plan() { return plan; }

        long confirmedAmount() {
            if (released || !attempted) return 0L;
            long amount = plan.committedAmount().getAsLong();
            if (amount == -1L && committed) return plan.amount();
            return amount >= 0L && amount <= plan.amount() ? amount : -1L;
        }
    }

    /** Owns start reservations until activation or cancellation, independently of a replacement ledger.
     * @author howxu <dev@howxu.cn>
     */
    private record PendingPrefetchStart(PreparedStart prepared, RecipeCommitLedger payment, long lifecycle) { }

    public CraftingStatus start(MachineRecipe recipe, long requestedParallelism) {
        return start(recipe, requestedParallelism, null);
    }

    /** Start preparation does not pay resources or admit an execution. @author howxu <dev@howxu.cn> */
    public enum PreparationResult { READY, DEFERRED, FAILED, CANCELLED }

    /** Only READY carries a prepared execution. @author howxu <dev@howxu.cn> */
    public record StartPreparation(PreparationResult result,
                                   @Nullable RecipeStartContext.ExecutionSnapshot execution) { }

    /** Executes the start behavior callback before shared-IO native admission is requested. */
    public StartPreparation prepareStartAttempt(MachineRecipe recipe, long requestedParallelism) {
        if (active() || patternStartReserved || pendingPatternStart != null || pendingPaymentStart != null) {
            return new StartPreparation(PreparationResult.DEFERRED, null);
        }
        if (recipe == null || requestedParallelism <= 0) {
            fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()));
            return new StartPreparation(PreparationResult.FAILED, null);
        }
        long lifecycle = controller.lifecycleEpoch();
        RecipeCommitLedger originalLedger = ledger;
        try {
            StartPreparation prepared = prepareStartAttempt(recipe, requestedParallelism, lifecycle, originalLedger);
            if (!startAttemptCurrent(lifecycle, originalLedger)) {
                return new StartPreparation(PreparationResult.CANCELLED, null);
            }
            if (prepared.result() == PreparationResult.READY
                    && (patternStartReserved || pendingPatternStart != null || pendingPaymentStart != null)) {
                return new StartPreparation(PreparationResult.DEFERRED, null);
            }
            return prepared;
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Recipe start preparation failed: recipe={} controller={}",
                    recipe.id(), controller.getBlockPos(), exception);
            if (!startAttemptCurrent(lifecycle, originalLedger)) {
                return new StartPreparation(PreparationResult.CANCELLED, null);
            }
            fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()));
            return new StartPreparation(PreparationResult.FAILED, null);
        }
    }

    private StartPreparation prepareStartAttempt(MachineRecipe recipe, long requestedParallelism,
                                                 long lifecycle, RecipeCommitLedger originalLedger) {
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(recipe)) {
            fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START,
                    Map.of("reason", "recipe_pool")));
            return new StartPreparation(PreparationResult.FAILED, null);
        }
        if (!runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) {
            fail(failure(BuiltinFailureReasons.MODULE_CONNECTION, FailurePhase.RECIPE_START, Map.of()));
            return new StartPreparation(PreparationResult.FAILED, null);
        }
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            fail(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.RECIPE_START, Map.of()));
            return new StartPreparation(PreparationResult.FAILED, null);
        }
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        if (!startAttemptCurrent(lifecycle, originalLedger)) {
            return new StartPreparation(PreparationResult.CANCELLED, null);
        }
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> requirements = effectiveRecipe.requirements();
        if (!behavior.hasBeforeStart()) {
            return new StartPreparation(PreparationResult.READY,
                    new RecipeStartContext.ExecutionSnapshot(effectiveRecipe.duration(),
                            MachineRequirement.copyList(requirements), effectiveRecipe.outputs()));
        }
        MachineBehaviorContext machineContext = behaviorContext();
        RecipeStartContext startContext = new RecipeStartContext(machineContext, recipe, requestedParallelism,
                effectiveParallelism, effectiveRecipe.duration(),
                MachineRequirement.copyList(requirements), effectiveRecipe.outputs());
        if (!startAttemptCurrent(lifecycle, originalLedger)) {
            return new StartPreparation(PreparationResult.CANCELLED, null);
        }
        try {
            behavior.beforeStart().accept(startContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeStart", runtime, recipe, exception);
            if (!startAttemptCurrent(lifecycle, originalLedger)) {
                return new StartPreparation(PreparationResult.CANCELLED, null);
            }
            fail(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.RECIPE_START, Map.of()));
            return new StartPreparation(PreparationResult.FAILED, null);
        } finally {
            flushScreenTextReplacements(machineContext.screenText());
        }
        if (!startAttemptCurrent(lifecycle, originalLedger)) {
            return new StartPreparation(PreparationResult.CANCELLED, null);
        }
        if (startContext.cancelled()) {
            stopEnergyPrefetch();
            failure = null;
            status = CraftingStatus.IDLE;
            return new StartPreparation(PreparationResult.CANCELLED, null);
        }
        if (patternStartReserved || pendingPatternStart != null || pendingPaymentStart != null) {
            return new StartPreparation(PreparationResult.DEFERRED, null);
        }
        return new StartPreparation(PreparationResult.READY, startContext.snapshot());
    }

    private boolean startAttemptCurrent(long lifecycle, RecipeCommitLedger payment) {
        return lifecycle == controller.lifecycleEpoch() && payment == ledger && !active();
    }

    /** Nullable compatibility bridge for callers that do not need the preparation outcome. */
    public @Nullable RecipeStartContext.ExecutionSnapshot prepareAsyncStart(MachineRecipe recipe, long requestedParallelism) {
        return prepareStartAttempt(recipe, requestedParallelism).execution();
    }

    public CraftingStatus start(MachineRecipe recipe, long requestedParallelism,
                                @Nullable RecipeStartContext.ExecutionSnapshot preparedStart) {
        if (active() || patternStartReserved || pendingPatternStart != null || pendingPaymentStart != null) return status;
        if (recipe == null || requestedParallelism <= 0) {
            return fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()));
        }
        if (errorPaused()) return status;
        long lifecycle = controller.lifecycleEpoch();
        RecipeCommitLedger originalLedger = ledger;
        PreparedStart prepared;
        List<MachineRequirement> executionRequirements;
        List<MachineOutput> executionOutputs;
        List<PreparedPrefetch> prefetches = List.of();
        try {
            if (!reserveStartSlot()) return status;
            ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
            if (!recipeBelongsToMachine(recipe)) {
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
            if (!startAttemptCurrent(lifecycle, originalLedger)) return status;
            if (effective == null) {
                releaseStartSlot();
                return status;
            }
            runtime = controller.currentRuntimeSnapshot();
            EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
            long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                    effectiveRecipe.parallelismLimit()));
            List<MachineRequirement> requirements = MachineRequirement.copyList(effective.requirements());
            // Extension copies must succeed before payment, including the copies used during activation.
            executionRequirements = MachineRequirement.copyList(requirements);
            executionOutputs = MachineOutput.copyList(effective.outputs());
            List<RecipeEnergyPrefetchFacet> facets = prefetchFacets(List.of());
            CraftingContext context = context(runtime);
            if (!startAttemptCurrent(lifecycle, originalLedger)) return status;
            boolean restarting = restartRecipe == recipe;
            // beforeStart has already run and frozen its edits. Unknown edits are executed by the full compatibility set.
            restartProgramAuthorized = restarting && restartRuleCurrent(recipe, runtime);
            if (restartProgramAuthorized) {
                effectiveParallelism = Math.min(effectiveParallelism, restartRule.maximumParallelism());
            }
            PlanningResult result = planStartInputs(context, requirements, facets, effectiveParallelism);
            if (!startAttemptCurrent(lifecycle, originalLedger)) return status;
            CraftingPlan plan = result.plan();
            if (!result.successful() || plan == null) return fail(result.failure());
            prefetches = planPrefetches(requirements, effective.duration(), plan.parallelism(), facets);
            if (!startAttemptCurrent(lifecycle, originalLedger)) {
                if (prefetches != null) releaseUnpaidPrefetches(prefetches);
                return status;
            }
            if (prefetches == null) return fail(missingInputStatus());
            prepared = new PreparedStart(effectiveRecipe, runtime,
                    catalogVersion(effectiveRecipe.source().recipePoolId()), effective, plan, prefetches,
                    List.of(), !facets.isEmpty());
        } catch (RuntimeException exception) {
            if (prefetches != null) releaseUnpaidPrefetches(prefetches);
            MMCR.LOG.warn("Recipe start planning failed: recipe={} controller={}",
                    recipe.id(), controller.getBlockPos(), exception);
            if (!startAttemptCurrent(lifecycle, originalLedger)) return status;
            return fail(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()));
        }
        ControllerRuntimeSnapshot runtime = prepared.runtime();
        CraftingPlan plan = prepared.plan();
        boolean committed = false;
        RecipeCommitLedger payment = new RecipeCommitLedger();
        ledger = payment;
        pendingPaymentStart = prepared;
        try {
            ExecutionStatus commitFailure = commitPreparedStart(prepared);
            if (!startAttemptCurrent(lifecycle, payment)) return status;
            if (commitFailure != null) {
                if (payment != ledger || lifecycle != controller.lifecycleEpoch()) return status;
                quarantineNativeStart(prepared, payment);
                return fail(commitFailure);
            }
            committed = true;
        } catch (RuntimeException exception) {
            committed = payment.state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID;
            if (!committed) {
                MMCR.LOG.warn("Native recipe start failed: controller={}", controller.getBlockPos(), exception);
                if (payment != ledger || lifecycle != controller.lifecycleEpoch()) return status;
                quarantineNativeStart(prepared, payment);
                return fail(missingInputStatus());
            }
            payment.pauseAfterCommitFailure();
            MMCR.LOG.warn("Recipe start paid before notification failure: controller={}", controller.getBlockPos(), exception);
        } finally {
            if (pendingPaymentStart == prepared) pendingPaymentStart = null;
            if (!committed) {
                if (payment == ledger && lifecycle == controller.lifecycleEpoch()) releaseStressContributions();
                releasePreparedPrefetches(prepared);
            }
        }
        if (lifecycle != controller.lifecycleEpoch() || payment != ledger || active()
                || pendingPrefetchStart == null || pendingPrefetchStart.prepared() != prepared) {
            releasePaidStartPrefetches(prepared);
            return status;
        }
        pendingPrefetchStart = null;

        cancelInstalledProgram();
        activeRecipe = acceptedRecipe;
        activeRecipe.setParallelism(plan.parallelism());
        startPlan = plan;
        setActiveInputScope(plan.inputScope());
        requiresPatternRequest = false;
        finishPlan = null;
        effectiveRequirements = executionRequirements;
        effectiveOutputs = executionOutputs;
        presentationEpoch++;
        try {
            ActiveMachineRecipe activatingRecipe = activeRecipe;
            activatePrefetches(prefetches, effectiveRequirements, activeRecipe.getParallelism());
            if (ledger != payment || activeRecipe != activatingRecipe || controller.lifecycleEpoch() != lifecycle) return status;
            captureInputState(effectiveRequirements, plan);
            captureVersions(runtime);
            status = CraftingStatus.working();
            failure = null;
            payment.settleTick(0, false);
            if (payment.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED) {
                restoreFailure(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
            }
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Recipe start activation failed after payment: recipe={} controller={}",
                    recipe.id(), controller.getBlockPos(), exception);
            payment.pauseAfterCommitFailure();
            if (lifecycle == controller.lifecycleEpoch() && payment == ledger && active()) {
                restoreFailure(failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()));
            }
        }
        return status;
    }

    public CraftingStatus tick() {
        if (attemptExecuting || !beginTickAttempt()) return status;
        TickAttempt attempt = tickAttempt;
        if (installedProgram != null && !claimTickAttempt(capabilityGameTime())) {
            tickAttempt = null;
            return status;
        }
        attemptExecuting = true;
        try {
            return tickAttempt();
        } finally {
            if (tickAttempt == attempt) tickAttempt = null;
            attemptExecuting = false;
        }
    }

    private CraftingStatus tickAttempt() {
        if (!active()) return status;
        if (errorPaused() || ledger.state().finalTickPaid()) return status;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        if (activeRecipe.isFinishPending()) return status;

        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            return waiting(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.PER_TICK, Map.of()));
        }
        AsyncTickPreparation preparation = prepareTick(runtime, behavior);
        if (!executeTickPhase(CapabilityTickPhase.BEFORE_RECIPE, preparation)) return status;
        if (!preparationCurrent(preparation)) return status;
        if (behavior.hasRecipeTick()) {
            RecipeTickContext recipeTickContext = recipeTickContext(preparation);
            try {
                behavior.recipeTick().accept(recipeTickContext);
            } catch (RuntimeException exception) {
                logCallbackFailure("recipeTick", runtime, preparation.recipe.getRecipe(), exception);
            } finally {
                flushScreenTextReplacements(recipeTickContext.machineContext().screenText());
            }
        }
        if (!preparationCurrent(preparation)) return status;
        CraftingContext context = context(runtime);
        if (!consumePaidPrefetch(ledger)) return status;
        PlanningResult result = planPerTick(context);
        if (!preparationCurrent(preparation)) return status;
        CraftingPlan tickPlan = result.plan();
        if (!result.successful() || tickPlan == null || tickPlan.parallelism() < activeRecipe.getParallelism()) {
            return waiting(result.failure());
        }
        ActiveMachineRecipe recipe = activeRecipe;
        RecipeCommitLedger payment = ledger;
        if (!commitPaidPlan(tickPlan)) return status;
        if (preparationCurrent(preparation)) completePaidTick(preparation, recipe, payment, preparation.attempt);
        if (preparation.tickContext != null) flushScreenTextReplacements(preparation.tickContext.machineContext().screenText());
        return status;
    }

    /** Captures the main-thread recipe tick context before any worker-side planning begins. */
    public boolean prepareAsyncTick(ControllerRuntimeSnapshot runtime) {
        if (attemptExecuting || !beginTickAttempt()) return false;
        attemptExecuting = true;
        boolean prepared = false;
        try {
            prepared = prepareAsyncTickContext(runtime);
            return prepared;
        } finally {
            if (!prepared) discardAsyncTickPreparation();
            attemptExecuting = false;
        }
    }

    private boolean prepareAsyncTickContext(ControllerRuntimeSnapshot runtime) {
        asyncTickPreparation = null;
        asyncTickPowerWait = false;
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        if (!active() || status.isPaused()) return false;
        if (errorPaused() || ledger.state().finalTickPaid()) return false;
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
        asyncTickPreparation = prepareTick(runtime, behavior);
        return true;
    }

    /** Executes one deferred capability tick phase against the captured recipe tick context. */
    public boolean executeAsyncCapabilityTick(CapabilityTickPhase phase) {
        if (attemptExecuting) return false;
        AsyncTickPreparation preparation = asyncTickPreparation;
        attemptExecuting = true;
        try {
            return preparation != null && executeTickPhase(phase, preparation);
        } finally {
            attemptExecuting = false;
        }
    }

    public void discardAsyncTickPreparation() {
        if (asyncTickPreparation != null && !asyncTickPreparation.paidTickSettled) {
            if (asyncTickPowerWait) stressSession.releaseOutputs();
            else releaseStressContributions();
        }
        asyncTickPreparation = null;
        asyncTickPowerWait = false;
        tickAttempt = null;
    }

    /** Runs main-thread tick preparation and captures the worker-safe native requirement plan. */
    public @Nullable AsyncRequirementPlanner.PreparedPlan prepareAsyncTickPlan(
            ControllerRuntimeSnapshot runtimeSnapshot) {
        if (attemptExecuting || !beginTickAttempt()) return null;
        attemptExecuting = true;
        AsyncRequirementPlanner.PreparedPlan planned = null;
        try {
            planned = prepareAsyncTickPlanAttempt(runtimeSnapshot);
            return planned;
        } finally {
            if (planned == null) discardAsyncTickPreparation();
            attemptExecuting = false;
        }
    }

    private @Nullable AsyncRequirementPlanner.PreparedPlan prepareAsyncTickPlanAttempt(
            ControllerRuntimeSnapshot runtimeSnapshot) {
        if (!prepareAsyncTickContext(runtimeSnapshot)) return null;
        if (!executeTickPhase(CapabilityTickPhase.BEFORE_RECIPE, asyncTickPreparation)) {
            discardAsyncTickPreparation();
            flushAsyncScreenText();
            return null;
        }
        AsyncTickPreparation preparation = asyncTickPreparation;
        RecipeBehavior behavior = preparation == null ? null : recipeBehavior(preparation.runtime());
        if (preparation == null || behavior == null) return null;
        if (behavior.hasRecipeTick()) {
            try {
                behavior.recipeTick().accept(recipeTickContext(preparation));
            } catch (RuntimeException exception) {
                logCallbackFailure("recipeTick", preparation.runtime(), preparation.recipe.getRecipe(), exception);
            } finally {
                flushAsyncScreenText();
            }
        }
        if (!preparationCurrent(preparation)) return null;
        List<MachineRequirement> requirements = perTickRequirements();
        CraftingContext context = context(preparation.runtime());
        AsyncRequirementPlanner.PreparedPlan prepared;
        if (requirements.isEmpty()) {
            prepared = new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), List.of(),
                    context.inputRouting(), activeInputScope);
        }
        // Scalar-only ticks are cheaper to plan against live storage during shared-IO arbitration.
        else if (requirements.stream().allMatch(requirement -> requirement instanceof EnergyRequirement
                || requirement instanceof LoadedHeatRequirement)) {
            prepared = new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(),
                    cachedPerTickRequirementIndexes, context.inputRouting(), activeInputScope);
        } else {
            prepared = context.planAsync(requirements, activeRecipe.getParallelism(), cachedPerTickRequirementIndexes);
        }
        if (!preparationCurrent(preparation)) return null;
        preparation.plan = prepared;
        // A known fallback replans the entire tick, so worker operations would be discarded.
        return prepared.initialMainThreadRequirements().isEmpty() ? prepared
                : new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), prepared.initialMainThreadRequirements(),
                        prepared.routing(), prepared.inputScope());
    }

    /** Flushes recipe behavior screen text after its callback has run on the server thread. */
    public void flushAsyncScreenText() {
        AsyncTickPreparation preparation = asyncTickPreparation;
        if (preparation != null && preparation.tickContext != null) {
            flushScreenTextReplacements(preparation.tickContext.machineContext().screenText());
        } else {
            ControllerScreenText text = screenText == null ? lastBehaviorScreenText : screenText;
            if (text != null) flushScreenTextReplacements(text);
        }
    }

    /** Commits worker-planned operations under shared-IO arbitration; native prefixes are not rolled back. */
    public boolean commitAsyncTick(AsyncRequirementPlanner.PlanResult planned) {
        if (attemptExecuting) return false;
        attemptExecuting = true;
        boolean committed = false;
        try {
            committed = commitAsyncTickAttempt(planned);
            return committed;
        } finally {
            if (!committed && ledger.state().phase() != RecipeCommitLedger.Phase.RESOURCE_PAID) discardAsyncTickPreparation();
            attemptExecuting = false;
        }
    }

    private boolean commitAsyncTickAttempt(AsyncRequirementPlanner.PlanResult planned) {
        AsyncTickPreparation preparation = asyncTickPreparation;
        if (preparation == null || !preparationCurrent(preparation) || errorPaused()
                || ledger.state().phase() != RecipeCommitLedger.Phase.READY) return false;
        if (!versionsCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return false;
        }
        if (installedProgram != null && !claimTickAttempt(capabilityGameTime())) return false;
        return commitAsyncTickPlan(planned, preparation.runtime());
    }

    /** Runs AFTER_INPUTS only after native resource payment has been confirmed. */
    public boolean completeAsyncTickAfterInputs() {
        if (attemptExecuting) return false;
        attemptExecuting = true;
        try {
            return completeAsyncTickAfterInputsAttempt();
        } finally {
            attemptExecuting = false;
        }
    }

    private boolean completeAsyncTickAfterInputsAttempt() {
        AsyncTickPreparation preparation = asyncTickPreparation;
        if (preparation == null || !preparationCurrent(preparation) || errorPaused()
                || ledger.state().phase() != RecipeCommitLedger.Phase.RESOURCE_PAID) return false;
        if (preparation.afterInputsAttempted) return preparation.afterInputsPassed;
        preparation.afterInputsAttempted = true;
        RecipeCommitLedger payment = ledger;
        if (!versionsCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return false;
        }
        if (!executeTickPhase(CapabilityTickPhase.AFTER_INPUTS, preparation)) {
            if (preparationCurrent(preparation)) {
                activeRecipe.applyTickGrant(true, false, currentGameTime());
                settleGrant(preparation.recipe, payment);
            }
            discardAsyncTickPreparation();
            return false;
        }
        preparation.afterInputsPassed = true;
        return true;
    }

    /** Runs the AFTER_RECIPE capability phase and publishes tick progress after the previous explicit steps succeed. */
    public CraftingStatus completeAsyncTickAfterRecipe() {
        if (attemptExecuting) return status;
        attemptExecuting = true;
        try {
            return completeAsyncTickAfterRecipeAttempt();
        } finally {
            discardAsyncTickPreparation();
            attemptExecuting = false;
        }
    }

    private CraftingStatus completeAsyncTickAfterRecipeAttempt() {
        AsyncTickPreparation preparation = asyncTickPreparation;
        if (preparation == null || !preparationCurrent(preparation) || errorPaused()) return status;
        if (!preparation.afterInputsPassed) return status;
        RecipeCommitLedger payment = ledger;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        int gameTime = currentGameTime();
        if (activeRecipe.needsFinishCommit()) {
            if (!executeTickPhase(CapabilityTickPhase.AFTER_RECIPE, preparation)) {
                if (preparationCurrent(preparation)) {
                    activeRecipe.applyTickGrant(true, false, gameTime);
                    settleGrant(preparation.recipe, payment);
                }
                return status;
            }
            if (!preparationCurrent(preparation)) return status;
            activeRecipe.beginFinishCommit();
            stressSession.onRecipeFinished();
            settleGrant(activeRecipe, ledger);
            status = CraftingStatus.working();
            return status;
        }
        activeRecipe.applyTickGrant(true, false, gameTime);
        if (activeRecipe.isFinishPending()) stressSession.onRecipeFinished();
        settleGrant(activeRecipe, ledger);
        if (!executeTickPhase(CapabilityTickPhase.AFTER_RECIPE, preparation)) return status;
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    public CapabilityTickResult tickIdle() {
        if (!components.hasTickFacets()) {
            return handleCapabilityTickResult(CapabilityTickResult.empty());
        }
        MachineBehaviorContext machineContext = behaviorContext();
        capabilityTickContextBuildCountForTesting++;
        return handleCapabilityTickResult(components.executeTickPhase(new CapabilityTickContext(capabilityGameTime(), CapabilityTickPhase.IDLE,
                null, 1L, new CapabilitySnapshot(components.capabilities()), machineContext)));
    }

    public CapabilityTickResult handleCapabilityTickResult(CapabilityTickResult result) {
        if (result == null) throw new IllegalArgumentException("result must not be null");
        boolean recovered = false;
        if (result.failure() != null) {
            capabilityTickFailure = result.failure();
            waiting(result.failure(), false);
        } else if (capabilityTickFailure != null && capabilityTickFailure.equals(failure)) {
            capabilityTickFailure = null;
            failure = null;
            status = active() ? CraftingStatus.working() : CraftingStatus.IDLE;
            recovered = true;
        }
        if (recovered || result.failure() != null || result.stateChanged()) controller.syncRecipeRuntimeFailure(this);
        return result;
    }

    public CraftingStatus finish() {
        if (finishExecuting) return status;
        finishExecuting = true;
        try {
            return finishAttempt();
        } finally {
            finishExecuting = false;
        }
    }

    private CraftingStatus finishAttempt() {
        if (!active()) return status;
        if (ledger.state().outputPaid()) return completeFinish();
        if (errorPaused()) return status;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        if (!activeRecipe.isFinishPending()) return status;
        stressSession.onRecipeFinished();
        if (!activeRecipe.shouldRetryFinish(currentGameTime())) return status;

        RecipeFinishContext finishContext = preparedAsyncFinishContext;
        preparedAsyncFinishContext = null;
        if (finishContext == null) {
            finishContext = prepareFinishContext();
            if (finishContext == null) return status;
        }
        if (!active()) return status;
        if (!versionsCurrent()) return invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
        ActiveMachineRecipe finishingRecipe = activeRecipe;
        RecipeCommitLedger finishingPayment = ledger;
        long finishingLifecycle = controller.lifecycleEpoch();
        ControllerRuntimeSnapshot runtime = installedProgram != null && installedBehavior != null && !installedBehavior.hasBeforeFinish()
                ? controller.executionRuntimeSnapshot() : controller.currentRuntimeSnapshot();
        CraftingContext context = installedProgram != null && installedProgram.finishReady()
                ? installedProgram.planningContext() : context(runtime);
        if (finishContext.cancelled()) {
            return finishBlocked(failure(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH_CANCELLED,
                    FailurePhase.FINISH, Map.of()));
        }
        if (finishContext.outputsDiscarded()) {
            ledger.markOutputPaid();
            return completeFinish();
        }
        PlanningResult result;
        try {
            List<MachineRequirement> requirements = installedProgram == null ? null : installedProgram.finishRequirements();
            if (requirements == null) requirements = compiledFinishRequirements(finishContext.outputs());
            result = context.planFixedRequirements(requirements, activeRecipe.getParallelism(), outputPolicies(requirements),
                    IntStream.range(0, requirements.size()).boxed().toList());
        } catch (IllegalArgumentException exception) {
            logCallbackFailure("beforeFinish.output_validation", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure(BuiltinFailureReasons.INVALID_OUTPUTS, FailurePhase.FINISH, Map.of()));
        } catch (RuntimeException exception) {
            logFinishFailure("planning", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure(BuiltinFailureReasons.FINISH, FailurePhase.FINISH, Map.of()));
        }
        if (activeRecipe != finishingRecipe || ledger != finishingPayment
                || controller.lifecycleEpoch() != finishingLifecycle || controller.isRemoved()) return status;
        finishPlan = result.plan();
        if (!result.successful() || finishPlan == null) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return finishBlocked(result.failure());
        }
        boolean committed;
        RecipeCommitLedger payment = ledger;
        ActiveMachineRecipe completingRecipe = activeRecipe;
        try {
            payment.beginNativeExecution();
            committed = finishPlan.commitRuntime(() -> { }, () -> activeRecipe == completingRecipe && ledger == payment
                    && controller.lifecycleEpoch() == finishingLifecycle && !controller.isRemoved());
            if (committed) payment.markOutputPaid();
            else payment.pauseAfterCommitFailure();
        } catch (RuntimeException exception) {
            logFinishFailure("commit", runtime, completingRecipe.getRecipe(), exception);
            if (payment.state().outputPaid()) {
                if (activeRecipe == completingRecipe) return completeFinish();
                return status;
            }
            payment.pauseAfterCommitFailure();
            if (activeRecipe != completingRecipe) return status;
            return finishBlocked(failure(BuiltinFailureReasons.FINISH, FailurePhase.FINISH, Map.of()));
        }
        if (activeRecipe != completingRecipe) return status;
        if (!committed) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return finishBlocked(finishPlan.failure());
        }
        return completeFinish();
    }

    private CraftingStatus completeFinish() {
        ActiveMachineRecipe completingRecipe = activeRecipe;
        RecipeCommitLedger payment = ledger;
        restartRecipe = activeRecipe == null ? null : activeRecipe.getRecipe();
        cancelInstalledProgram();
        if (activeRecipe != null) activeRecipe.applyTickGrant(true, true, currentGameTime());
        releaseActivePrefetches(true);
        if (activeRecipe != completingRecipe || ledger != payment) return status;
        activeRecipe = null;
        setActiveInputScope(null);
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

    /** Executes the finish behavior callback before shared-IO native output admission is requested. */
    public boolean prepareAsyncFinish() {
        if (finishExecuting) return false;
        if (!active() || errorPaused() || ledger.state().outputPaid() || !versionsCurrent() || !activeRecipe.isFinishPending()
                || !activeRecipe.shouldRetryFinish(currentGameTime())) return false;
        preparedAsyncFinishContext = prepareFinishContext();
        return preparedAsyncFinishContext != null;
    }

    private @Nullable RecipeFinishContext prepareFinishContext() {
        ControllerRuntimeSnapshot runtime = installedProgram != null
                ? controller.executionRuntimeSnapshot() : controller.currentRuntimeSnapshot();
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) {
            finishBlocked(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.FINISH, Map.of()));
            return null;
        }
        RecipeFinishContext finishContext;
        if (behavior.hasBeforeFinish()) runtime = controller.currentRuntimeSnapshot();
        MachineBehaviorContext machineContext = behavior.hasBeforeFinish() ? behaviorContext() : null;
        ActiveMachineRecipe finishingRecipe = activeRecipe;
        long lifecycle = controller.lifecycleEpoch();
        try {
            finishContext = machineContext == null
                    ? new RecipeFinishContext(activeRecipe.getRecipe(), activeRecipe.getMaxParallelism(),
                            activeRecipe.getParallelism(), activeOutputs())
                    : new RecipeFinishContext(machineContext, activeRecipe.getRecipe(),
                            activeRecipe.getMaxParallelism(), activeRecipe.getParallelism(), activeOutputs());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logCallbackFailure("beforeFinish.output_validation", runtime, finishingRecipe.getRecipe(), exception);
            if (activeRecipe == finishingRecipe && lifecycle == controller.lifecycleEpoch()) {
                finishBlocked(failure(BuiltinFailureReasons.INVALID_OUTPUTS, FailurePhase.FINISH, Map.of()));
            }
            return null;
        }
        if (!behavior.hasBeforeFinish()) return finishContext;
        try {
            behavior.beforeFinish().accept(finishContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeFinish", runtime, finishingRecipe.getRecipe(), exception);
            if (activeRecipe != finishingRecipe || lifecycle != controller.lifecycleEpoch()) return null;
            finishBlocked(failure(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH, FailurePhase.FINISH, Map.of()));
            return null;
        } finally {
            flushScreenTextReplacements(machineContext.screenText());
        }
        if (activeRecipe != finishingRecipe || lifecycle != controller.lifecycleEpoch()) return null;
        if (finishContext.cancelled()) {
            finishBlocked(failure(BuiltinFailureReasons.BEHAVIOR_BEFORE_FINISH_CANCELLED, FailurePhase.FINISH, Map.of()));
            return null;
        }
        return finishContext;
    }

    public boolean active() {
        return activeRecipe != null;
    }

    private boolean needsRecipeTickContext(RecipeBehavior behavior) {
        return behavior.hasRecipeTick() || components.hasTickFacets();
    }

    private AsyncTickPreparation prepareTick(ControllerRuntimeSnapshot runtime, RecipeBehavior behavior) {
        AsyncTickPreparation preparation = new AsyncTickPreparation(runtime, activeRecipe, controller.lifecycleEpoch(), tickAttempt);
        if (needsRecipeTickContext(behavior)) recipeTickContext(preparation);
        return preparation;
    }

    private RecipeTickContext recipeTickContext(AsyncTickPreparation preparation) {
        if (preparation.tickContext == null) {
            CapabilitySnapshot capabilities = new CapabilitySnapshot(components.capabilities());
            if (cachedPublicRequirementSource != effectiveRequirements) {
                cachedPublicRequirementSource = effectiveRequirements;
                cachedPublicRequirements = MachineRequirement.copyList(effectiveRequirements);
            }
            preparation.tickContext = new RecipeTickContext(behaviorContext(capabilities), activeRecipe.getRecipe(),
                    preparation.currentTick, activeRecipe.getTotalTick(), activeRecipe.getParallelism(),
                    cachedPublicRequirements, effectiveOutputs, capabilities);
            recipeTickContextBuildCountForTesting++;
        }
        return preparation.tickContext;
    }

    private boolean executeTickPhase(CapabilityTickPhase phase, AsyncTickPreparation preparation) {
        if (!preparationCurrent(preparation)) return false;
        RecipeCommitLedger payment = ledger;
        RecipeCommitLedger.State admission = payment.state();
        CapabilityTickResult result;
        try {
            if (!components.hasTickFacets()) {
                if (capabilityTickFailure != null) handleCapabilityTickResult(CapabilityTickResult.empty());
                return true;
            }
            RecipeTickContext recipeTickContext = recipeTickContext(preparation);
            capabilityTickContextBuildCountForTesting++;
            result = components.executeTickPhase(new CapabilityTickContext(
                    capabilityGameTime(), phase,
                    recipeTickContext, activeRecipe.getParallelism(), new CapabilitySnapshot(components.capabilities()),
                    recipeTickContext.machineContext()), () -> preparationCurrent(preparation),
                    () -> { if (phase == CapabilityTickPhase.BEFORE_RECIPE) payment.beginNativeExecution(); });
            // AFTER operations never erase the already confirmed ordinary payment or granted progress.
            if (!preparationCurrent(preparation)) {
                if (phase == CapabilityTickPhase.BEFORE_RECIPE
                        && payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                return false;
            }
            if (result.failure() != null && payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) {
                payment.pauseAfterCommitFailure();
            } else payment.completePrefetchDebit(admission);
            handleCapabilityTickResult(result);
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Machine recipe tick capability phase failed: phase={} controller={}", phase,
                    controller.getBlockPos(), exception);
            if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
            if (ledger != payment || activeRecipe != preparation.recipe
                    || controller.lifecycleEpoch() != preparation.lifecycleEpoch) return false;
            handleCapabilityTickResult(new CapabilityTickResult(List.of(),
                    failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()), false));
            return false;
        }
        return result.failure() == null && preparationCurrent(preparation);
    }

    private boolean preparationCurrent(AsyncTickPreparation preparation) {
        return activeRecipe == preparation.recipe && preparation.lifecycleEpoch == controller.lifecycleEpoch()
                && attemptCurrent(preparation.attempt);
    }

    private boolean beginTickAttempt() {
        if (tickAttempt != null || !active() || status.isPaused()
                || ledger.state().phase() != RecipeCommitLedger.Phase.READY || ledger.state().finalTickPaid()) return false;
        tickAttempt = new TickAttempt(activeRecipe, ledger, installedProgram, controller.lifecycleEpoch());
        return true;
    }

    private boolean attemptCurrent(@Nullable TickAttempt attempt) {
        return attemptBound(attempt) && (attempt.installed() == null || attempt.installed().current());
    }

    /** Identity checks only; never substitutes for live validation across an execution boundary. */
    private boolean attemptBound(@Nullable TickAttempt attempt) {
        return attemptIdentityCurrent(attempt) && !errorPaused();
    }

    private boolean attemptIdentityCurrent(@Nullable TickAttempt attempt) {
        return attempt != null && tickAttempt == attempt && activeRecipe == attempt.recipe() && ledger == attempt.payment()
                && controller.lifecycleEpoch() == attempt.lifecycleEpoch() && !controller.isRemoved()
                && installedProgram == attempt.installed();
    }

    public void pause() {
        releaseStressContributions();
        if (active()) status = CraftingStatus.paused();
        cancelInstalledProgram();
        if (active() && !errorPaused()) status = CraftingStatus.paused();
    }

    /** Releases this lane's contributions, including facets no longer in the component snapshot. */
    public void releaseStressContributions() {
        stressSession.releaseAll();
    }

    public void resume() {
        if (active() && !errorPaused()) status = CraftingStatus.working();
    }

    public @Nullable ExecutionStatus failure() {
        return failure;
    }

    public CraftingStateSnapshot snapshot() {
        CraftingStateSnapshot previous = cachedStateSnapshot;
        ResourceLocation recipeId = activeRecipe == null ? null : activeRecipe.getRecipe().id();
        long structure = structureVersion == Long.MIN_VALUE ? 0L : structureVersion;
        long capability = capabilityVersion == Long.MIN_VALUE ? 0L : capabilityVersion;
        long modifier = modifierVersion == Long.MIN_VALUE ? 0L : modifierVersion;
        CraftingStatus.Status currentStatus = status.getStatus();
        String statusMessage = status.getUnlocMessage();
        if (previous != null && Objects.equals(previous.recipeId(), recipeId)
                && cachedSnapshotStatus == currentStatus
                && Objects.equals(cachedSnapshotStatusMessage, statusMessage)
                && Objects.equals(cachedSnapshotFailure, failure)
                && previous.structureVersion() == structure
                && previous.capabilityVersion() == capability
                && previous.modifierVersion() == modifier
                && previous.tick() == tickCount() && previous.totalTick() == totalTick()
                && previous.parallelism() == parallelism()
                && previous.maxParallelism() == maxParallelism()) {
            // Equal replacement failures need only one deep comparison, even when the payload is reused.
            cachedSnapshotFailure = failure;
            return previous;
        }
        snapshotBuildCountForTesting++;
        cachedStateSnapshot = new CraftingStateSnapshot(recipeId, status, failure,
                structure, capability, modifier, tickCount(), totalTick(), parallelism(), maxParallelism());
        cachedSnapshotStatus = currentStatus;
        cachedSnapshotStatusMessage = statusMessage;
        cachedSnapshotFailure = failure;
        return cachedStateSnapshot;
    }

    int snapshotBuildCountForTesting() {
        return snapshotBuildCountForTesting;
    }

    int recipeTickContextBuildCountForTesting() {
        return recipeTickContextBuildCountForTesting;
    }

    int capabilityTickContextBuildCountForTesting() {
        return capabilityTickContextBuildCountForTesting;
    }

    /** Value-only observation; does not copy the published crafting status. */
    StateObservation observation() {
        return new StateObservation(recipe() == null ? null : recipe().id(), status.getStatus(), status.getUnlocMessage(),
                failure, structureVersion == Long.MIN_VALUE ? 0L : structureVersion,
                capabilityVersion == Long.MIN_VALUE ? 0L : capabilityVersion,
                modifierVersion == Long.MIN_VALUE ? 0L : modifierVersion,
                tickCount(), totalTick(), parallelism(), activeRecipe == null ? 1 : activeRecipe.getMaxParallelism());
    }

    private boolean matchesObservation(@Nullable ResourceLocation recipeId, CraftingStatus.Status observedStatus,
                                       @Nullable String statusMessage, @Nullable ExecutionStatus observedFailure,
                                       long observedStructureVersion, long observedCapabilityVersion,
                                       long observedModifierVersion, int tick, int totalTick,
                                       long parallelism, long maxParallelism) {
        MachineRecipe recipe = recipe();
        return Objects.equals(recipeId, recipe == null ? null : recipe.id())
                && observedStatus == status.getStatus()
                && Objects.equals(statusMessage, status.getUnlocMessage())
                && Objects.equals(observedFailure, failure)
                && observedStructureVersion == (structureVersion == Long.MIN_VALUE ? 0L : structureVersion)
                && observedCapabilityVersion == (capabilityVersion == Long.MIN_VALUE ? 0L : capabilityVersion)
                && observedModifierVersion == (modifierVersion == Long.MIN_VALUE ? 0L : modifierVersion)
                && tick == tickCount() && totalTick == totalTick()
                && parallelism == parallelism() && maxParallelism == maxParallelism();
    }

    /**
     * Scope-owned scalar baseline; never publish or share while borrowed.
     *
     * @author howxu <dev@howxu.cn>
     */
    static class ObservationBuffer {
        private @Nullable ResourceLocation recipeId;
        private CraftingStatus.Status observedStatus;
        private @Nullable String statusMessage;
        private @Nullable ExecutionStatus observedFailure;
        private long observedStructureVersion;
        private long observedCapabilityVersion;
        private long observedModifierVersion;
        private int tick;
        private int totalTick;
        private long parallelism;
        private long maxParallelism;

        void capture(CraftingRuntime runtime) {
            MachineRecipe recipe = runtime.recipe();
            recipeId = recipe == null ? null : recipe.id();
            observedStatus = runtime.status.getStatus();
            statusMessage = runtime.status.getUnlocMessage();
            observedFailure = runtime.failure;
            observedStructureVersion = runtime.structureVersion == Long.MIN_VALUE ? 0L : runtime.structureVersion;
            observedCapabilityVersion = runtime.capabilityVersion == Long.MIN_VALUE ? 0L : runtime.capabilityVersion;
            observedModifierVersion = runtime.modifierVersion == Long.MIN_VALUE ? 0L : runtime.modifierVersion;
            tick = runtime.tickCount();
            totalTick = runtime.totalTick();
            parallelism = runtime.parallelism();
            maxParallelism = runtime.maxParallelism();
        }

        boolean matches(CraftingRuntime runtime) {
            return runtime.matchesObservation(recipeId, observedStatus, statusMessage, observedFailure,
                    observedStructureVersion, observedCapabilityVersion, observedModifierVersion,
                    tick, totalTick, parallelism, maxParallelism);
        }

        void clear() {
            recipeId = null;
            statusMessage = null;
            observedFailure = null;
        }
    }

    /**
     * Immutable scalar state used for factory change detection.
     *
     * @author howxu <dev@howxu.cn>
     */
    record StateObservation(@Nullable ResourceLocation recipeId, CraftingStatus.Status status, String statusMessage,
                            @Nullable ExecutionStatus failure, long structureVersion, long capabilityVersion,
                            long modifierVersion, int tick, int totalTick, long parallelism, long maxParallelism) {
        boolean matches(CraftingRuntime runtime) {
            return runtime.matchesObservation(recipeId, status, statusMessage, failure,
                    structureVersion, capabilityVersion, modifierVersion,
                    tick, totalTick, parallelism, maxParallelism);
        }
    }

    String recipeIdText() {
        ResourceLocation recipeId = recipe() == null ? null : recipe().id();
        if (!Objects.equals(cachedRecipeId, recipeId)) {
            cachedRecipeId = recipeId;
            cachedRecipeIdText = recipeId == null ? "" : recipeId.toString();
        }
        return cachedRecipeIdText;
    }

    public @Nullable MachineRecipe recipe() {
        return activeRecipe == null ? null : activeRecipe.getRecipe();
    }

    public @Nullable ActiveMachineRecipe activeRecipe() {
        return activeRecipe;
    }

    public ControllerRecipePresentation recipePresentation() {
        long currentParallelism = active() ? parallelism() : 0L;
        int currentDuration = active() ? totalTick() : 0;
        long executionRevision = activeRecipe == null ? 0L : activeRecipe.effectiveExecutionRevision();
        if (cachedRecipePresentation != null
                && cachedPresentationRecipe == activeRecipe
                && cachedPresentationEpoch == presentationEpoch
                && cachedActiveExecutionRevision == executionRevision
                && cachedPresentationParallelism == currentParallelism
                && cachedPresentationDuration == currentDuration) {
            return cachedRecipePresentation;
        }
        cachedRecipePresentation = ControllerRecipePresentation.from(this);
        cachedPresentationRecipe = activeRecipe;
        cachedPresentationEpoch = presentationEpoch;
        cachedActiveExecutionRevision = executionRevision;
        cachedPresentationParallelism = currentParallelism;
        cachedPresentationDuration = currentDuration;
        return cachedRecipePresentation;
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
        return activeRecipe != null && !errorPaused() && !ledger.state().outputPaid()
                && ledger.state().phase() != RecipeCommitLedger.Phase.UNCERTAIN
                && activeRecipe.shouldRetryFinish(currentGameTime());
    }

    public void recordSearchFailure(@Nullable ExecutionStatus nextFailure) {
        // An idle candidate miss is not a running-recipe failure; let completed outputs coast to their deadline.
        if (active()) releaseStressContributions();
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.RECIPE_SEARCH, FailurePhase.RECIPE_SEARCH, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    public boolean versionsCurrent() {
        if (!active()) return true;
        ComponentRuntime components = controller.componentRuntime();
        MachineRecipe recipe = activeRecipe.getRecipe();
        return structureVersion == controller.currentStructureVersion()
                && capabilityVersion == components.capabilityVersion()
                && inputScopeSourcesCurrent()
                && recipeBelongsToMachine(recipe)
                && components.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds());
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        return resourceDomain;
    }

    public void invalidate() {
        PreparedStart oldPaymentStart = pendingPaymentStart;
        PreparedStart oldPatternStart = pendingPatternStart;
        StressSession oldStress = stressSession;
        // Detach the cancelled identity before any native release/owner callback can start its successor.
        pendingPaymentStart = null;
        pendingPatternStart = null;
        pendingExactPatternStart = false;
        pendingPatternSourceOwner = null;
        pendingPatternSource = null;
        pendingPatternDomain = null;
        stressSession = new StressSession();
        cancelInstalledProgram();
        ledger = new RecipeCommitLedger();
        restartRecipe = null;
        restartRule = null;
        restartProgramAuthorized = false;
        activeRecipe = null;
        setActiveInputScope(null);
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
        try {
            releaseStartSlot();
        } finally {
            try {
                releaseActivePrefetches();
            } finally {
                try {
                    if (oldPatternStart != null) releasePreparedPrefetches(oldPatternStart);
                    if (oldPaymentStart != null && oldPaymentStart != oldPatternStart) releasePreparedPrefetches(oldPaymentStart);
                } finally {
                    oldStress.cancel();
                }
            }
        }
    }

    public void invalidateForSmartInterfaceChange() {
        if (!active() && (pendingPrefetchStart == null || pendingPrefetchStart.payment().state().paidAttempts() == 0L)) {
            releasePatternStart();
        }
    }

    public void invalidateForCatalogChange() {
        if (active()) invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
    }

    public void restore(ActiveMachineRecipe restored, @Nullable StructureClaimRegistry.ResourceDomain domain,
                        long restoredStructureVersion, long restoredCapabilityVersion,
                        long restoredModifierVersion, long restoredComponentStateVersion) {
        RecipeCommitLedger restoredPayment = new RecipeCommitLedger();
        if (restored != null) restoredPayment.settleTick(restored.getTick(), restored.isFinishPending());
        if (restored != null && restored.getDataCompound().getBoolean("native_start_uncertain")) {
            restoredPayment.pauseAfterCommitFailure();
        }
        restore(restored, domain, restoredStructureVersion, restoredCapabilityVersion,
                restoredModifierVersion, restoredComponentStateVersion, restoredPayment);
        if (activeRecipe == restored && ledger == restoredPayment) publishPrefetchOwners();
    }

    private void restore(ActiveMachineRecipe restored, @Nullable StructureClaimRegistry.ResourceDomain domain,
                         long restoredStructureVersion, long restoredCapabilityVersion,
                         long restoredModifierVersion, long restoredComponentStateVersion,
                         RecipeCommitLedger restoredPayment) {
        releaseStressContributions();
        if (restored == null || restored.getRecipe() == null) {
            failLoad();
            return;
        }
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(restored.getRecipe())) {
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
                    MachineRequirement.copyList(requirements), outputs));
            if (restored.getTotalTick() < 1 || restored.getTick() < 0
                    || restored.getTick() > restored.getTotalTick()
                    || (restored.isFinishPending() && restored.getTick() != restored.getTotalTick() - 1)) {
                failLoad();
                return;
            }
        }
        ledger = restoredPayment;
        if (!restorePrefetches(restored, requirements)) {
            failLoad();
            return;
        }
        activeRecipe = restored;
        setActiveInputScope(InputScope.legacy());
        cancelInstalledProgram();
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
        presentationEpoch++;
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(ItemRequirement.TYPE.equals(requirement.type()) || FluidRequirement.TYPE.equals(requirement.type())
                    || LoadedChemicalRequirement.TYPE.equals(requirement.type())
                    || SourceRequirement.TYPE.equals(requirement.type())
                    || ManaRequirement.TYPE.equals(requirement.type()) || requirement instanceof LightningRequirement)
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
        if (capabilityVersion != runtime.capabilityVersion()) releaseStressContributions();
        if (!recipeBelongsToMachine(activeRecipe.getRecipe()) || !inputScopeSourcesCurrent()) {
            invalidate(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RUNTIME);
            return;
        }
        cancelInstalledProgram();
        captureVersions(runtime);
    }

    public void save(CompoundTag output) { save(output, registryAccess()); }

    public void load(CompoundTag input, @Nullable StructureClaimRegistry.ResourceDomain domain) {
        load(input, domain, registryAccess());
    }

    public void save(CompoundTag output, HolderLookup.Provider registries) {
        output.putBoolean("requires_pattern_request", pendingPaymentStart != null
                && ledger.state().phase() != RecipeCommitLedger.Phase.READY ? pendingExactPatternStart : requiresPatternRequest);
        if (pendingPaymentStart != null && ledger.state().phase() != RecipeCommitLedger.Phase.READY) {
            savePaidStart(output, pendingPaymentStart, registries);
            return;
        }
        boolean present = activeRecipe != null && activeRecipe.getRecipe() != null && !ledger.state().outputPaid();
        output.putBoolean("active", present);
        ledger.save(output);
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
            saveInputScope(output, activeInputScope);
            CompoundTag recipeOutput = new CompoundTag();
            activeRecipe.serialize(recipeOutput, registries);
            output.put("recipe", recipeOutput);
        }
    }

    /** Saved allocations need discovered endpoints; recipes without them retain the early-load path. */
    public static boolean requiresTopologyForLoad(@Nullable CompoundTag input) {
        if (input == null || !input.getBoolean("active")) return false;
        CompoundTag data = input.getCompound("recipe").getCompound("data");
        return data.contains(PREFETCH_ALLOCATIONS_KEY) || data.getBoolean(PREFETCH_RESERVATION_KEY);
    }

    public void load(CompoundTag input, @Nullable StructureClaimRegistry.ResourceDomain domain,
                     HolderLookup.Provider registries) {
        boolean restoredPatternRequest = input.getBoolean("requires_pattern_request");
        boolean active = input.getBoolean("active");
        if (!active) {
            invalidate();
            requiresPatternRequest = restoredPatternRequest;
            restoreFailure(readFailure(input, null));
            return;
        }
        InputScope restoredScope = readInputScope(input);
        if (restoredScope == null) {
            failLoad();
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
        RecipeCommitLedger restoredLedger = new RecipeCommitLedger();
        if (!restoredLedger.load(input, loaded.recipe().getTick(), loaded.recipe().isFinishPending())) {
            failLoad();
            return;
        }
        if (restoredLedger.state().outputPaid()) {
            invalidate();
            return;
        }
        ledger = restoredLedger;
        requiresPatternRequest = restoredPatternRequest;
        long restoredUpgradeContentRevision = (input.contains("upgrade_content_revision") ? input.getLong("upgrade_content_revision") : Long.MIN_VALUE);
        restore(loaded.recipe(), domain,
                input.contains("structure_version") ? input.getLong("structure_version") : Long.MIN_VALUE,
                input.contains("capability_version") ? input.getLong("capability_version") : Long.MIN_VALUE,
                input.contains("modifier_version") ? input.getLong("modifier_version") : Long.MIN_VALUE,
                input.contains("component_state_version") ? input.getLong("component_state_version") : Long.MIN_VALUE,
                restoredLedger);
        if (activeRecipe == loaded.recipe() && ledger == restoredLedger) {
            requiresPatternRequest = restoredPatternRequest;
            setActiveInputScope(restoredScope);
            if (restoredUpgradeContentRevision != Long.MIN_VALUE) {
                upgradeContentRevision = restoredUpgradeContentRevision;
            }
            restoreFailure(readFailure(input, loaded.recipe().getRecipe().id()));
            if (errorPaused() && failure == null) {
                restoreFailure(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
            }
            publishPrefetchOwners();
        }
    }

    private static @Nullable ExecutionStatus readFailure(CompoundTag input, @Nullable ResourceLocation recipeId) {
        if (input.contains("failure")) return FailureStatusCodec.read(input.getCompound("failure"));
        if (!input.getBoolean("has_failure")) return null;
        return FailureStatusMigration.craftingFailure(input.getString("failure_reason"), recipeId);
    }

    public @Nullable InputScope activeInputScope() {
        return activeInputScope != null ? activeInputScope
                : pendingPaymentStart != null && ledger.state().phase() != RecipeCommitLedger.Phase.READY
                ? pendingPaymentStart.plan().inputScope() : null;
    }

    private static void saveInputScope(CompoundTag output, @Nullable InputScope scope) {
        if (scope == null) return;
        CompoundTag node = new CompoundTag();
        node.putInt("version", 1);
        node.putString("kind", scope.kind().name());
        node.put("sources", BlockPos.CODEC.listOf().encodeStart(NbtOps.INSTANCE, scope.sources()).getOrThrow());
        output.put("input_scope", node);
    }

    private static @Nullable InputScope readInputScope(CompoundTag input) {
        if (!input.contains("input_scope")) return InputScope.legacy();
        if (!input.contains("input_scope", Tag.TAG_COMPOUND)) return null;
        CompoundTag node = input.getCompound("input_scope");
        if (node.getInt("version") != 1 || !node.contains("sources")) return null;
        List<BlockPos> positions = BlockPos.CODEC.listOf().parse(NbtOps.INSTANCE,
                node.get("sources")).result().orElse(null);
        if (positions == null || new HashSet<>(positions).size() != positions.size()) return null;
        try {
            InputScope.Kind kind = InputScope.Kind.valueOf(node.getString("kind"));
            if (kind == InputScope.Kind.LEGACY && !positions.isEmpty()
                    || kind == InputScope.Kind.ISOLATED && positions.size() != 1) return null;
            return new InputScope(kind, positions);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void setActiveInputScope(@Nullable InputScope scope) {
        activeInputScope = scope;
        checkedInputScope = null;
        checkedInputRouting = null;
    }

    private boolean inputScopeSourcesCurrent() {
        if (activeInputScope == null || activeInputScope.kind() == InputScope.Kind.LEGACY) return true;
        // Resolve custom providers live even on a memo hit; their facet presence may change without rebinding.
        InputRoutingFacet routing = components.inputRouting();
        if (checkedInputScope == activeInputScope && checkedInputRouting == routing) return inputScopeSourcesValid;
        Set<BlockPos> positions = components.inputSourcePositions(routing);
        boolean valid = true;
        List<BlockPos> sources = activeInputScope.sources();
        for (int index = 0; index < sources.size(); index++) {
            if (!positions.contains(sources.get(index))) {
                valid = false;
                break;
            }
        }
        checkedInputScope = activeInputScope;
        checkedInputRouting = routing;
        inputScopeSourcesValid = valid;
        inputScopeValidationCountForTesting++;
        return valid;
    }

    int inputScopeValidationCountForTesting() { return inputScopeValidationCountForTesting; }

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
        return waiting(nextFailure, true);
    }

    /** Only a pre-commit Create power check may retain the lane's previous input contributions. */
    private CraftingStatus waiting(@Nullable ExecutionStatus nextFailure, boolean allowInputRetention) {
        ActiveMachineRecipe recipe = activeRecipe;
        RecipeCommitLedger payment = ledger;
        StressSession owner = stressSession;
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        boolean retainInput = allowInputRetention && isPowerWait(failure);
        if (asyncTickPreparation != null) asyncTickPowerWait = retainInput;
        if (retainInput) owner.releaseOutputs();
        else owner.releaseAll();
        if (activeRecipe != recipe || ledger != payment) return status;
        if (recipe != null && recipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
            cancelInstalledProgram();
            activeRecipe = null;
            setActiveInputScope(null);
            startPlan = null;
            finishPlan = null;
            clearEffectiveRecipe();
            resourceDomain = null;
            stressSession = new StressSession();
            try {
                releaseActivePrefetches();
            } finally {
                owner.cancel();
            }
        }
        return status;
    }

    private static boolean isPowerWait(ExecutionStatus status) {
        return status != null && CreateFailureReasons.isPowerFailure(status.reason());
    }

    private CraftingStatus finishBlocked(@Nullable ExecutionStatus nextFailure) {
        releaseStressContributions();
        if (activeRecipe != null) activeRecipe.markFinishBlocked(currentGameTime());
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.FINISH, FailurePhase.FINISH, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private CraftingStatus invalidate(FailureReason reason, FailurePhase phase) {
        releaseStressContributions();
        cancelInstalledProgram();
        failure = failure(reason, phase, Map.of());
        releaseActivePrefetches();
        activeRecipe = null;
        setActiveInputScope(null);
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
        // An unpaid idle restart rejection must not revoke the completed recipe's native output grace.
        if (active() || pendingPaymentStart != null) releaseStressContributions();
        releaseStartSlot();
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.RECIPE_START, FailurePhase.RECIPE_START, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private MachineBehaviorContext behaviorContext() {
        MachineBehaviorContext context = screenText == null ? controller.behaviorContext() : controller.behaviorContext(screenText);
        lastBehaviorScreenText = context.screenText();
        return context;
    }

    private MachineBehaviorContext behaviorContext(CapabilitySnapshot capabilities) {
        MachineBehaviorContext context = screenText == null
                ? controller.behaviorContext(capabilities)
                : controller.behaviorContext(capabilities, screenText);
        lastBehaviorScreenText = context.screenText();
        return context;
    }

    private static void flushScreenTextReplacements(ControllerScreenText screenText) {
        if (screenText instanceof ControllerScreenTextState state) state.flushReplacements();
    }

    private CraftingContext context(ControllerRuntimeSnapshot runtime) {
        return new CraftingContext(components.capabilitySnapshot(), contextModifiers(runtime), activeInputScope)
                .withReservationOwner(stressSession);
    }

    private CraftingContext legacyPatternContext(ControllerRuntimeSnapshot runtime, List<MachineCapability> requestCapabilities) {
        List<MachineCapability> capabilities = new ArrayList<>(components.capabilities());
        if (requestCapabilities != null) capabilities.addAll(requestCapabilities);
        return new CraftingContext(new CapabilitySnapshot(capabilities), contextModifiers(runtime))
                .withReservationOwner(stressSession);
    }

    private List<RecipeEnergyPrefetchFacet> prefetchFacets(List<MachineCapability> requestCapabilities) {
        List<MachineCapability> capabilities = new ArrayList<>(components.capabilities());
        if (requestCapabilities != null) capabilities.addAll(requestCapabilities);
        return new CapabilitySnapshot(capabilities).facets(RecipeEnergyPrefetchFacet.class);
    }

    private static PlanningResult planStartInputs(CraftingContext context, List<MachineRequirement> requirements,
                                                  List<RecipeEnergyPrefetchFacet> facets, long parallelism) {
        List<Integer> indexes = startInputIndexes(requirements, facets);
        List<MachineRequirement> inputs = indexes.stream().map(requirements::get).toList();
        return context.planRequirements(inputs, parallelism, Map.of(), indexes);
    }

    private static List<Integer> startInputIndexes(List<MachineRequirement> requirements,
                                                  List<RecipeEnergyPrefetchFacet> facets) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (requirement.io() != RecipeModifier.IOType.INPUT) continue;
            if (!facets.isEmpty() && requirement instanceof EnergyRequirement) continue;
            indexes.add(index);
        }
        return List.copyOf(indexes);
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
        long total = 0L;
        boolean complete = false;
        try {
            for (MachineRequirement requirement : requirements) {
                if (!(requirement instanceof EnergyRequirement energy)
                        || energy.io() != RecipeModifier.IOType.INPUT) continue;
                long remaining = SaturatingLong.multiply(SaturatingLong.multiply(energy.fePerTick(), duration), parallelism);
                try {
                    total = Math.addExact(total, remaining);
                } catch (ArithmeticException exception) {
                    return null;
                }
                for (RecipeEnergyPrefetchFacet facet : facets) {
                    if (remaining <= 0L) break;
                    var planned = facet.planPrefetch(remaining);
                    if (planned.isEmpty()) continue;
                    RecipeEnergyPrefetchFacet.PrefetchPlan plan = planned.get();
                    prefetches.add(new PreparedPrefetch(facet.reservationKey(), facet, plan));
                    if (plan.amount() > remaining) return null;
                    remaining -= plan.amount();
                }
                if (remaining > 0L) return null;
            }
            List<PreparedPrefetch> result = List.copyOf(prefetches);
            complete = true;
            return result;
        } finally {
            if (!complete) releaseUnpaidPrefetches(prefetches);
        }
    }

    private @Nullable ExecutionStatus commitPreparedStart(PreparedStart prepared) {
        if (!preparedStartCurrent(prepared)) {
            return failure(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RECIPE_START, Map.of());
        }
        RecipeCommitLedger payment = ledger;
        long lifecycle = controller.lifecycleEpoch();
        StressSession startingStress = stressSession;
        ActiveMachineRecipe startingRecipe = new ActiveMachineRecipe(prepared.recipe().source(),
                prepared.plan().parallelism(), prepared.effective());
        ExecutionIdentity identity = new ExecutionIdentity(controllerIdentity, lifecycle, laneId,
                NEXT_EXECUTION_ID.incrementAndGet());
        SharedIoCoordinator coordinator = controller.getLevel() instanceof ServerLevel level
                && SharedIoCoordinator.get(level).executionRoundId() != 0L ? SharedIoCoordinator.get(level) : null;
        Runnable beforeNative = payment::beginNativeExecution;
        boolean complete = false;
        boolean prefetchException = false;
        pendingPrefetchStart = new PendingPrefetchStart(prepared, payment, lifecycle);
        Set<Integer> airInputIndexes = new LinkedHashSet<>();
        List<MachineRequirement> requirements = prepared.effective().requirements();
        PlanningReservations reservations = new PlanningReservations();
        Sampling airStartSampling = new Sampling();
        for (int index = 0; index < requirements.size(); index++) {
            if (requirements.get(index) instanceof AirRequirement air && air.io() == RecipeModifier.IOType.INPUT) {
                CapabilityResult result = AirRequirementHandler.preflightStart(air, components.capabilities(),
                        new PlanningContext(prepared.plan().parallelism(), index, false, reservations, Map.of(),
                                null, null, airStartSampling));
                if (!result.success()) return result.status();
                airInputIndexes.add(index);
            }
        }
        try {
            // Reject a missing delivery slot before any requirement is recorded as entering native execution.
            if (coordinator != null && !coordinator.beginExecutionAdmission(this, laneId, identity, payment, startingRecipe)) {
                return failure(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RECIPE_START,
                        Map.of("reason", "admission_slot"));
            }
            for (PreparedPrefetch prefetch : prepared.prefetches()) {
                if (payment != ledger || lifecycle != controller.lifecycleEpoch() || pendingPaymentStart != prepared) return missingInputStatus();
                if (payment.state().phase() == RecipeCommitLedger.Phase.READY && !preparedStartCurrent(prepared)) {
                    return failure(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RECIPE_START, Map.of());
                }
                beforeNative.run();
                prefetch.attempted = true;
                prefetch.inFlight = true;
                CapabilityResult result;
                try {
                    result = prefetch.plan().operation().commit();
                    prefetch.committed = result != null && result.success();
                } catch (RuntimeException exception) {
                    prefetchException = true;
                    throw exception;
                } finally {
                    prefetch.inFlight = false;
                }
                if (result == null || !result.success() || prefetch.confirmedAmount() != prefetch.plan().amount()) {
                    payment.pauseAfterCommitFailure();
                    return result == null || result.status() == null ? missingInputStatus() : result.status();
                }
                if (payment != ledger || lifecycle != controller.lifecycleEpoch()) return missingInputStatus();
            }
            // Recheck live Air after prefetch callbacks, before any ordinary startup input.
            reservations = new PlanningReservations();
            for (int index : airInputIndexes) {
                CapabilityResult result = AirRequirementHandler.preflightStart((AirRequirement) requirements.get(index),
                        components.capabilities(), new PlanningContext(prepared.plan().parallelism(), index, false,
                                reservations, Map.of(), null, null, airStartSampling));
                if (!result.success()) return result.status();
            }
            Set<Integer> unpaidInputs = new HashSet<>(airInputIndexes);
            List<Integer> energyIndexes = new ArrayList<>();
            for (int index = 0; index < requirements.size(); index++) {
                if (requirements.get(index) instanceof EnergyRequirement energy && energy.io() == IOType.INPUT) {
                    unpaidInputs.add(index);
                    energyIndexes.add(index);
                }
            }
            // Native storage writes cannot be rolled back on 1.21.1. Re-plan the
            // combined live energy demand without executing it; only recipe ticks pay.
            if (!energyIndexes.isEmpty() && !prepared.energyInputsExcluded()) {
                PlanningResult checked = legacyPatternContext(prepared.runtime(), prepared.requestCapabilities())
                        .planFixedRequirements(energyIndexes.stream().map(requirements::get).toList(),
                                prepared.plan().parallelism(), Map.of(), energyIndexes);
                if (!checked.successful()) return checked.failure() == null ? missingInputStatus() : checked.failure();
            }
            if (payment.state().phase() == RecipeCommitLedger.Phase.READY && !preparedStartCurrent(prepared)) {
                return failure(BuiltinFailureReasons.VERSION_INVALIDATED, FailurePhase.RECIPE_START, Map.of());
            }
            if (!prepared.plan().commitInputsRuntime(unpaidInputs, beforeNative,
                    () -> payment == ledger && lifecycle == controller.lifecycleEpoch()
                            && pendingPaymentStart == prepared)) {
                if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                return prepared.plan().failure() == null ? missingInputStatus() : prepared.plan().failure();
            }
            payment.markResourcePaid(0, false);
            Set<Integer> validatedInputs = pendingExactPatternStart
                    ? requestValidatedInputs(requirements, prepared.plan().parallelism(), prepared.requestCapabilities()) : Set.of();
            List<Integer> consumed = new ArrayList<>();
            for (int index = 0; index < requirements.size(); index++) {
                MachineRequirement requirement = requirements.get(index);
                boolean input = requirement.io() == IOType.INPUT && (ItemRequirement.TYPE.equals(requirement.type())
                        || FluidRequirement.TYPE.equals(requirement.type()) || LoadedChemicalRequirement.TYPE.equals(requirement.type())
                        || SourceRequirement.TYPE.equals(requirement.type()) || ManaRequirement.TYPE.equals(requirement.type())
                        || requirement instanceof LightningRequirement);
                consumed.add(input && (prepared.plan().hasOperations(index) || validatedInputs.contains(index)) ? 1 : 0);
            }
            startingRecipe.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(consumed));
            acceptedRecipe = startingRecipe;
            if (coordinator != null && !coordinator.acceptExecution(this, laneId, identity, payment, startingRecipe)) {
                payment.pauseAfterCommitFailure();
                return missingInputStatus();
            }
            complete = true;
            return null;
        } catch (RuntimeException exception) {
            payment.pauseAfterCommitFailure();
            throw exception;
        } finally {
            try {
                if (!complete && !prefetchException && payment.state().paidAttempts() == 0L
                        && payment == ledger && lifecycle == controller.lifecycleEpoch()
                        && pendingPaymentStart == prepared && !hasOrdinaryNativeEffects(prepared.plan(), requirements)
                        && prepared.prefetches().stream().allMatch(prefetch -> !prefetch.inFlight
                                && !prefetch.transferred && prefetch.confirmedAmount() >= 0L)) {
                    // These are known owner-scoped holds, not an irreversible ordinary resource prefix.
                    startingStress.releaseAll();
                    releasePreparedPrefetches(prepared);
                    if (prepared.prefetches().stream().noneMatch(prefetch -> prefetch.releaseFailed)) {
                        payment.settleReleasedStart();
                    }
                } else if (payment == ledger && lifecycle == controller.lifecycleEpoch()
                        && payment.state().phase() != RecipeCommitLedger.Phase.RESOURCE_PAID) startingStress.releaseAll();
            } finally {
                if (coordinator != null) coordinator.endExecutionAdmission(identity);
            }
        }
    }

    private static boolean hasOrdinaryNativeEffects(CraftingPlan plan, List<MachineRequirement> requirements) {
        for (var requirementPlan : plan.requirements()) {
            int index = requirementPlan.requirementIndex();
            if (!plan.wasNativeExecuted(index)) continue;
            if (index < 0 || index >= requirements.size()
                    || !(requirements.get(index) instanceof StressRequirement stress)
                    || !(RequirementHandlerRegistry.handlerFor(stress.type()) instanceof StressRequirementHandler)) return true;
        }
        return false;
    }

    private void quarantineNativeStart(PreparedStart prepared, RecipeCommitLedger payment) {
        if (payment != ledger || payment.state().phase() == RecipeCommitLedger.Phase.READY || active()) return;
        payment.pauseAfterCommitFailure();
        activeRecipe = new ActiveMachineRecipe(prepared.recipe().source(), prepared.plan().parallelism(), prepared.effective());
        requiresPatternRequest = pendingExactPatternStart;
        activeRecipe.setParallelism(prepared.plan().parallelism());
        setActiveInputScope(prepared.plan().inputScope());
        effectiveRequirements = MachineRequirement.copyList(prepared.effective().requirements());
        effectiveOutputs = MachineOutput.copyList(prepared.effective().outputs());
        captureInputState(effectiveRequirements, prepared.plan());
        captureVersions(prepared.runtime());
        activeRecipe.getDataCompound().putBoolean("native_start_uncertain", true);
        presentationEpoch++;
        if (pendingPrefetchStart != null && pendingPrefetchStart.prepared() == prepared) pendingPrefetchStart = null;
        activatePrefetches(prepared.prefetches(), effectiveRequirements, prepared.plan().parallelism());
    }

    private boolean reserveStartSlot() {
        if (!(controller.getLevel() instanceof ServerLevel level)) return true;
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        if (coordinator.executionRoundId() == 0L) return true;
        return !controller.isRemoved() && !controller.isRedstonePaused()
                && level.getBlockEntity(controller.getBlockPos()) == controller
                && controller.currentStructureSnapshot().formed() && controller.currentStructureSnapshot().structureAreaLoaded()
                && coordinator.controllerPermit(controller.getBlockPos()).eligible(coordinator.executionRoundId(),
                level.getGameTime(), level.tickRateManager().runsNormally()) && coordinator.reserveExecution(this, laneId);
    }

    private void releaseStartSlot() {
        if (controller.getLevel() instanceof ServerLevel level) {
            SharedIoCoordinator.get(level).releaseExecutionReservation(this, laneId);
        }
    }

    private void savePaidStart(CompoundTag output, PreparedStart prepared, HolderLookup.Provider registries) {
        ActiveMachineRecipe saved = new ActiveMachineRecipe(prepared.recipe().source(), prepared.plan().parallelism(),
                prepared.effective());
        saved.setParallelism(prepared.plan().parallelism());
        List<Integer> consumed = new ArrayList<>();
        int planIndex = 0;
        for (MachineRequirement requirement : prepared.effective().requirements()) {
            boolean input = requirement.io() == IOType.INPUT && (ItemRequirement.TYPE.equals(requirement.type())
                    || FluidRequirement.TYPE.equals(requirement.type()) || LoadedChemicalRequirement.TYPE.equals(requirement.type())
                    || SourceRequirement.TYPE.equals(requirement.type()) || ManaRequirement.TYPE.equals(requirement.type())
                    || requirement instanceof LightningRequirement);
            boolean executed = ledger.state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID
                    ? prepared.plan().hasOperations(planIndex) : prepared.plan().wasNativeExecuted(planIndex);
            consumed.add(input && executed ? 1 : 0);
            planIndex++;
        }
        saved.setInputConsumptionPlan(ledger.state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID && acceptedRecipe != null
                ? acceptedRecipe.inputConsumptionPlan() : new ActiveMachineRecipe.InputConsumptionPlan(consumed));
        if (ledger.state().phase() != RecipeCommitLedger.Phase.RESOURCE_PAID) saved.getDataCompound().putBoolean("native_start_uncertain", true);
        if (!prepared.prefetches().isEmpty()) {
            Map<String, StoredPrefetch> allocations = new LinkedHashMap<>();
            for (PreparedPrefetch prefetch : prepared.prefetches()) {
                if (!prefetch.attempted || prefetch.released) continue;
                long confirmed = prefetch.confirmedAmount();
                if (confirmed == 0L) continue;
                long amount = Math.max(0L, confirmed);
                long uncertain = confirmed < 0L ? prefetch.plan().amount() : 0L;
                StoredPrefetch old = allocations.get(prefetch.reservationKey());
                allocations.put(prefetch.reservationKey(), new StoredPrefetch(prefetch.reservationKey(),
                        Math.addExact(old == null ? 0L : old.amount(), amount),
                        Math.addExact(old == null ? 0L : old.batchAmount(), prefetch.plan().amount()),
                        Math.addExact(old == null ? 0L : old.uncertainAmount(), uncertain)));
            }
            ListTag tags = new ListTag();
            allocations.forEach((key, allocation) -> {
                CompoundTag tag = new CompoundTag();
                tag.putString(PREFETCH_ALLOCATION_KEY, key);
                tag.putLong(PREFETCH_ALLOCATION_AMOUNT, allocation.amount());
                tag.putLong(PREFETCH_BATCH_AMOUNT, allocation.batchAmount());
                if (allocation.uncertainAmount() > 0L) tag.putLong(PREFETCH_UNCERTAIN_AMOUNT, allocation.uncertainAmount());
                tags.add(tag);
            });
            if (!tags.isEmpty()) saved.getDataCompound().put(PREFETCH_ALLOCATIONS_KEY, tags);
        }
        output.putBoolean("active", true);
        saveInputScope(output, prepared.plan().inputScope());
        ledger.save(output);
        CompoundTag failureTag = new CompoundTag();
        FailureStatusCodec.write(failureTag, failure);
        output.put("failure", failureTag);
        output.putLong("structure_version", prepared.runtime().structure().version());
        output.putLong("capability_version", prepared.runtime().capabilityVersion());
        output.putLong("modifier_version", prepared.runtime().modifierVersion());
        output.putLong("component_state_version", prepared.runtime().stateVersion());
        CompoundTag recipeTag = new CompoundTag();
        saved.serialize(recipeTag, registries);
        output.put("recipe", recipeTag);
    }

    private ExecutionStatus missingInputStatus() {
        return failure(BuiltinFailureReasons.MISSING_INPUT, FailurePhase.RECIPE_START, Map.of());
    }

    private void activatePrefetches(List<PreparedPrefetch> prefetches, List<MachineRequirement> requirements,
                                    long parallelism) {
        Map<String, ActivePrefetch> allocations = new LinkedHashMap<>();
        for (PreparedPrefetch prefetch : prefetches) {
            if (!prefetch.attempted || prefetch.released || prefetch.transferred) continue;
            long confirmed = prefetch.confirmedAmount();
            if (confirmed == 0L) continue;
            long amount = Math.max(0L, confirmed);
            long uncertain = confirmed < 0L ? prefetch.plan().amount() : 0L;
            ActivePrefetch existing = allocations.get(prefetch.reservationKey());
            long remaining = Math.addExact(existing == null ? 0L : existing.remaining(), amount);
            long batch = Math.addExact(existing == null ? 0L : existing.batchAmount(), prefetch.plan().amount());
            allocations.put(prefetch.reservationKey(), new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    remaining, batch, Math.addExact(existing == null ? 0L : existing.uncertainAmount(), uncertain)));
            prefetch.transferred = true;
        }
        activePrefetches = List.copyOf(allocations.values());
        prefetchedEnergyPerTick = prefetchedEnergyPerTick(requirements, parallelism);
        prefetchedEnergyRemaining = activePrefetches.stream()
                .mapToLong(ActivePrefetch::remaining).reduce(0L, Math::addExact);
        prefetchedEnergyTickConsumed = 0L;
        prefetchedEnergyConsumedTick = -1;
        publishPrefetchOwners();
    }

    private void publishPrefetchOwners() {
        ActiveMachineRecipe recipe = activeRecipe;
        RecipeCommitLedger payment = ledger;
        List<ActivePrefetch> owned = activePrefetches;
        Map<String, Long> remainingByKey = new LinkedHashMap<>();
        Set<RecipeEnergyPrefetchFacet> facets = new LinkedHashSet<>(prefetchFacets(List.of()));
        for (ActivePrefetch prefetch : owned) {
            remainingByKey.put(prefetch.reservationKey(), prefetch.remaining());
            facets.add(prefetch.facet());
        }
        for (RecipeEnergyPrefetchFacet facet : facets) {
            if (activeRecipe != recipe || ledger != payment || activePrefetches != owned) return;
            facet.onRecipeReservationChanged(this, remainingByKey.getOrDefault(facet.reservationKey(), 0L),
                    () -> { if (activeRecipe == recipe && ledger == payment) invalidate(); });
            if (activeRecipe != recipe || ledger != payment || activePrefetches != owned) return;
            long nextBatch = owned.stream()
                    .filter(prefetch -> prefetch.reservationKey().equals(facet.reservationKey()))
                    .mapToLong(ActivePrefetch::batchAmount).findFirst().orElse(0L);
            facet.onRecipeContinuationRequested(this, errorPaused() ? 0L : nextBatch);
        }
    }

    /** Called after a complete candidate search, not after a last-recipe retry alone. */
    public void stopEnergyPrefetch() {
        Set<RecipeEnergyPrefetchFacet> facets = new LinkedHashSet<>(prefetchFacets(List.of()));
        for (ActivePrefetch prefetch : activePrefetches) facets.add(prefetch.facet());
        for (RecipeEnergyPrefetchFacet facet : facets) facet.onRecipeContinuationRequested(this, 0L);
    }

    private boolean restorePrefetches(ActiveMachineRecipe restored, List<MachineRequirement> requirements) {
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        prefetchedEnergyTickConsumed = 0L;
        prefetchedEnergyConsumedTick = -1;
        CompoundTag data = restored.getDataCompound();
        boolean partialStart = data.getBoolean("native_start_uncertain");
        if (partialStart && (!errorPaused() || restored.getTick() != 0 || restored.isFinishPending())) return false;
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
            long batchAmount = allocation.contains(PREFETCH_BATCH_AMOUNT)
                    ? allocation.getLong(PREFETCH_BATCH_AMOUNT) : -1L;
            if (allocation.contains(PREFETCH_BATCH_AMOUNT) && batchAmount < amount) return false;
            long uncertain = allocation.contains(PREFETCH_UNCERTAIN_AMOUNT)
                    ? allocation.getLong(PREFETCH_UNCERTAIN_AMOUNT) : 0L;
            if (uncertain < 0L || uncertain > 0L && !partialStart
                    || uncertain > 0L && (batchAmount < 0L || uncertain > batchAmount - amount)) return false;
            allocations.add(new StoredPrefetch(reservationKey, amount, batchAmount, uncertain));
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
        long tickConsumed = data.getLong(PREFETCH_TICK_CONSUMED_KEY);
        if (tickConsumed < 0L || tickConsumed > perTick || tickConsumed > remaining
                || (restored.isFinishPending() && tickConsumed != 0L)) return false;
        remaining -= tickConsumed;
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
        if (partialStart ? allocationTotal > remaining : allocationTotal != remaining) return false;
        long batchTotal = 0L;
        boolean legacy = false;
        try {
            for (StoredPrefetch allocation : allocations) {
                legacy |= allocation.batchAmount() < 0L;
                batchTotal = Math.addExact(batchTotal,
                        allocation.batchAmount() < 0L ? allocation.amount() : allocation.batchAmount());
            }
        } catch (ArithmeticException exception) {
            return false;
        }
        if (batchTotal > total || !partialStart && !legacy && batchTotal != total) return false;
        long unassigned = total - batchTotal;
        try {
            for (StoredPrefetch allocation : allocations) {
                RecipeEnergyPrefetchFacet facet = facetsByKey.get(allocation.reservationKey());
                long batchAmount = allocation.batchAmount();
                if (batchAmount < 0L) {
                    // Old ledgers retained only balances. Reconstruct a full batch in debit order.
                    batchAmount = allocation.amount() + unassigned;
                    unassigned = 0L;
                }
                ActivePrefetch restoredPrefetch = new ActivePrefetch(allocation.reservationKey(), facet,
                        allocation.amount(), batchAmount, allocation.uncertainAmount());
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
        prefetchedEnergyTickConsumed = tickConsumed;
        prefetchedEnergyConsumedTick = restored.getTick();
        return true;
    }

    private void savePrefetchAllocations() {
        if (activeRecipe == null) return;
        CompoundTag data = activeRecipe.getDataCompound();
        data.remove(PREFETCH_RESERVATION_KEY);
        data.remove(PREFETCH_ALLOCATIONS_KEY);
        data.remove(PREFETCH_TICK_CONSUMED_KEY);
        if (activePrefetches.isEmpty()) return;
        ListTag allocations = new ListTag();
        for (ActivePrefetch prefetch : activePrefetches) {
            CompoundTag allocation = new CompoundTag();
            allocation.putString(PREFETCH_ALLOCATION_KEY, prefetch.reservationKey());
            allocation.putLong(PREFETCH_ALLOCATION_AMOUNT, prefetch.remaining());
            allocation.putLong(PREFETCH_BATCH_AMOUNT, prefetch.batchAmount());
            if (prefetch.uncertainAmount() > 0L) allocation.putLong(PREFETCH_UNCERTAIN_AMOUNT, prefetch.uncertainAmount());
            allocations.add(allocation);
        }
        data.put(PREFETCH_ALLOCATIONS_KEY, allocations);
        if (!activeRecipe.isFinishPending() && prefetchedEnergyConsumedTick == activeRecipe.getTick()
                && prefetchedEnergyTickConsumed > 0L) {
            data.putLong(PREFETCH_TICK_CONSUMED_KEY, prefetchedEnergyTickConsumed);
        }
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
        ActiveMachineRecipe consumingRecipe = activeRecipe;
        RecipeCommitLedger payment = ledger;
        TickAttempt attempt = tickAttempt;
        long lifecycle = controller.lifecycleEpoch();
        List<ActivePrefetch> owned = activePrefetches;
        if (prefetchedEnergyConsumedTick != consumingRecipe.getTick()) {
            prefetchedEnergyConsumedTick = consumingRecipe.getTick();
            prefetchedEnergyTickConsumed = 0L;
        }
        long remaining = Math.min(prefetchedEnergyPerTick - prefetchedEnergyTickConsumed, prefetchedEnergyRemaining);
        if (remaining <= 0L) return null;
        List<ActivePrefetch> updated = new ArrayList<>(owned);
        for (int index = 0; index < updated.size() && remaining > 0L; index++) {
            if (!prefetchConsumptionCurrent(consumingRecipe, payment, attempt, lifecycle, owned)) return missingInputStatus();
            ActivePrefetch prefetch = updated.get(index);
            long consumed = Math.min(remaining, prefetch.remaining());
            if (consumed <= 0L) continue;
            CapabilityResult result;
            RecipeCommitLedger.State admission = payment.state();
            LongValueStorage observed = prefetch.facet() instanceof MachineCapability capability
                    ? CapabilityFactories.valueStorage(capability, LongValueStorage.class) : null;
            long beforeAmount = observed == null ? 0L : observed.amount();
            payment.beginNativeExecution();
            try {
                result = prefetch.facet().consumeReservation(consumed);
            } catch (RuntimeException exception) {
                logTickFailure("prefetch_consume", controller.currentRuntimeSnapshot(), consumingRecipe.getRecipe(), exception);
                // Use the same actual local-debit observation as a negative result. Unknown outcomes remain paused.
                result = CapabilityResult.failure(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
            }
            if (!prefetchConsumptionCurrent(consumingRecipe, payment, attempt, lifecycle, owned)) {
                if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                return missingInputStatus();
            }
            if (result == null || !result.success()) {
                // A negative result is not a rollback. An observable completed debit still owns tick credit.
                long moved = observed == null ? -1L : beforeAmount - observed.amount();
                if (moved < 0L || moved > consumed) {
                    payment.pauseAfterCommitFailure();
                } else {
                    if (moved > 0L) {
                        updated.set(index, new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                                prefetch.remaining() - moved, prefetch.batchAmount()));
                        activePrefetches = List.copyOf(updated);
                        owned = activePrefetches;
                        prefetchedEnergyRemaining -= moved;
                        prefetchedEnergyTickConsumed += moved;
                    }
                    payment.completePrefetchDebit(admission);
                    if (moved > 0L) prefetch.facet().onRecipeReservationChanged(this,
                            prefetch.remaining() - moved,
                            () -> { if (activeRecipe == consumingRecipe && ledger == payment) invalidate(); });
                    if (!prefetchConsumptionCurrent(consumingRecipe, payment, attempt, lifecycle, owned)) return missingInputStatus();
                }
                return result == null || result.status() == null
                        ? failure(BuiltinFailureReasons.MISSING_INPUT, FailurePhase.PER_TICK, Map.of()) : result.status();
            }
            updated.set(index, new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    prefetch.remaining() - consumed, prefetch.batchAmount()));
            // Publish each successful debit before visiting the next allocation, which may fail.
            activePrefetches = List.copyOf(updated);
            owned = activePrefetches;
            prefetchedEnergyRemaining -= consumed;
            prefetchedEnergyTickConsumed += consumed;
            payment.completePrefetchDebit(admission);
            prefetch.facet().onRecipeReservationChanged(this, prefetch.remaining() - consumed,
                    () -> { if (activeRecipe == consumingRecipe && ledger == payment) invalidate(); });
            if (!prefetchConsumptionCurrent(consumingRecipe, payment, attempt, lifecycle, owned)) return missingInputStatus();
            remaining -= consumed;
        }
        if (remaining > 0L) return missingInputStatus();
        return null;
    }

    private boolean prefetchConsumptionCurrent(ActiveMachineRecipe recipe, RecipeCommitLedger payment,
                                               @Nullable TickAttempt attempt, long lifecycle,
                                               List<ActivePrefetch> owned) {
        return activeRecipe == recipe && ledger == payment && activePrefetches == owned
                && controller.lifecycleEpoch() == lifecycle && !controller.isRemoved()
                && (attempt == null || attemptCurrent(attempt));
    }

    private void releaseActivePrefetches() {
        releaseActivePrefetches(false);
    }

    private void releaseActivePrefetches(boolean keepContinuation) {
        List<ActivePrefetch> prefetches = activePrefetches;
        ActiveMachineRecipe recipe = activeRecipe;
        RecipeCommitLedger payment = ledger;
        Set<RecipeEnergyPrefetchFacet> facets = new LinkedHashSet<>(prefetchFacets(List.of()));
        for (ActivePrefetch prefetch : prefetches) facets.add(prefetch.facet());
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        prefetchedEnergyTickConsumed = 0L;
        prefetchedEnergyConsumedTick = -1;
        if (!keepContinuation) for (RecipeEnergyPrefetchFacet facet : facets) {
            if (activeRecipe != recipe || ledger != payment) break;
            try {
                facet.onRecipeContinuationRequested(this, 0L);
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Failed to withdraw recipe continuation: controller={}", controller.getBlockPos(), exception);
            }
        }
        for (ActivePrefetch prefetch : prefetches) {
            try {
                if (prefetch.remaining() > 0L) prefetch.facet().releaseReservation(prefetch.remaining());
                if (activeRecipe == recipe && ledger == payment) {
                    prefetch.facet().onRecipeReservationChanged(this, 0L,
                            () -> { if (activeRecipe == recipe && ledger == payment) invalidate(); });
                }
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Failed to release prefetched recipe energy: controller={} key={}",
                        controller.getBlockPos(), prefetch.reservationKey(), exception);
            }
        }
    }

    private PlanningResult planPerTick(CraftingContext context) {
        List<MachineRequirement> source = perTickRequirements();
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
            if (isStressOutput(requirement)) continue;
            int original = cachedPerTickRequirementIndexes.get(index);
            requirements.add(requirement);
            indexes.add(original);
            if (isPerTickOutput(requirement)) policies.put(original, activeRecipe.getRecipe().allowPartialOutputs()
                    ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
        }
        return context.planFixedRequirements(requirements, activeRecipe.getParallelism(), policies, indexes);
    }

    private boolean commitPaidPlan(CraftingPlan plan) {
        TickAttempt attempt = tickAttempt;
        if (!attemptCurrent(attempt) || ledger.state().phase() != RecipeCommitLedger.Phase.READY) return false;
        RecipeCommitLedger payment = ledger;
        ActiveMachineRecipe recipe = activeRecipe;
        if (!consumePaidPrefetch(payment)) return false;
        RecipeCommitLedger.State admission = payment.state();
        StressSession owner = stressSession;
        List<MachineRequirement> originalRequirements = effectiveRequirements;
        try {
            if (!plan.commitRuntime(payment::beginNativeExecution, () -> attemptCurrent(attempt))) {
                if (!settleStressOnlyFailure(plan, originalRequirements, payment, admission, owner)
                        && payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                if (attemptIdentityCurrent(attempt)) waiting(plan.failure(), false);
                return false;
            }
            if (!attemptCurrent(attempt)) return false;
            if (!hasOrdinaryNativeEffects(plan, originalRequirements)) payment.completePrefetchDebit(admission);
            if (!commitStressOutputs(controller.executionRuntimeSnapshot())) {
                if (!settleStressOnlyFailure(plan, originalRequirements, payment, admission, owner)) payment.pauseAfterCommitFailure();
                return false;
            }
            payment.markResourcePaid(recipe.getTick(), recipe.needsFinishCommit());
            return attemptCurrent(attempt);
        } catch (RuntimeException exception) {
            if (settleStressOnlyFailure(plan, originalRequirements, payment, admission, owner)) {
                logTickFailure("stress_input_commit", controller.executionRuntimeSnapshot(), recipe.getRecipe(), exception);
                if (attemptIdentityCurrent(attempt)) waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()), false);
            } else pausePaymentFailure(payment, -1L, exception);
            return false;
        }
    }

    private boolean settleStressOnlyFailure(CraftingPlan plan, List<MachineRequirement> requirements, RecipeCommitLedger payment,
                                            RecipeCommitLedger.State admission, StressSession owner) {
        if (hasOrdinaryNativeEffects(plan, requirements)) return false;
        owner.releaseAll();
        payment.completePrefetchDebit(admission);
        return payment.state().phase() == admission.phase();
    }

    private boolean commitStressOutputs() {
        for (MachineRequirement requirement : perTickRequirements()) {
            if (isStressOutput(requirement)) return commitStressOutputs(controller.executionRuntimeSnapshot());
        }
        return true;
    }

    private boolean commitStressOutputs(ControllerRuntimeSnapshot runtime) {
        TickAttempt attempt = tickAttempt;
        RecipeCommitLedger payment = ledger;
        ActiveMachineRecipe recipe = activeRecipe;
        if (!attemptCurrent(attempt)) return false;
        StressSession owner = stressSession;
        RecipeCommitLedger.State admission = payment.state();
        List<MachineRequirement> originalRequirements = effectiveRequirements;
        CraftingPlan plan = null;
        List<MachineRequirement> source = perTickRequirements();
        List<MachineRequirement> outputs = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            if (!isStressOutput(source.get(index))) continue;
            outputs.add(source.get(index));
            indexes.add(cachedPerTickRequirementIndexes.get(index));
        }
        if (outputs.isEmpty()) return true;
        try {
            PlanningResult result = context(runtime).planFixedRequirements(outputs, recipe.getParallelism(),
                    Map.of(), indexes);
            if (!attemptCurrent(attempt)) return false;
            plan = result.plan();
            if (!result.successful() || plan == null || plan.parallelism() < recipe.getParallelism()) {
                waiting(result.failure(), false);
                return false;
            }
            if (!plan.commitRuntime(payment::beginNativeExecution, () -> attemptCurrent(attempt))) {
                if (!settleStressOnlyFailure(plan, originalRequirements, payment, admission, owner)) payment.pauseAfterCommitFailure();
                if (activeRecipe == recipe && ledger == payment && tickAttempt == attempt
                        && controller.lifecycleEpoch() == attempt.lifecycleEpoch()) waiting(plan.failure(), false);
                return false;
            }
            payment.completePrefetchDebit(admission);
            return attemptCurrent(attempt);
        } catch (RuntimeException exception) {
            if ((plan == null || !settleStressOnlyFailure(plan, originalRequirements, payment, admission, owner))
                    && payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
            logTickFailure("stress_output_commit", runtime, recipe.getRecipe(), exception);
            if (activeRecipe == recipe && ledger == payment && tickAttempt == attempt
                    && controller.lifecycleEpoch() == attempt.lifecycleEpoch()) {
                waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()), false);
            }
            return false;
        }
    }

    private static boolean isStressOutput(MachineRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.OUTPUT && StressRequirement.TYPE.equals(requirement.type());
    }

    private boolean commitAsyncTickPlan(AsyncRequirementPlanner.PlanResult planned,
                                        ControllerRuntimeSnapshot runtime) {
        TickAttempt attempt = tickAttempt;
        if (!attemptCurrent(attempt) || !consumePaidPrefetch(ledger)) return false;
        if (planned == null || !planned.mainThreadRequirements().isEmpty() || !asyncOperationsInScope(planned)) {
            PlanningResult live = planPerTick(context(runtime));
            if (!attemptCurrent(attempt)) return false;
            if (!live.successful() || live.plan() == null) {
                waiting(live.failure(), isPowerWait(live.failure()));
                return false;
            }
            return commitPaidPlan(live.plan());
        }
        RecipeCommitLedger payment = ledger;
        ActiveMachineRecipe recipe = activeRecipe;
        List<AsyncPlanningFacet> facets = asyncTickPreparation != null && asyncTickPreparation.plan != null
                ? asyncTickPreparation.plan.capabilities().stream().map(capability ->
                        capability.sourceCapabilityIndex() < 0 || capability.sourceCapabilityIndex() >= components.capabilities().size()
                                ? null : components.capabilities().get(capability.sourceCapabilityIndex())
                                .facet(AsyncPlanningFacet.class).orElse(null)).toList()
                : components.capabilities().stream().map(capability -> capability.facet(AsyncPlanningFacet.class).orElse(null))
                        .filter(Objects::nonNull).toList();
        try {
            for (AsyncRequirementPlanner.PlannedOperation operation : planned.operations()) {
                if (!attemptCurrent(attempt)) return false;
                if (operation.capabilityIndex() < 0 || operation.capabilityIndex() >= facets.size()
                        || facets.get(operation.capabilityIndex()) == null) {
                    if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                    waiting(invalidInstalledStatus(), false);
                    return false;
                }
                payment.beginNativeExecution();
                CapabilityResult result = facets.get(operation.capabilityIndex()).commit(operation.operation());
                if (!attemptCurrent(attempt)) return false;
                if (result == null || !result.success()) {
                    payment.pauseAfterCommitFailure();
                    waiting(result == null ? null : result.status(), false);
                    return false;
                }
            }
            if (!commitStressOutputs(runtime)) {
                if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                return false;
            }
            if (!attemptCurrent(attempt)) return false;
            payment.markResourcePaid(recipe.getTick(), recipe.needsFinishCommit());
            return true;
        } catch (RuntimeException exception) {
            pausePaymentFailure(payment, -1L, exception);
            return false;
        }
    }

    private boolean asyncOperationsInScope(AsyncRequirementPlanner.PlanResult planned) {
        AsyncRequirementPlanner.PreparedPlan prepared = asyncTickPreparation == null ? null : asyncTickPreparation.plan;
        if (prepared == null) return activeInputScope == null || activeInputScope.kind() == InputScope.Kind.LEGACY;
        List<MachineCapability> capabilities = components.capabilities();
        List<MachineRequirement> requirements = perTickRequirements();
        InputRoutingFacet routing = components.capabilitySnapshot().facets(InputRoutingFacet.class).stream()
                .findFirst().orElse(null);
        for (AsyncRequirementPlanner.PlannedOperation operation : planned.operations()) {
            int requirementIndex = cachedPerTickRequirementIndexes.indexOf(operation.requirementIndex());
            if (operation.capabilityIndex() >= prepared.capabilities().size() || requirementIndex < 0) return false;
            int sourceIndex = prepared.capabilities().get(operation.capabilityIndex()).sourceCapabilityIndex();
            if (sourceIndex < 0 || sourceIndex >= capabilities.size()) return false;
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (activeInputScope != null && activeInputScope.kind() != InputScope.Kind.LEGACY
                    && RequirementPlanner.isMaterialInput(requirement.io() == IOType.INPUT
                    ? cn.howxu.mmcr.util.IOType.INPUT : cn.howxu.mmcr.util.IOType.OUTPUT,
                    operation.operation().capabilityId())) {
                if (routing == null || routing.sources().stream().noneMatch(source ->
                        source.capabilityIndex() == sourceIndex && activeInputScope.sources().contains(source.position()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<MachineRequirement> perTickRequirements() {
        if (cachedPerTickSource == effectiveRequirements && cachedPerTickConsumed == consumedAtStart
                && cachedPerTickRetained == retainedInputs && cachedPerTickPrefetches == activePrefetches) {
            return cachedPerTickRequirements;
        }
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        List<MachineRequirement> source = effectiveRequirements;
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                if (!activePrefetches.isEmpty() && requirement instanceof EnergyRequirement) continue;
                if (consumedAtStart.contains(index)) continue;
                // Startup probability is already decided; retained one-shot resources only remain conditions.
                if (retainedInputs.contains(index) && requirement instanceof SourceRequirement sourceInput
                        && sourceInput.consumeChance() > 0F && sourceInput.consumeChance() < 1F) {
                    requirement = new SourceRequirement(sourceInput.io(), sourceInput.amount(), sourceInput.chance(),
                            0F, sourceInput.tags());
                } else if (retainedInputs.contains(index) && requirement instanceof ManaRequirement manaInput
                        && manaInput.consumeChance() > 0F && manaInput.consumeChance() < 1F) {
                    requirement = new ManaRequirement(manaInput.io(), manaInput.amount(), manaInput.chance(),
                            0F, manaInput.tags());
                } else if (retainedInputs.contains(index) && requirement instanceof LoadedChemicalRequirement chemicalInput
                        && chemicalInput.consumeChance() > 0F && chemicalInput.consumeChance() < 1F) {
                    requirement = new LoadedChemicalRequirement(chemicalInput.io(), chemicalInput.ingredient(),
                            chemicalInput.chance(), chemicalInput.tags(), 0F);
                }
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
                indexes.add(index);
            } else if (isPerTickOutput(requirement)) {
                requirements.add(requirement);
                indexes.add(index);
            }
        }
        cachedPerTickSource = effectiveRequirements;
        cachedPerTickConsumed = consumedAtStart;
        cachedPerTickRetained = retainedInputs;
        cachedPerTickPrefetches = activePrefetches;
        cachedPerTickRequirements = List.copyOf(requirements);
        cachedPerTickRequirementIndexes = List.copyOf(indexes);
        return cachedPerTickRequirements;
    }

    private List<MachineRequirement> finishRequirements() {
        return effectiveRequirements().stream()
                .filter(requirement -> !(requirement instanceof AirRequirement))
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
                || StressRequirement.TYPE.equals(requirement.type())
                || MekanismRecipeTypes.HEAT.equals(requirement.type().id())
                || AirRequirement.TYPE.equals(requirement.type()));
    }

    private static boolean isPerTickOutput(MachineOutput output) {
        return MekanismRecipeTypes.HEAT.equals(output.outputType().id())
                || StressRequirement.TYPE.id().equals(output.outputType().id());
    }

    private RecipeBehavior recipeBehavior(ControllerRuntimeSnapshot runtime) {
        MachineBehavior behavior = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() == null ? null : runtime.structure().configuredMachine().behavior()
                : runtime.structure().machine().behavior();
        return behavior instanceof RecipeBehavior recipeBehavior ? recipeBehavior : null;
    }

    private boolean recipeBelongsToMachine(MachineRecipe recipe) {
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        return recipePoolId != null && recipePoolId.equals(recipe.recipePoolId());
    }

    private void logCallbackFailure(String phase, ControllerRuntimeSnapshot runtime, MachineRecipe recipe,
                                    RuntimeException exception) {
        MMCR.LOG.warn("Machine behavior callback failed: phase={} machine={} recipe={} controller={}", phase,
                runtime.machineId(), recipe.id(), controller.getBlockPos(), exception);
    }

    private void logTickFailure(String phase, ControllerRuntimeSnapshot runtime, MachineRecipe recipe,
                                RuntimeException exception) {
        MMCR.LOG.warn("Native recipe tick failed: phase={} machine={} recipe={} controller={}", phase,
                runtime.machineId(), recipe.id(), controller.getBlockPos(), exception);
    }

    private void logFinishFailure(String phase, ControllerRuntimeSnapshot runtime, MachineRecipe recipe,
                                   RuntimeException exception) {
        MMCR.LOG.warn("Machine recipe finish failed: phase={} machine={} recipe={} controller={}", phase,
                runtime.machineId(), recipe.id(), controller.getBlockPos(), exception);
    }

    private List<RecipeModifier> contextModifiers(ControllerRuntimeSnapshot runtime) {
        return MachineModifier.recipeModifiers(components.modifierList());
    }

    private EffectiveRecipe resolve(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        long catalog = catalogVersion(recipe.recipePoolId());
        if (cachedEffectiveRecipe != null && cachedEffectiveRecipe.source() == recipe
                && cachedEffectiveStructure == runtime.structure().version()
                && cachedEffectiveCapability == runtime.capabilityVersion()
                && cachedEffectiveModifier == runtime.modifierVersion()
                && cachedEffectiveState == runtime.stateVersion()
                && cachedEffectiveUpgrade == runtime.upgradeContentRevision() && cachedEffectiveCatalog == catalog
                && cachedEffectiveMaxParallelism == runtime.maxParallelism()
                && cachedEffectiveLaneLimit == runtime.factory().laneLimit()) {
            return cachedEffectiveRecipe;
        }
        cachedEffectiveRecipe = new EffectiveRecipeResolver().resolve(recipe, runtime, components.modifierList());
        cachedEffectiveStructure = runtime.structure().version();
        cachedEffectiveCapability = runtime.capabilityVersion();
        cachedEffectiveModifier = runtime.modifierVersion();
        cachedEffectiveState = runtime.stateVersion();
        cachedEffectiveUpgrade = runtime.upgradeContentRevision();
        cachedEffectiveCatalog = catalog;
        cachedEffectiveMaxParallelism = runtime.maxParallelism();
        cachedEffectiveLaneLimit = runtime.factory().laneLimit();
        return cachedEffectiveRecipe;
    }

    /** Returns outputs using the same runtime modifier context as pattern-start preparation. */
    public List<MachineOutput> runtimeMachineOutputs(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        return recipe.runtimeMachineOutputs(contextModifiers(runtime));
    }

    /** Resolves candidate limits and duration without beforeStart, planning, or reserving a slot. */
    public @Nullable EffectiveRecipe effectivePatternRecipe(MachineRecipe recipe) {
        if (recipe == null || !patternOwnerCurrent() || !recipeBelongsToMachine(recipe)) return null;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!runtime.structure().formed() || !runtime.structure().structureAreaLoaded() || controller.isRedstonePaused()
                || recipeBehavior(runtime) == null
                || !runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())
                || !controller.recipesForMachine().contains(recipe)) return null;
        return resolve(recipe, runtime);
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
        captureInputState(requirements, plan, Set.of());
    }

    private void captureInputState(List<MachineRequirement> requirements, CraftingPlan plan,
                                   Set<Integer> requestValidatedInputs) {
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(ItemRequirement.TYPE.equals(requirement.type())
                    || FluidRequirement.TYPE.equals(requirement.type())
                    || LoadedChemicalRequirement.TYPE.equals(requirement.type())
                    || SourceRequirement.TYPE.equals(requirement.type())
                    || ManaRequirement.TYPE.equals(requirement.type()) || requirement instanceof LightningRequirement)
                    || requirement.io() != RecipeModifier.IOType.INPUT) {
                continue;
            }
            boolean executed = ledger.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED
                    ? plan.wasNativeExecuted(index) : plan.hasOperations(index);
            // "Consumed" also marks request materials already checked and returned at admission.
            // Persisting this through the existing plan keeps reloads from rechecking expired AE views.
            if (executed || requestValidatedInputs.contains(index)) consumed.add(index);
            else retained.add(index);
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
        presentationEpoch++;
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

    private HolderLookup.Provider registryAccess() {
        if (controller.getLevel() != null && controller.getLevel().registryAccess() != null) {
            return controller.getLevel().registryAccess();
        }
        return HolderLookup.Provider.create(java.util.stream.Stream.empty());
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

    /**
     * Keeps the pre-grant tick and lazily owns callback data for either execution path.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class AsyncTickPreparation {
        private final ControllerRuntimeSnapshot runtime;
        private final int currentTick;
        private final ActiveMachineRecipe recipe;
        private final long lifecycleEpoch;
        private final TickAttempt attempt;
        private @Nullable AsyncRequirementPlanner.PreparedPlan plan;
        private @Nullable RecipeTickContext tickContext;
        private boolean afterInputsAttempted;
        private boolean afterInputsPassed;
        private boolean paidTickSettled;

        private AsyncTickPreparation(ControllerRuntimeSnapshot runtime, ActiveMachineRecipe recipe, long lifecycleEpoch,
                                     TickAttempt attempt) {
            this.runtime = runtime;
            this.currentTick = recipe.getTick();
            this.recipe = recipe;
            this.lifecycleEpoch = lifecycleEpoch;
            this.attempt = attempt;
        }

        private ControllerRuntimeSnapshot runtime() {
            return runtime;
        }
    }

    /** Identity shared by preparation, payment and both AFTER phases. @author howxu <dev@howxu.cn> */
    private record TickAttempt(ActiveMachineRecipe recipe, RecipeCommitLedger payment,
                               @Nullable InstalledRecipeProgram installed, long lifecycleEpoch) { }

    private record StoredPrefetch(String reservationKey, long amount, long batchAmount, long uncertainAmount) {
    }

    private record ActivePrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet, long remaining,
                                  long batchAmount, long uncertainAmount) {
        private ActivePrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet, long remaining, long batchAmount) {
            this(reservationKey, facet, remaining, batchAmount, 0L);
        }
    }

    private static String failureUnloc(ExecutionStatus status) {
        if (status == null) return "";
        FailureReason reason = status.reason();
        return (reason == null ? BuiltinFailureReasons.UNKNOWN : reason).translationKey();
    }
}
