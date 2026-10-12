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
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirementHandler;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.Stage;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.VersionStamp;
import cn.howxu.mmcr.internal.recipe.RecipeProgramCompiler;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
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
import net.minecraft.server.level.ServerLevel;
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
    private final StressSession stressSession = new StressSession();
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
        return new ProgramCompilation(pendingCompilationIdentity, activeRecipe.getRecipe().id(), totalTick(), parallelism(), versions, start, tick, finish,
                installedBehavior.hasRecipeTick() || components.hasTickFacets());
    }

    public @Nullable InstalledRecipeProgram installCompiledProgram(ExecutionIdentity identity, CompiledRecipeProgram program) {
        if (!active() || errorPaused() || !versionsCurrent() || !identity.equals(pendingCompilationIdentity) || identity.controllerInstance() != controllerIdentity
                || identity.lifecycleEpoch() != controller.lifecycleEpoch() || !identity.laneId().equals(laneId)
                || installedProgram != null || !program.recipeId().equals(activeRecipe.getRecipe().id())
                || program.parallelism() != parallelism() || program.duration() != totalTick()
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
        discardAsyncTickPreparation();
        if (lastInstalledPreparationGameTime == gameTime) return false;
        lastInstalledPreparationGameTime = gameTime;
        preparedInstalledRound = roundId;
        preparedInstalledGameTime = gameTime;
        if (!beginTickAttempt()) return false;
        // A custom capability may expose its facet on the next live query, including at BEFORE_RECIPE.
        if (needsRecipeTickContext(installedBehavior) || components.hasTickFacets()) {
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
        // attemptCurrent includes the installed program's full live validation.
        installedPreparationReady = tickAttempt != null && tickAttempt.installed() == execution && attemptCurrent(tickAttempt);
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

    boolean advanceInstalled(InstalledRecipeProgram execution) {
        if (attemptExecuting || tickAttempt != null && !installedPreparationReady) return false;
        attemptExecuting = true;
        try {
            return advanceInstalledAttempt(execution);
        } finally {
            installedPreparationReady = false;
            discardAsyncTickPreparation();
            attemptExecuting = false;
        }
    }

    private boolean advanceInstalledAttempt(InstalledRecipeProgram execution) {
        if (finishPending()) {
            if (!execution.finishAttemptReady()) return false;
            finish();
            installedCompletion.run();
            return execution.ledger().state().outputPaid();
        }
        if (!execution.current() || errorPaused()) return false;
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
        if (!commitStressOutputs(controller.executionRuntimeSnapshot())) {
            payment.pauseAfterCommitFailure();
            return false;
        }
        payment.markResourcePaid(recipe.getTick(), recipe.needsFinishCommit());
        installedPreparationReady = false;
        completePaidTick(asyncTickPreparation, recipe, payment, attempt);
        flushAsyncScreenText();
        if (execution.finishAttemptReady()) finish();
        installedCompletion.run();
        controller.syncRecipeRuntimeFailure(this);
        return true;
    }

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
        if (result.plan().commitRuntime(payment::beginNativeExecution, () -> attempt == null
                ? installedProgram != null && installedProgram.current() : attemptCurrent(attempt))) return null;
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
        if (!attemptCurrent(attempt)) return false;
        if (rejected == null) return true;
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
        if (recipe == null || requestedParallelism <= 0 || active() || errorPaused() || patternStartReserved
                || pendingPatternStart != null) return null;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(recipe)
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
        List<MachineRequirement> requirements = effective.requirements().stream()
                .map(MachineRequirement::copyOf).toList();
        List<RecipeEnergyPrefetchFacet> facets = prefetchFacets(requestCapabilities);
        PlanningResult result = planStartInputs(context(runtime, requestCapabilities), requirements, facets,
                effectiveParallelism);
        CraftingPlan plan = result.plan();
        if (!result.successful() || plan == null) return null;
        for (RecipeEnergyPrefetchFacet facet : facets) {
            facet.onRecipeSearchStarted(this);
            facet.onRecipeSearchCandidate(this, true);
        }
        List<PreparedPrefetch> prefetches = null;
        try {
            prefetches = planPrefetches(requirements, effective.duration(), plan.parallelism(), facets);
        } finally {
            for (RecipeEnergyPrefetchFacet facet : facets) {
                facet.onRecipeSearchFinished(this, prefetches != null, true);
            }
        }
        if (prefetches == null) return null;
        pendingPatternStart = new PreparedStart(effectiveRecipe, runtime,
                catalogVersion(effectiveRecipe.source().recipePoolId()), effective, plan,
                prefetches);
        ledger = new RecipeCommitLedger();
        return pendingPatternStart;
    }

    public boolean reservePatternStart() {
        if (active() || errorPaused() || patternStartReserved || !reserveStartSlot()) return false;
        patternStartReserved = true;
        return true;
    }

    public void releasePatternStart() {
        PreparedStart prepared = pendingPatternStart;
        pendingPatternStart = null;
        if (pendingPaymentStart == prepared) pendingPaymentStart = null;
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
        if (!patternStartReserved || active() || prepared == null || prepared != pendingPatternStart) return false;
        if (!preparedStartCurrent(prepared)) {
            discardPatternStart(prepared);
            return false;
        }
        pendingPaymentStart = prepared;
        RecipeCommitLedger payment = ledger;
        ExecutionStatus commitFailure = commitPreparedStart(prepared);
        if (commitFailure == null) return true;
        if (ledger != payment) return false;
        quarantineNativeStart(prepared, payment);
        fail(commitFailure);
        return false;
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
        for (PreparedPrefetch prefetch : prepared.prefetches()) {
            if (prefetch.committed) {
                prefetch.facet().releaseReservation(prefetch.plan().amount());
                prefetch.committed = false;
            } else {
                prefetch.facet().restoreReservation(prefetch.plan().amount());
            }
        }
    }

    private void releasePaidStartPrefetches(PreparedStart prepared) {
        if (pendingPrefetchStart == null || pendingPrefetchStart.prepared() != prepared) return;
        pendingPrefetchStart = null;
        for (PreparedPrefetch prefetch : prepared.prefetches()) {
            try {
                prefetch.facet().releaseReservation(prefetch.plan().amount());
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Failed to release committed start energy: controller={} key={}",
                        controller.getBlockPos(), prefetch.reservationKey(), exception);
            }
        }
    }

    private void discardPatternStart(PreparedStart prepared) {
        if (pendingPatternStart == prepared) releasePatternStart();
        else releasePreparedPrefetches(prepared);
    }

    boolean preparedStartCurrent(PreparedStart prepared) {
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
        activeRecipe.setParallelism(prepared.plan().parallelism());
        startPlan = prepared.plan();
        finishPlan = null;
        effectiveRequirements = MachineRequirement.copyList(prepared.effective().requirements().stream()
                .map(MachineRequirement::copyOf).toList());
        effectiveOutputs = MachineOutput.copyList(prepared.effective().outputs());
        presentationEpoch++;
        ActiveMachineRecipe activatingRecipe = activeRecipe;
        activatePrefetches(prepared.prefetches(), effectiveRequirements, activeRecipe.getParallelism());
        if (ledger != payment.payment() || activeRecipe != activatingRecipe
                || controller.lifecycleEpoch() != payment.lifecycle()) return false;
        captureInputState(effectiveRequirements, prepared.plan());
        captureVersions(prepared.runtime());
        pendingPatternStart = null;
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

    static final class PreparedPrefetch {
        private final String reservationKey;
        private final RecipeEnergyPrefetchFacet facet;
        private final RecipeEnergyPrefetchFacet.PrefetchPlan plan;
        private boolean committed;

        PreparedPrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet,
                         RecipeEnergyPrefetchFacet.PrefetchPlan plan) {
            this.reservationKey = reservationKey;
            this.facet = facet;
            this.plan = plan;
        }

        String reservationKey() { return reservationKey; }

        RecipeEnergyPrefetchFacet facet() { return facet; }

        RecipeEnergyPrefetchFacet.PrefetchPlan plan() { return plan; }
    }

    /** Owns start reservations until activation or cancellation, independently of a replacement ledger.
     * @author howxu <dev@howxu.cn>
     */
    private record PendingPrefetchStart(PreparedStart prepared, RecipeCommitLedger payment, long lifecycle) { }

    public CraftingStatus start(MachineRecipe recipe, long requestedParallelism) {
        return start(recipe, requestedParallelism, null);
    }

    /** Executes the start behavior callback before a shared-IO transaction is requested. */
    public @Nullable RecipeStartContext.ExecutionSnapshot prepareAsyncStart(MachineRecipe recipe, long requestedParallelism) {
        if (recipe == null || requestedParallelism <= 0 || active() || patternStartReserved) return null;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!recipeBelongsToMachine(recipe)
                || !runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) return null;
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return null;
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> requirements = effectiveRecipe.requirements();
        if (!behavior.hasBeforeStart()) {
            return new RecipeStartContext.ExecutionSnapshot(effectiveRecipe.duration(),
                    MachineRequirement.copyList(requirements), effectiveRecipe.outputs());
        }
        MachineBehaviorContext machineContext = behaviorContext();
        long lifecycle = controller.lifecycleEpoch();
        RecipeCommitLedger originalLedger = ledger;
        RecipeStartContext startContext = new RecipeStartContext(machineContext, recipe, requestedParallelism,
                effectiveParallelism, effectiveRecipe.duration(),
                MachineRequirement.copyList(requirements), effectiveRecipe.outputs());
        try {
            behavior.beforeStart().accept(startContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeStart", runtime, recipe, exception);
            if (lifecycle != controller.lifecycleEpoch() || originalLedger != ledger) return null;
            fail(failure(BuiltinFailureReasons.RECIPE_BEHAVIOR, FailurePhase.RECIPE_START, Map.of()));
            return null;
        } finally {
            flushScreenTextReplacements(machineContext.screenText());
        }
        if (lifecycle != controller.lifecycleEpoch() || originalLedger != ledger || active()) return null;
        if (startContext.cancelled()) {
            stopEnergyPrefetch();
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
        if (active() || errorPaused() || patternStartReserved || pendingPaymentStart != null) return status;
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
        if (effective == null) {
            releaseStartSlot();
            return status;
        }
        EffectiveRecipe effectiveRecipe = resolve(recipe, runtime);
        long effectiveParallelism = Math.max(1L, Math.min(requestedParallelism,
                effectiveRecipe.parallelismLimit()));
        List<MachineRequirement> requirements = effective.requirements().stream()
                .map(MachineRequirement::copyOf).toList();
        List<RecipeEnergyPrefetchFacet> facets = prefetchFacets(List.of());
        CraftingContext context = context(runtime);
        boolean restarting = restartRecipe == recipe;
        restartProgramAuthorized = restarting && restartRuleCurrent(recipe, runtime);
        if (restartProgramAuthorized) effectiveParallelism = Math.min(effectiveParallelism, restartRule.maximumParallelism());
        PlanningResult result = planStartInputs(context, requirements, facets, effectiveParallelism);
        CraftingPlan plan = result.plan();
        if (!result.successful() || plan == null) {
            return fail(result.failure());
        }
        List<PreparedPrefetch> prefetches = planPrefetches(requirements, effective.duration(), plan.parallelism(), facets);
        if (prefetches == null) return fail(missingInputStatus());
        PreparedStart prepared = new PreparedStart(effectiveRecipe, runtime,
                catalogVersion(effectiveRecipe.source().recipePoolId()), effective, plan, prefetches);
        boolean committed = false;
        RecipeCommitLedger payment = new RecipeCommitLedger();
        ledger = payment;
        pendingPaymentStart = prepared;
        long lifecycle = controller.lifecycleEpoch();
        try {
            ExecutionStatus commitFailure = commitPreparedStart(prepared);
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
                releaseStressContributions();
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
        finishPlan = null;
        effectiveRequirements = MachineRequirement.copyList(effective.requirements().stream()
                .map(MachineRequirement::copyOf).toList());
        effectiveOutputs = MachineOutput.copyList(effective.outputs());
        presentationEpoch++;
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
        if (requirements.isEmpty()) return new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), List.of());
        // Scalar-only ticks are cheaper to plan against live storage during shared-IO arbitration.
        if (requirements.stream().allMatch(requirement -> requirement instanceof EnergyRequirement
                || requirement instanceof LoadedHeatRequirement)) {
            return new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(),
                    cachedPerTickRequirementIndexes);
        }
        AsyncRequirementPlanner.PreparedPlan prepared = context(preparation.runtime())
                .planAsync(requirements, activeRecipe.getParallelism(), cachedPerTickRequirementIndexes);
        if (!preparationCurrent(preparation)) return null;
        // A known fallback replans the entire tick, so worker operations would be discarded.
        return prepared.initialMainThreadRequirements().isEmpty() ? prepared
                : new AsyncRequirementPlanner.PreparedPlan(List.of(), List.of(), prepared.initialMainThreadRequirements());
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

    /** Commits worker-planned native operations inside the shared-IO arbitration transaction. */
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

    /** Runs the AFTER_INPUTS capability phase after the shared-IO transaction has committed. */
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
        if (result.failure() != null) {
            capabilityTickFailure = result.failure();
            waiting(result.failure(), false);
        } else if (capabilityTickFailure != null && capabilityTickFailure.equals(failure)) {
            capabilityTickFailure = null;
            failure = null;
            status = active() ? CraftingStatus.working() : CraftingStatus.IDLE;
        }
        if (result.failure() != null || result.stateChanged()) controller.syncRecipeRuntimeFailure(this);
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
        restartRecipe = activeRecipe == null ? null : activeRecipe.getRecipe();
        cancelInstalledProgram();
        if (activeRecipe != null) activeRecipe.applyTickGrant(true, true, currentGameTime());
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
                    recipeTickContext.machineContext()), () -> preparationCurrent(preparation), payment::beginNativeExecution);
            if (!preparationCurrent(preparation)) return false;
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
        return attempt != null && tickAttempt == attempt && activeRecipe == attempt.recipe() && ledger == attempt.payment()
                && controller.lifecycleEpoch() == attempt.lifecycleEpoch() && !controller.isRemoved() && !errorPaused()
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

    /**
     * Immutable scalar state used for factory change detection.
     *
     * @author howxu <dev@howxu.cn>
     */
    record StateObservation(@Nullable ResourceLocation recipeId, CraftingStatus.Status status, String statusMessage,
                            @Nullable ExecutionStatus failure, long structureVersion, long capabilityVersion,
                            long modifierVersion, int tick, int totalTick, long parallelism, long maxParallelism) {
        boolean matches(CraftingRuntime runtime) {
            MachineRecipe recipe = runtime.recipe();
            return Objects.equals(recipeId, recipe == null ? null : recipe.id())
                    && status == runtime.status.getStatus()
                    && Objects.equals(statusMessage, runtime.status.getUnlocMessage())
                    && Objects.equals(failure, runtime.failure)
                    && structureVersion == (runtime.structureVersion == Long.MIN_VALUE ? 0L : runtime.structureVersion)
                    && capabilityVersion == (runtime.capabilityVersion == Long.MIN_VALUE ? 0L : runtime.capabilityVersion)
                    && modifierVersion == (runtime.modifierVersion == Long.MIN_VALUE ? 0L : runtime.modifierVersion)
                    && tick == runtime.tickCount() && totalTick == runtime.totalTick()
                    && parallelism == runtime.parallelism() && maxParallelism == runtime.maxParallelism();
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
        return activeRecipe != null && activeRecipe.shouldRetryFinish(currentGameTime());
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
        StructureSnapshot structure = controller.currentStructureSnapshot();
        ComponentRuntime components = controller.componentRuntime();
        MachineRecipe recipe = activeRecipe.getRecipe();
        return structureVersion == structure.version()
                && capabilityVersion == components.capabilityVersion()
                && recipeBelongsToMachine(recipe)
                && components.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds());
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        return resourceDomain;
    }

    public void invalidate() {
        releaseStressContributions();
        releaseStartSlot();
        cancelInstalledProgram();
        ledger = new RecipeCommitLedger();
        restartRecipe = null;
        restartRule = null;
        restartProgramAuthorized = false;
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
        if (!restorePrefetches(restored, requirements)) {
            failLoad();
            return;
        }
        activeRecipe = restored;
        cancelInstalledProgram();
        ledger = new RecipeCommitLedger();
        ledger.settleTick(restored.getTick(), restored.isFinishPending());
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
                    || ManaRequirement.TYPE.equals(requirement.type()))
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
        publishPrefetchOwners();
    }

    public void rebindCurrentVersions() {
        if (!active()) return;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (capabilityVersion != runtime.capabilityVersion()) releaseStressContributions();
        if (!recipeBelongsToMachine(activeRecipe.getRecipe())) {
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
        long restoredUpgradeContentRevision = (input.contains("upgrade_content_revision") ? input.getLong("upgrade_content_revision") : Long.MIN_VALUE);
        restore(loaded.recipe(), domain,
                input.contains("structure_version") ? input.getLong("structure_version") : Long.MIN_VALUE,
                input.contains("capability_version") ? input.getLong("capability_version") : Long.MIN_VALUE,
                input.contains("modifier_version") ? input.getLong("modifier_version") : Long.MIN_VALUE,
                input.contains("component_state_version") ? input.getLong("component_state_version") : Long.MIN_VALUE);
        if (active()) {
            ledger = restoredLedger;
            if (restoredUpgradeContentRevision != Long.MIN_VALUE) {
                upgradeContentRevision = restoredUpgradeContentRevision;
            }
            restoreFailure(readFailure(input, loaded.recipe().getRecipe().id()));
            if (errorPaused() && failure == null) {
                restoreFailure(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()));
            }
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
        return waiting(nextFailure, true);
    }

    /** Only a pre-commit Create power check may retain the lane's previous input contributions. */
    private CraftingStatus waiting(@Nullable ExecutionStatus nextFailure, boolean allowInputRetention) {
        failure = nextFailure == null
                ? failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()) : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        boolean retainInput = allowInputRetention && isPowerWait(failure);
        if (asyncTickPreparation != null) asyncTickPowerWait = retainInput;
        if (retainInput) stressSession.releaseOutputs();
        else releaseStressContributions();
        if (activeRecipe != null && activeRecipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
            releaseStressContributions();
            cancelInstalledProgram();
            releaseActivePrefetches();
            activeRecipe = null;
            startPlan = null;
            finishPlan = null;
            clearEffectiveRecipe();
            resourceDomain = null;
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
        releaseStressContributions();
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
        return new CraftingContext(new CapabilitySnapshot(components.capabilities()), contextModifiers(runtime))
                .withReservationOwner(stressSession);
    }

    private CraftingContext context(ControllerRuntimeSnapshot runtime, List<MachineCapability> requestCapabilities) {
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
        for (MachineRequirement requirement : requirements) {
            if (!(requirement instanceof EnergyRequirement energy)
                    || energy.io() != RecipeModifier.IOType.INPUT) continue;
            long remaining = SaturatingLong.multiply(SaturatingLong.multiply(energy.fePerTick(), duration), parallelism);
            try {
                total = Math.addExact(total, remaining);
            } catch (ArithmeticException exception) {
                for (PreparedPrefetch prefetch : prefetches) {
                    prefetch.facet().restoreReservation(prefetch.plan().amount());
                }
                return null;
            }
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
        RecipeCommitLedger payment = ledger;
        long lifecycle = controller.lifecycleEpoch();
        pendingPrefetchStart = new PendingPrefetchStart(prepared, payment, lifecycle);
        Set<Integer> airInputIndexes = new HashSet<>();
        List<MachineRequirement> requirements = prepared.effective().requirements();
        PlanningReservations reservations = new PlanningReservations();
        for (int index = 0; index < requirements.size(); index++) {
            if (requirements.get(index) instanceof AirRequirement air && air.io() == RecipeModifier.IOType.INPUT) {
                CapabilityResult result = AirRequirementHandler.preflightStart(air, components.capabilities(),
                        new PlanningContext(prepared.plan().parallelism(), index, false, reservations));
                if (!result.success()) return result.status();
                airInputIndexes.add(index);
            }
        }
        try {
            for (PreparedPrefetch prefetch : prepared.prefetches()) {
                payment.beginNativeExecution();
                CapabilityResult result = prefetch.plan().operation().commit();
                if (result == null || !result.success()) {
                    payment.pauseAfterCommitFailure();
                    return result == null || result.status() == null ? missingInputStatus() : result.status();
                }
                prefetch.committed = true;
                if (payment != ledger || lifecycle != controller.lifecycleEpoch()) return missingInputStatus();
            }
            // Recheck live Air after prefetch callbacks, before any ordinary startup input.
            reservations = new PlanningReservations();
            for (int index : airInputIndexes) {
                CapabilityResult result = AirRequirementHandler.preflightStart((AirRequirement) requirements.get(index),
                        components.capabilities(), new PlanningContext(prepared.plan().parallelism(), index, false, reservations));
                if (!result.success()) return result.status();
            }
            if (!prepared.plan().commitInputsRuntime(airInputIndexes, payment::beginNativeExecution,
                    () -> payment == ledger && lifecycle == controller.lifecycleEpoch()
                            && pendingPaymentStart == prepared)) {
                if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                return prepared.plan().failure() == null ? missingInputStatus() : prepared.plan().failure();
            }
            payment.markResourcePaid(0, false);
            ExecutionIdentity identity = new ExecutionIdentity(controllerIdentity, lifecycle, laneId,
                    NEXT_EXECUTION_ID.incrementAndGet());
            acceptedRecipe = new ActiveMachineRecipe(prepared.recipe().source(), prepared.plan().parallelism(), prepared.effective());
            if (controller.getLevel() instanceof ServerLevel level && SharedIoCoordinator.get(level).executionRoundId() != 0L
                    && !SharedIoCoordinator.get(level).acceptExecution(this, laneId, identity, payment, acceptedRecipe)) {
                payment.pauseAfterCommitFailure();
                return missingInputStatus();
            }
            return null;
        } catch (RuntimeException exception) {
            payment.pauseAfterCommitFailure();
            throw exception;
        } finally {
            if (payment.state().phase() != RecipeCommitLedger.Phase.RESOURCE_PAID) releaseStressContributions();
        }
    }

    private void quarantineNativeStart(PreparedStart prepared, RecipeCommitLedger payment) {
        if (payment != ledger || payment.state().phase() == RecipeCommitLedger.Phase.READY || active()) return;
        payment.pauseAfterCommitFailure();
        activeRecipe = new ActiveMachineRecipe(prepared.recipe().source(), prepared.plan().parallelism(), prepared.effective());
        activeRecipe.setParallelism(prepared.plan().parallelism());
        effectiveRequirements = MachineRequirement.copyList(prepared.effective().requirements());
        effectiveOutputs = MachineOutput.copyList(prepared.effective().outputs());
        captureInputState(effectiveRequirements, prepared.plan());
        captureVersions(prepared.runtime());
        activeRecipe.getDataCompound().putBoolean("native_start_uncertain", true);
        presentationEpoch++;
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
                    || SourceRequirement.TYPE.equals(requirement.type()) || ManaRequirement.TYPE.equals(requirement.type()));
            boolean executed = ledger.state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID
                    ? prepared.plan().hasOperations(planIndex) : prepared.plan().wasNativeExecuted(planIndex);
            consumed.add(input && executed ? 1 : 0);
            planIndex++;
        }
        saved.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(consumed));
        if (ledger.state().phase() != RecipeCommitLedger.Phase.RESOURCE_PAID) saved.getDataCompound().putBoolean("native_start_uncertain", true);
        if (!prepared.prefetches().isEmpty() && ledger.state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID) {
            Map<String, Long> allocations = new LinkedHashMap<>();
            for (PreparedPrefetch prefetch : prepared.prefetches()) {
                allocations.merge(prefetch.reservationKey(), prefetch.plan().amount(), SaturatingLong::add);
            }
            ListTag tags = new ListTag();
            allocations.forEach((key, amount) -> {
                CompoundTag tag = new CompoundTag();
                tag.putString(PREFETCH_ALLOCATION_KEY, key);
                tag.putLong(PREFETCH_ALLOCATION_AMOUNT, amount);
                tag.putLong(PREFETCH_BATCH_AMOUNT, amount);
                tags.add(tag);
            });
            saved.getDataCompound().put(PREFETCH_ALLOCATIONS_KEY, tags);
        }
        output.putBoolean("active", true);
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
            ActivePrefetch existing = allocations.get(prefetch.reservationKey());
            long remaining = existing == null ? prefetch.plan().amount()
                    : Math.addExact(existing.remaining(), prefetch.plan().amount());
            allocations.put(prefetch.reservationKey(), new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    remaining, remaining));
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
        Map<String, Long> remainingByKey = new LinkedHashMap<>();
        Set<RecipeEnergyPrefetchFacet> facets = new LinkedHashSet<>(prefetchFacets(List.of()));
        for (ActivePrefetch prefetch : activePrefetches) {
            remainingByKey.put(prefetch.reservationKey(), prefetch.remaining());
            facets.add(prefetch.facet());
        }
        for (RecipeEnergyPrefetchFacet facet : facets) {
            facet.onRecipeReservationChanged(this, remainingByKey.getOrDefault(facet.reservationKey(), 0L), this::invalidate);
            long nextBatch = activePrefetches.stream()
                    .filter(prefetch -> prefetch.reservationKey().equals(facet.reservationKey()))
                    .mapToLong(ActivePrefetch::batchAmount).findFirst().orElse(0L);
            facet.onRecipeContinuationRequested(this, nextBatch);
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
            allocations.add(new StoredPrefetch(reservationKey, amount, batchAmount));
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
        if (allocationTotal != remaining) return false;
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
        if (batchTotal > total || !legacy && batchTotal != total) return false;
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
                        allocation.amount(), batchAmount);
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
        if (prefetchedEnergyConsumedTick != activeRecipe.getTick()) {
            prefetchedEnergyConsumedTick = activeRecipe.getTick();
            prefetchedEnergyTickConsumed = 0L;
        }
        long remaining = Math.min(prefetchedEnergyPerTick - prefetchedEnergyTickConsumed, prefetchedEnergyRemaining);
        if (remaining <= 0L) return null;
        List<ActivePrefetch> updated = new ArrayList<>(activePrefetches);
        for (int index = 0; index < updated.size() && remaining > 0L; index++) {
            ActivePrefetch prefetch = updated.get(index);
            long consumed = Math.min(remaining, prefetch.remaining());
            if (consumed <= 0L) continue;
            CapabilityResult result;
            RecipeCommitLedger payment = ledger;
            RecipeCommitLedger.State admission = payment.state();
            ActiveMachineRecipe consumingRecipe = activeRecipe;
            LongValueStorage observed = prefetch.facet() instanceof MachineCapability capability
                    ? CapabilityFactories.valueStorage(capability, LongValueStorage.class) : null;
            long beforeAmount = observed == null ? 0L : observed.amount();
            payment.beginNativeExecution();
            try {
                result = prefetch.facet().consumeReservation(consumed);
            } catch (RuntimeException exception) {
                payment.pauseAfterCommitFailure();
                logTickFailure("prefetch_consume", controller.currentRuntimeSnapshot(), consumingRecipe.getRecipe(), exception);
                return failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of());
            }
            if (activeRecipe != consumingRecipe || ledger != payment) return missingInputStatus();
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
                        prefetchedEnergyRemaining -= moved;
                        prefetchedEnergyTickConsumed += moved;
                    }
                    payment.completePrefetchDebit(admission);
                    if (moved > 0L) prefetch.facet().onRecipeReservationChanged(this,
                            prefetch.remaining() - moved, this::invalidate);
                }
                return result == null || result.status() == null
                        ? failure(BuiltinFailureReasons.MISSING_INPUT, FailurePhase.PER_TICK, Map.of()) : result.status();
            }
            updated.set(index, new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    prefetch.remaining() - consumed, prefetch.batchAmount()));
            // Publish each successful debit before visiting the next allocation, which may fail.
            activePrefetches = List.copyOf(updated);
            prefetchedEnergyRemaining -= consumed;
            prefetchedEnergyTickConsumed += consumed;
            payment.completePrefetchDebit(admission);
            prefetch.facet().onRecipeReservationChanged(this, prefetch.remaining() - consumed, this::invalidate);
            remaining -= consumed;
        }
        if (remaining > 0L) return missingInputStatus();
        return null;
    }

    private void releaseActivePrefetches() {
        releaseActivePrefetches(false);
    }

    private void releaseActivePrefetches(boolean keepContinuation) {
        if (!keepContinuation) stopEnergyPrefetch();
        List<ActivePrefetch> prefetches = activePrefetches;
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        prefetchedEnergyTickConsumed = 0L;
        prefetchedEnergyConsumedTick = -1;
        for (ActivePrefetch prefetch : prefetches) {
            try {
                if (prefetch.remaining() > 0L) prefetch.facet().releaseReservation(prefetch.remaining());
                prefetch.facet().onRecipeReservationChanged(this, 0L, this::invalidate);
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
        try {
            if (!plan.commitRuntime(payment::beginNativeExecution, () -> attemptCurrent(attempt))) {
                if (payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN) payment.pauseAfterCommitFailure();
                if (ledger == payment) waiting(plan.failure(), false);
                return false;
            }
            if (!attemptCurrent(attempt)) return false;
            if (!commitStressOutputs(controller.executionRuntimeSnapshot())) {
                payment.pauseAfterCommitFailure();
                return false;
            }
            payment.markResourcePaid(recipe.getTick(), recipe.needsFinishCommit());
            return attemptCurrent(attempt);
        } catch (RuntimeException exception) {
            pausePaymentFailure(payment, -1L, exception);
            return false;
        }
    }

    private boolean commitStressOutputs(ControllerRuntimeSnapshot runtime) {
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
            PlanningResult result = context(runtime).planFixedRequirements(outputs, activeRecipe.getParallelism(),
                    Map.of(), indexes);
            CraftingPlan plan = result.plan();
            if (!result.successful() || plan == null || plan.parallelism() < activeRecipe.getParallelism()) {
                waiting(result.failure(), false);
                return false;
            }
            if (!plan.commitRuntime(ledger::beginNativeExecution, () -> attemptCurrent(tickAttempt))) {
                ledger.pauseAfterCommitFailure();
                waiting(plan.failure(), false);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            logTickFailure("stress_output_commit", runtime, activeRecipe.getRecipe(), exception);
            waiting(failure(BuiltinFailureReasons.PER_TICK, FailurePhase.PER_TICK, Map.of()), false);
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
        if (planned == null || !planned.mainThreadRequirements().isEmpty()) {
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
        List<AsyncPlanningFacet> facets = components.capabilities().stream()
                .map(capability -> capability.facet(AsyncPlanningFacet.class).orElse(null)).toList();
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
                payment.pauseAfterCommitFailure();
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

    private void captureVersions(ControllerRuntimeSnapshot runtime) {
        structureVersion = runtime.structure().version();
        capabilityVersion = runtime.capabilityVersion();
        modifierVersion = runtime.modifierVersion();
        componentStateVersion = runtime.stateVersion();
        upgradeContentRevision = runtime.upgradeContentRevision();
        resourceDomain = controller.resourceDomain();
    }

    private void captureInputState(List<MachineRequirement> requirements, CraftingPlan plan) {
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(ItemRequirement.TYPE.equals(requirement.type())
                    || FluidRequirement.TYPE.equals(requirement.type())
                    || LoadedChemicalRequirement.TYPE.equals(requirement.type())
                    || SourceRequirement.TYPE.equals(requirement.type())
                    || ManaRequirement.TYPE.equals(requirement.type()))
                    || requirement.io() != RecipeModifier.IOType.INPUT) {
                continue;
            }
            boolean executed = ledger.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED
                    ? plan.wasNativeExecuted(index) : plan.hasOperations(index);
            if (executed) consumed.add(index);
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

    private record StoredPrefetch(String reservationKey, long amount, long batchAmount) {
    }

    private record ActivePrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet, long remaining, long batchAmount) {
    }

    private static String failureUnloc(ExecutionStatus status) {
        if (status == null) return "";
        FailureReason reason = status.reason();
        return (reason == null ? BuiltinFailureReasons.UNKNOWN : reason).translationKey();
    }
}
