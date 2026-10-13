package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureReport;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.sync.FailureStatusMigration;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Factory recipe thread with optional core-thread recipe filtering.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeThread extends RecipeThread {
    private final boolean coreThread;
    private final boolean baseThread;
    private final String threadName;
    private final String laneId;
    private final Set<MachineRecipe> recipeSet = new LinkedHashSet<>();
    private int idleTicks;
    private long lastIdleGameTime = Long.MIN_VALUE;
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeCatalogVersion = Long.MIN_VALUE;
    private boolean restartPending;
    private Predicate<MachineRecipe> startEligibility = recipe -> true;
    private Runnable finishContinuation = () -> { };
    private int failureStreak;
    private long nextSearchTick = Long.MIN_VALUE;
    private @Nullable RecipeSearchContextKey lastSearchFailureKey;
    private @Nullable ResourceLocation lastSearchFailureReason;
    private @Nullable ResourceLocation searchFailureRecipePoolId;
    private @Nullable RecipeSearchContextKey currentSearchContextKey;
    private boolean resourceWakePending;
    private volatile long recipeSetVersion;
    private long currentSearchGameTime;
    private boolean searchGameTimeSet;
    private Map<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> failureResourceMatchers = Map.of();
    private List<MachineRecipe> failureCandidates = List.of();
    private List<MachineRecipe> filteredCandidateSource = List.of();
    private long filteredCandidateCatalogVersion = Long.MIN_VALUE;
    private long filteredCandidateRecipeSetVersion = Long.MIN_VALUE;
    private List<MachineRecipe> filteredCandidates = List.of();
    private final long suppliedCandidateCatalogVersion;
    private final @Nullable ResourceLocation suppliedCandidatePool;
    private boolean suppliedCandidateCatalogCurrent = true;
    private long sourceCatalogVersion = Long.MIN_VALUE;
    private Map<ResourceLocation, MachineRecipe> catalogSources = Map.of();
    private final Map<MachineRecipe, MachineRecipe> resolvedCandidateSources = new IdentityHashMap<>();

    private FactoryRecipeThread(MachineControllerBlockEntity controller,
                                  boolean coreThread, boolean baseThread, String threadName) {
        this(controller, coreThread, baseThread, threadName, null);
    }

    private FactoryRecipeThread(MachineControllerBlockEntity controller,
                                  boolean coreThread, boolean baseThread, String threadName,
                                  @Nullable String explicitLaneId) {
        super(controller);
        this.coreThread = coreThread;
        this.baseThread = baseThread;
        this.threadName = threadName == null ? "" : threadName;
        this.laneId = explicitLaneId != null && !explicitLaneId.isBlank() ? explicitLaneId
                : baseThread ? "base" : coreThread ? "core-" + this.threadName
                : this.threadName.startsWith("factory-") ? this.threadName : "factory";
        this.runtime.setScreenText(controller.recipeScreenText(this.laneId));
        suppliedCandidatePool = currentRecipePoolId();
        suppliedCandidateCatalogVersion = currentRecipeCatalog().version();
        bindInstalledLane();
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller) {
        return simple(controller, "factory");
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, String laneId) {
        return new FactoryRecipeThread(controller, false, false, laneId, laneId);
    }

    public static FactoryRecipeThread base(MachineControllerBlockEntity controller) {
        return new FactoryRecipeThread(controller, false, true, "", "base");
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller,
                                           String threadName, Set<MachineRecipe> recipes) {
        return core(controller, threadName, recipes, null);
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller,
                                           String threadName, Set<MachineRecipe> recipes,
                                           @Nullable String laneId) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, true, false, threadName, laneId);
        thread.recipeSet.addAll(recipes == null ? Set.of() : recipes);
        return thread;
    }

    public Set<MachineRecipe> recipeSet() { return Set.copyOf(recipeSet); }
    public List<MachineRecipe> candidatesFor(List<MachineRecipe> candidates) {
        return candidatesFor(candidates, Long.MIN_VALUE);
    }

    public List<MachineRecipe> candidatesFor(List<MachineRecipe> candidates, long catalogVersion) {
        List<MachineRecipe> machineCandidates = candidatesForMachine(controller, candidates).stream()
                .map(this::resolveSearchCandidate).filter(Objects::nonNull).distinct().toList();
        if (!coreThread || machineCandidates.isEmpty()) return machineCandidates;
        if (filteredCandidateSource.equals(machineCandidates)
                && filteredCandidateCatalogVersion == catalogVersion
                && filteredCandidateRecipeSetVersion == recipeSetVersion) {
            return filteredCandidates;
        }
        filteredCandidateSource = Collections.unmodifiableList(new ArrayList<>(machineCandidates));
        filteredCandidateCatalogVersion = catalogVersion;
        filteredCandidateRecipeSetVersion = recipeSetVersion;
        Set<MachineRecipe> allowed = recipeSet.stream().map(this::resolveSearchCandidate)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        filteredCandidates = machineCandidates.stream().filter(allowed::contains).toList();
        return filteredCandidates;
    }

    private @Nullable MachineRecipe resolveSearchCandidate(MachineRecipe source) {
        MachineRecipeCatalog catalog = currentRecipeCatalog();
        if (catalog.version() != suppliedCandidateCatalogVersion) suppliedCandidateCatalogCurrent = false;
        if (sourceCatalogVersion != catalog.version()) {
            Map<ResourceLocation, MachineRecipe> sources = new HashMap<>();
            for (MachineRecipe recipe : catalog.recipes()) sources.put(recipe.id(), recipe);
            catalogSources = Map.copyOf(sources);
            resolvedCandidateSources.clear();
            sourceCatalogVersion = catalog.version();
        }
        MachineRecipe current = catalogSources.get(source.id());
        if (current == source) return source;
        if (current != null) {
            if (!resolvedCandidateSources.containsKey(source)) {
                resolvedCandidateSources.put(source,
                        ActiveMachineRecipe.sameDefinition(source, current, registryAccess()) ? current : null);
            }
            return resolvedCandidateSources.get(source);
        }
        return suppliedCandidateCatalogCurrent && catalog.version() == suppliedCandidateCatalogVersion
                && Objects.equals(currentRecipePoolId(), suppliedCandidatePool) ? source : null;
    }

    public void replaceRecipeSet(Set<MachineRecipe> recipes) {
        if (!coreThread) return;
        Set<MachineRecipe> replacement = recipes == null ? Set.of() : Set.copyOf(recipes);
        if (recipeSet.equals(replacement)) return;
        recipeSet.clear();
        recipeSet.addAll(replacement);
        recipeSetVersion++;
        clearSearchFailure();
        requestFullSearch();
        if (lastRecipe != null && resolveCurrentLastRecipe(List.copyOf(replacement)) == null) {
            clearLastRecipe();
            restartPending = false;
        }
    }

    public boolean isCoreThread() { return coreThread; }
    public @Nullable ResourceLocation lastRecipeId() { return lastRecipe == null ? null : lastRecipe.id(); }
    public long searchGameTime() { return currentSearchGameTime; }
    public boolean isBaseThread() { return baseThread; }
    public String threadName() { return threadName; }
    @Override public String laneId() { return laneId; }
    public long coreRecipeSetVersion() { return recipeSetVersion; }

    public void setStartEligibility(Predicate<MachineRecipe> eligibility) {
        startEligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    @Override
    protected boolean isStartCandidateEligible(MachineRecipe recipe) {
        return super.isStartCandidateEligible(recipe)
                && candidatesFor(List.of(recipe)).contains(recipe)
                && AsyncRequirementPlanner.capturedRequirementFailure(controller.currentRuntimeSnapshot(), recipe) == null
                && startEligibility.test(recipe);
    }

    public boolean canSearch(long gameTime, RecipeSearchContextKey current) {
        return hasFullSearchPending() || searchFailureRecipePoolId == null || !searchFailureRecipePoolId.equals(currentRecipePoolId())
                || lastSearchFailureKey == null
                || !lastSearchFailureKey.equals(current)
                || gameTime >= nextSearchTick;
    }

    public boolean needsSearch(RecipeSearchContextKey currentKey, long gameTime) {
        return isIdle() && canSearch(gameTime, currentKey);
    }

    public void recordSearchFailure(RecipeSearchContextKey key, long gameTime) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        failureStreak = Math.min(Integer.MAX_VALUE, failureStreak + 1);
        lastSearchFailureKey = key;
        searchFailureRecipePoolId = currentRecipePoolId();
        nextSearchTick = gameTime + retryDelay(failureStreak);
        lastSearchFailureReason = failureReason();
        resourceWakePending = false;
    }

    public void wakeSearch() {
        nextSearchTick = Long.MIN_VALUE;
        resourceWakePending = true;
    }

    public boolean matchesAvailability(ResourceAvailabilityNotifier.Reason reason, @Nullable Object resource) {
        if (reason == null) return false;
        if (resource == null) {
            return reason == ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY
                    && (BuiltinFailureReasons.MISSING_OUTPUT.id().equals(lastSearchFailureReason)
                    || BuiltinFailureReasons.FINISH.id().equals(lastSearchFailureReason)
                    || !failureResourceMatchers.getOrDefault(reason, List.of()).isEmpty());
        }
        return failureResourceMatchers.getOrDefault(reason, List.of()).stream().anyMatch(matcher -> matcher.test(resource));
    }

    public void clearSearchFailure() {
        failureStreak = 0;
        lastSearchFailureKey = null;
        searchFailureRecipePoolId = null;
        lastSearchFailureReason = null;
        nextSearchTick = Long.MIN_VALUE;
        resourceWakePending = false;
        failureResourceMatchers = Map.of();
        failureCandidates = List.of();
    }

    public @Nullable ResourceLocation searchFailureReason() {
        return lastSearchFailureReason;
    }

    public @Nullable RecipeSearchContextKey searchFailureKey() {
        return lastSearchFailureKey;
    }

    public long searchResourceEpoch(long currentEpoch) {
        return resourceWakePending || lastSearchFailureKey == null
                ? currentEpoch : lastSearchFailureKey.resourceAvailabilityEpoch();
    }

    public void setSearchContextKey(@Nullable RecipeSearchContextKey key) {
        currentSearchContextKey = key;
    }

    public void setSearchGameTime(long gameTime) {
        currentSearchGameTime = gameTime;
        searchGameTimeSet = true;
    }

    private void clearSearchGameTime() { searchGameTimeSet = false; }

    private static int retryDelay(int failureStreak) {
        return Math.min(100, 5 << Math.min(5, Math.max(0, failureStreak - 1)));
    }

    public boolean isTimedOut() {
        return !baseThread && !coreThread && isIdle()
                && idleTicks >= ServerConfig.factoryIdleTimeoutTicks();
    }

    public boolean idleTimeoutDue(long gameTime) {
        if (baseThread || coreThread || !isIdle()) return false;
        if (lastIdleGameTime == Long.MIN_VALUE) lastIdleGameTime = gameTime;
        return effectiveIdleTicks(gameTime) >= ServerConfig.factoryIdleTimeoutTicks();
    }

    public void tickIdle(long gameTime) {
        if (!isIdle()) {
            idleTicks = 0;
            lastIdleGameTime = Long.MIN_VALUE;
            return;
        }
        if (lastIdleGameTime == Long.MIN_VALUE) {
            idleTicks = Math.min(Integer.MAX_VALUE, idleTicks + 1);
        } else if (gameTime > lastIdleGameTime) {
            idleTicks = (int) Math.min(Integer.MAX_VALUE, idleTicks + gameTime - lastIdleGameTime);
        }
        lastIdleGameTime = gameTime;
    }

    private int effectiveIdleTicks(long gameTime) {
        if (lastIdleGameTime == Long.MIN_VALUE || gameTime <= lastIdleGameTime) return idleTicks;
        return (int) Math.min(Integer.MAX_VALUE, idleTicks + gameTime - lastIdleGameTime);
    }

    @Override protected void onStarted() {
        restartPending = false;
        clearFullSearchRequest();
        idleTicks = 0;
        lastIdleGameTime = Long.MIN_VALUE;
        clearSearchFailure();
        currentSearchContextKey = null;
        searchGameTimeSet = false;
        failureResourceMatchers = Map.of();
        failureCandidates = List.of();
        MachineRecipe recipe = runtime.recipe();
        if (recipe != null) {
            MachineRecipeCatalog catalog = currentRecipeCatalog();
            if (catalog.version() != suppliedCandidateCatalogVersion) suppliedCandidateCatalogCurrent = false;
            lastRecipe = recipe;
            lastRecipeCatalogVersion = catalog.version();
        }
    }
    @Override
    protected void onFinished() {
        idleTicks = 0;
        lastIdleGameTime = Long.MIN_VALUE;
        // The continuation may already own the next execution; validate at its next opportunity.
    }

    @Override
    protected void onPendingStartCatalogChanged() {
        clearSearchFailure();
    }

    @Override
    protected void onStartSearchFailed(@Nullable ExecutionStatus failure) {
        super.onStartSearchFailed(failure);
        armSearchFailure();
        updateFailureResourceMatchers(failureCandidates);
    }

    @Override
    protected void onRecipeSearchFailed(RecipeSearchResult result) {
        super.onRecipeSearchFailed(result);
        updateFailureResourceMatchers(failureCandidates, result.failureReport());
    }

    @Override
    protected void onStartAttemptResult(StartOrigin origin, StartResult result,
                                        @Nullable RecipeSearchContextKey key) {
        if (key != null && key.coreRecipeSetVersion() != recipeSetVersion) return;
        if (origin == StartOrigin.LAST_RECIPE) reportLastRecipeStartResult(result);
        else {
            onFullSearchResult(result);
            if (result == StartResult.FAILED) onStartFailed(key);
        }
    }

    @Override
    protected void onLastRecipeStartResult(StartResult result) {
        switch (result) {
            case STARTED -> restartPending = false;
            case PENDING, DEFERRED -> { }
            case NO_MATCH, FAILED -> {
                restartPending = false;
                clearSearchFailure();
                requestFullSearch();
            }
            case CANCELLED -> restartPending = false;
        }
    }

    public void setFinishContinuation(Runnable finishContinuation) {
        this.finishContinuation = finishContinuation == null ? () -> { } : finishContinuation;
    }

    public StartResult prepareAsyncFinishRestart(FactorySearchContext context, List<MachineRecipe> candidates,
                                              long availableParallelism, long structureVersion, long capabilityVersion,
                                              long modifierVersion, long componentStateVersion) {
        if (!restartPending || hasFullSearchPending()) return StartResult.NO_MATCH;
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        setSearchContextKey(contextSearchContextKey(context));
        setSearchGameTime(context.gameTime());
        MachineRecipe candidate = restartCandidate(candidates, availableParallelism, context.catalogVersion());
        if (candidate == null) {
            reportLastRecipeStartResult(StartResult.NO_MATCH);
            return StartResult.NO_MATCH;
        }
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return enqueueAsyncFinishRestart(candidate, availableParallelism, current.structure().version(), context,
                StartOrigin.LAST_RECIPE);
    }

    @Override public void onRecipeFinished() {
        restartPending = true;
        finishContinuation.run();
    }

    @Override
    public StartResult searchAndStartRecipe(List<MachineRecipe> candidates, long availableParallelism, long structureVersion) {
        FactorySearchContext context = new FactorySearchContext(controller.currentRuntimeSnapshot(), List.of(),
                controller.componentRuntime().capabilities(), controller.componentRuntime().modifierList(),
                currentRecipeCatalog().version(), controller.resourceAvailabilityEpoch(), availableParallelism,
                controller.getLevel() == null ? 0L : controller.getLevel().getGameTime());
        return searchAndStartRecipe(context, candidates, structureVersion);
    }

    public StartResult searchAndStartRecipe(FactorySearchContext context, long structureVersion) {
        return searchAndStartRecipe(context, context == null ? null : context.orderedCandidates(),
                structureVersion);
    }

    public StartResult searchAndStartRecipe(FactorySearchContext context, List<MachineRecipe> candidates,
                                         long structureVersion) {
        if (context == null) return StartResult.NO_MATCH;
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        if (context.maxParallelism() <= 0L) return StartResult.NO_MATCH;
        setSearchContextKey(contextSearchContextKey(context));
        setSearchGameTime(context.gameTime());
        List<MachineRecipe> filtered = candidatesFor(candidates, context.catalogVersion());
        failureCandidates = filtered.stream().filter(Objects::nonNull).toList();
        long epoch = controller.lifecycleEpoch();
        long coreVersion = recipeSetVersion;
        SearchResult result = search(context, filtered, structureVersion, runtime);
        return epoch == controller.lifecycleEpoch() && coreVersion == recipeSetVersion
                ? startSearchResult(context, filtered, structureVersion, result)
                : StartResult.CANCELLED;
    }

    /** Computes an immutable factory-lane search result without accessing the lane runtime. */
    public static SearchResult search(FactorySearchContext context, List<MachineRecipe> candidates,
                                       long structureVersion) {
        return search(context, candidates, structureVersion, null);
    }

    public static SearchResult search(FactorySearchContext context, List<MachineRecipe> candidates,
                                      long structureVersion, @Nullable Object prefetchOwner) {
        if (context == null) return new SearchResult(null, null, false);
        Machine machine = context.snapshot().structure().machine() == null
                ? context.snapshot().structure().configuredMachine() : context.snapshot().structure().machine();
        ResourceLocation machineId = machine == null ? null : machine.registryName();
        if (machineId == null || context.maxParallelism() <= 0) return new SearchResult(null, null, false);
        ResourceLocation recipePoolId = candidates == null ? null : candidates.stream()
                .filter(Objects::nonNull).map(MachineRecipe::recipePoolId).findFirst().orElse(null);
        try {
            List<MachineRecipe> eligible = new ArrayList<>();
            List<MachineRecipe> rejected = new ArrayList<>();
            List<RecipeSearchTask.PlanningValue> failures = new ArrayList<>();
            for (MachineRecipe candidate : candidates == null ? List.<MachineRecipe>of() : candidates) {
                int stage = Math.max(1, context.snapshot().structure().matchedStage());
                FailureReason failure = candidate.stageRequirements().stream()
                        .anyMatch(requirement -> stage < requirement.minStage())
                        ? BuiltinFailureReasons.STAGE_INSUFFICIENT : null;
                if (failure == null) eligible.add(candidate);
                else {
                    rejected.add(candidate);
                    failures.add(RecipeSearchTask.PlanningValue.failure(candidate.id(), failure, 0, false));
                }
            }
            RecipeSearchResult result = new RecipeSearchTask(context.snapshot(), machineId, recipePoolId, structureVersion,
                    context.maxParallelism(), eligible, context.capabilities(),
                    MachineModifier.recipeModifiers(context.modifiers())).compute(prefetchOwner);
            if (!result.success() && !rejected.isEmpty()) {
                FailureReport report = result.failureReport();
                RecipeSearchResult staticResult = RecipeSearchTask.forPlanningValues(context.snapshot(), machineId,
                        structureVersion, context.maxParallelism(), rejected, failures).compute();
                for (FailureReport.Candidate failure : staticResult.failureReport().candidates()) {
                    report = report.plus(failure.status(), failure.validity());
                }
                ExecutionStatus primary = report.primary();
                float validity = 0F;
                for (FailureReport.Candidate failure : report.candidates()) {
                    if (failure.status() == primary) validity = failure.validity();
                }
                result = RecipeSearchResult.failure(machineId, structureVersion, context.snapshot().capabilityVersion(),
                        context.snapshot().modifierVersion(), report, validity);
            }
            return new SearchResult(result, null, false, prefetchOwner != null);
        } catch (RuntimeException exception) {
            return new SearchResult(null, exception, false);
        }
    }

    /** Applies a worker search result on the main thread and begins the normal lane start lifecycle. */
    public StartResult startSearchResult(FactorySearchContext context, List<MachineRecipe> candidates,
                                     long structureVersion, SearchResult searchResult) {
        if (context == null || searchResult == null) return StartResult.NO_MATCH;
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        setSearchContextKey(contextSearchContextKey(context));
        setSearchGameTime(context.gameTime());
        List<MachineRecipe> filtered = candidatesFor(candidates, context.catalogVersion());
        failureCandidates = filtered;
        long epoch = controller.lifecycleEpoch();
        long coreVersion = recipeSetVersion;
        SearchResult resolved = searchResult.requiresMainThreadReplan()
                ? search(context, filtered, structureVersion, runtime) : searchResult;
        if (epoch != controller.lifecycleEpoch() || coreVersion != recipeSetVersion) return StartResult.CANCELLED;
        RecipeSearchResult result = resolved.result();
        if (resolved.failure() != null || result == null || !result.success()) {
            controller.clearPendingConflictStart();
            if (!resolved.livePrefetchSearch() && resolved.failure() == null && result != null) runtime.stopEnergyPrefetch();
            if (result == null) onStartSearchFailed(null);
            else onRecipeSearchFailed(result);
            StartResult failure = resolved.failure() != null || result == null ? StartResult.FAILED : StartResult.NO_MATCH;
            onFullSearchResult(failure);
            return failure;
        }
        if (!filtered.contains(result.recipe())) {
            onFullSearchResult(StartResult.NO_MATCH);
            return StartResult.NO_MATCH;
        }
        if (controller.shouldDelayConflictProneStart(result)) return StartResult.DEFERRED;
        StartResult started = startRecipe(result.recipe(), context.maxParallelism(), structureVersion, context);
        if (epoch == controller.lifecycleEpoch() && coreVersion == recipeSetVersion) onFullSearchResult(started);
        return started;
    }

    /** Immutable outcome of a worker-side factory recipe search. */
    public record SearchResult(@Nullable RecipeSearchResult result, @Nullable RuntimeException failure,
                               boolean requiresMainThreadReplan, boolean livePrefetchSearch) {
        public SearchResult(@Nullable RecipeSearchResult result, @Nullable RuntimeException failure,
                            boolean requiresMainThreadReplan) {
            this(result, failure, requiresMainThreadReplan, false);
        }
    }

    public StartResult tryRestartLastRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                        long structureVersion, long capabilityVersion,
                                        long modifierVersion, long componentStateVersion) {
        setSearchContextKey(null);
        clearSearchGameTime();
        return tryRestartLastRecipe(candidates, availableParallelism, structureVersion, capabilityVersion,
                modifierVersion, componentStateVersion, Long.MIN_VALUE, null);
    }

    public StartResult tryRestartLastRecipe(FactorySearchContext context, List<MachineRecipe> candidates,
                                         long availableParallelism, long structureVersion, long capabilityVersion,
                                        long modifierVersion, long componentStateVersion) {
        if (context != null) {
            setSearchContextKey(contextSearchContextKey(context));
            setSearchGameTime(context.gameTime());
        } else {
            setSearchContextKey(null);
            clearSearchGameTime();
        }
        return tryRestartLastRecipe(candidates, availableParallelism, structureVersion, capabilityVersion,
                modifierVersion, componentStateVersion,
                context == null ? Long.MIN_VALUE : context.catalogVersion(), context);
    }

    private StartResult tryRestartLastRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                          long structureVersion, long capabilityVersion,
                                          long modifierVersion, long componentStateVersion,
                                          long catalogVersion, @Nullable FactorySearchContext context) {
        if (!restartPending || hasFullSearchPending()) return StartResult.NO_MATCH;
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        MachineRecipe candidate = restartCandidate(candidates, availableParallelism, catalogVersion);
        if (candidate == null) {
            reportLastRecipeStartResult(StartResult.NO_MATCH);
            return StartResult.NO_MATCH;
        }
        return startRecipe(candidate, availableParallelism, controller.currentRuntimeSnapshot().structure().version(),
                context, StartOrigin.LAST_RECIPE);
    }

    private @Nullable MachineRecipe restartCandidate(List<MachineRecipe> candidates, long availableParallelism,
                                                     long catalogVersion) {
        MachineRecipe candidate = resolveCurrentLastRecipe(candidatesFor(candidates, catalogVersion));
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return candidate != null && availableParallelism > 0L
                && current.structure().formed() && current.structure().structureAreaLoaded()
                && current.moduleConnectionStatus().canRunRecipe(candidate.requiredHostIds())
                && AsyncRequirementPlanner.capturedRequirementFailure(current, candidate) == null ? candidate : null;
    }

    private @Nullable MachineRecipe resolveCurrentLastRecipe(List<MachineRecipe> candidates) {
        if (lastRecipe == null) return null;
        if (!recipeBelongsToCurrentMachine(lastRecipe)) {
            clearLastRecipe();
            return null;
        }
        MachineRecipeCatalog catalog = currentRecipeCatalog();
        if (lastRecipeCatalogVersion != catalog.version()) {
            MachineRecipe current = catalog.recipes().stream()
                    .filter(recipe -> lastRecipe.id().equals(recipe.id())).findFirst().orElse(null);
            if (current == null || !ActiveMachineRecipe.sameDefinition(lastRecipe, current, registryAccess())) {
                clearLastRecipe();
                return null;
            }
            lastRecipe = current;
            lastRecipeCatalogVersion = catalog.version();
        }
        return candidates.contains(lastRecipe) ? lastRecipe : null;
    }

    @Override
    protected void onStartFailed() {
        onStartFailed(null);
    }

    @Override
    protected void onStartFailed(@Nullable RecipeSearchContextKey contextKey) {
        List<MachineRecipe> candidates = failureCandidates.isEmpty() && lastRecipe != null
                ? List.of(lastRecipe) : failureCandidates;
        armSearchFailure(contextKey);
        updateFailureResourceMatchers(candidates);
    }

    private void clearLastRecipe() {
        lastRecipe = null;
        lastRecipeCatalogVersion = Long.MIN_VALUE;
    }

    @Override
    protected void onRecipeFailure() {
        restartPending = false;
        List<MachineRecipe> candidates = lastRecipe == null ? failureCandidates : List.of(lastRecipe);
        armSearchFailureFromLiveContext();
        updateFailureResourceMatchers(candidates);
    }

    private void armSearchFailure() {
        armSearchFailure(null);
    }

    private void armSearchFailure(@Nullable RecipeSearchContextKey preferredKey) {
        RecipeSearchContextKey key = preferredKey != null ? preferredKey
                : currentSearchContextKey != null ? currentSearchContextKey : currentSearchContextKey();
        long gameTime = searchGameTimeSet ? currentSearchGameTime
                : controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
        recordSearchFailure(key, gameTime);
    }

    private void armSearchFailureFromLiveContext() {
        RecipeSearchContextKey key = currentSearchContextKey();
        long gameTime = searchGameTimeSet ? currentSearchGameTime
                : controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
        recordSearchFailure(key, gameTime);
    }

    private @Nullable ResourceLocation failureReason() {
        return runtime.failure() == null || runtime.failure().reason() == null
                ? null : runtime.failure().reason().id();
    }

    private void updateFailureResourceMatchers(List<MachineRecipe> candidates) {
        updateFailureResourceMatchers(candidates, null);
    }

    private void updateFailureResourceMatchers(List<MachineRecipe> candidates, @Nullable FailureReport report) {
        EnumMap<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers =
                new EnumMap<>(ResourceAvailabilityNotifier.Reason.class);
        if (report == null) {
            if (lastSearchFailureReason != null) {
                for (MachineRecipe recipe : candidates) addRecipeMatchers(matchers, recipe, lastSearchFailureReason);
            }
        } else {
            for (FailureReport.Candidate failure : report.candidates()) {
                ExecutionStatus status = failure.status();
                if (status.reason() == null || status.failure() == null) continue;
                Set<ResourceLocation> ids = status.failure().trace().frames().stream()
                        .map(frame -> frame.recipeId()).filter(Objects::nonNull).collect(Collectors.toSet());
                for (MachineRecipe recipe : candidates) {
                    if (ids.contains(recipe.id())) addRecipeMatchers(matchers, recipe, status.reason().id());
                }
            }
        }
        failureResourceMatchers = matchers.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }

    private static void addRecipeMatchers(EnumMap<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers,
                                          MachineRecipe recipe, ResourceLocation reason) {
        if (BuiltinFailureReasons.MODULE_CONNECTION.id().equals(reason)) {
            addMatcher(matchers, ResourceAvailabilityNotifier.Reason.MODULE_CONNECTION,
                    resource -> resource instanceof ModuleConnectionStatus);
        }
        for (MachineRequirement requirement : recipe.runtimeRequirements()) {
            addRequirementMatchers(matchers, requirement, reason);
        }
    }

    static void addRequirementMatchers(EnumMap<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers,
                                       MachineRequirement requirement, ResourceLocation failureReason) {
        for (RequirementHandler.ResourceWakeup wakeup : RequirementHandlerRegistry.resourceWakeupsFor(requirement)) {
            if (!wakeup.failureReasonIds().contains(failureReason)) continue;
            ResourceAvailabilityNotifier.Reason reason = ResourceAvailabilityNotifier.Reason.valueOf(
                    wakeup.reason().name());
            addMatcher(matchers, reason, wakeup.matcher());
        }
    }

    private static void addMatcher(Map<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers,
                                   ResourceAvailabilityNotifier.Reason reason, Predicate<Object> matcher) {
        matchers.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(matcher);
    }

    private RecipeSearchContextKey currentSearchContextKey() {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        MachineRecipeCatalog catalog = currentRecipeCatalog();
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(), catalog.version(),
                controller.resourceAvailabilityEpoch(), recipeSetVersion);
    }

    private RecipeSearchContextKey contextSearchContextKey(FactorySearchContext context) {
        return new RecipeSearchContextKey(context.snapshot().structure().version(),
                context.snapshot().capabilityVersion(), context.snapshot().modifierVersion(),
                context.snapshot().stateVersion(), context.catalogVersion(),
                searchResourceEpoch(context.resourceAvailabilityEpoch()), recipeSetVersion);
    }

    @Override
    protected @Nullable RecipeSearchContextKey searchContextKeyForStart() {
        return currentSearchContextKey;
    }

    public void setActiveRecipeForTesting(@Nullable ActiveMachineRecipe activeRecipe) {
        if (activeRecipe == null) runtime.invalidate();
        else {
            ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
            runtime.restore(activeRecipe, controller.resourceDomain(), snapshot.structure().version(),
                    snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion());
        }
    }

    public void rebindCurrentVersions() {
        if (lastRecipe != null && !recipeBelongsToCurrentMachine(lastRecipe)) {
            clearLastRecipe();
            restartPending = false;
            clearFullSearchRequest();
            clearSearchFailure();
        }
        if (searchFailureRecipePoolId != null && !searchFailureRecipePoolId.equals(currentRecipePoolId())) {
            clearSearchFailure();
        }
        runtime.rebindCurrentVersions();
    }

    @Override
    protected void onDiscardedForRecipePoolChange() {
        clearLastRecipe();
        restartPending = false;
        clearSearchFailure();
        clearFullSearchRequest();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        onDiscardedForRecipePoolChange();
    }

    public void save(CompoundTag output, HolderLookup.Provider registries) {
        output.putBoolean("core", coreThread);
        output.putBoolean("base", baseThread);
        output.putString("name", threadName);
        output.putString("lane_id", laneId);
        long gameTime = controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
        output.putInt("idle_ticks", effectiveIdleTicks(gameTime));
        output.putBoolean("has_last", lastRecipe != null);
        if (lastRecipe != null) {
            output.putString("last_recipe", lastRecipe.id().toString());
        }
        output.putInt("search_failure_streak", failureStreak);
        long remaining = lastSearchFailureKey == null || nextSearchTick == Long.MIN_VALUE
                ? 0L : Math.max(0L, nextSearchTick - gameTime);
        output.putInt("search_retry_remaining", (int) Math.min(100L, remaining));
        output.putString("search_failure_reason_id",
                lastSearchFailureReason == null ? "" : lastSearchFailureReason.toString());
        output.putString("search_failure_reason_id",
                lastSearchFailureReason == null ? "" : lastSearchFailureReason.toString());
        if (searchFailureRecipePoolId != null) {
            output.putString("search_failure_pool", searchFailureRecipePoolId.toString());
        }
        if (lastSearchFailureKey != null) {
            output.putBoolean("has_search_failure_key", true);
            CompoundTag key = new CompoundTag();
            output.put("search_failure_key", key);
            key.putLong("structure_version", lastSearchFailureKey.structureVersion());
            key.putLong("capability_version", lastSearchFailureKey.capabilityVersion());
            key.putLong("modifier_version", lastSearchFailureKey.modifierVersion());
            key.putLong("component_state_version", lastSearchFailureKey.componentStateVersion());
            key.putLong("catalog_version", lastSearchFailureKey.catalogVersion());
            key.putLong("resource_availability_epoch", lastSearchFailureKey.resourceAvailabilityEpoch());
            key.putLong("core_recipe_set_version", lastSearchFailureKey.coreRecipeSetVersion());
        }
        CompoundTag runtimeOutput = new CompoundTag();
        runtime.save(runtimeOutput, registries);
        output.put("runtime", runtimeOutput);
    }

    public static FactoryRecipeThread load(CompoundTag input, MachineControllerBlockEntity controller,
                                           HolderLookup.Provider registries) {
        return load(input, controller, registries, null);
    }

    public static FactoryRecipeThread load(CompoundTag input, MachineControllerBlockEntity controller,
                                           HolderLookup.Provider registries, @Nullable List<MachineRecipe> candidates) {
        return load(input, controller, registries, candidates, null);
    }

    public static FactoryRecipeThread load(CompoundTag input, MachineControllerBlockEntity controller,
                                            HolderLookup.Provider registries, @Nullable List<MachineRecipe> candidates,
                                            @Nullable String fallbackLaneId) {
        String persistedLaneId = input.getString("lane_id");
        String laneId = persistedLaneId.isBlank() ? fallbackLaneId : persistedLaneId;
        FactoryRecipeThread thread = new FactoryRecipeThread(controller,
                input.getBoolean("core"), input.getBoolean("base"), input.getString("name"), laneId);
        List<MachineRecipe> availableCandidates = candidatesForMachine(controller, candidates);
        RecipeSearchContextKey restoredKey = readSearchFailureKey(input);
        if (thread.coreThread) thread.recipeSet.addAll(availableCandidates);
        thread.idleTicks = input.getInt("idle_ticks");
        if (input.getBoolean("has_last")) {
            String recipeName = input.getString("last_recipe");
            ResourceLocation recipeId = recipeName.isEmpty() ? null : ResourceLocation.parse(recipeName);
            thread.lastRecipe = recipeId == null ? null : availableCandidates.stream()
                    .filter(candidate -> candidate != null && recipeId.equals(candidate.id()))
                    .findFirst().orElse(null);
            if (thread.lastRecipe != null) {
                thread.lastRecipeCatalogVersion = thread.currentRecipeCatalog().version();
            } else thread.requestFullSearch();
        }
        thread.runtime.load(input.getCompound("runtime"), controller.resourceDomain(), registries);
        MachineRecipe activeRecipe = thread.runtime.recipe();
        if (activeRecipe != null) {
            MachineRecipe current = availableCandidates.stream()
                    .filter(candidate -> candidate != null && activeRecipe.id().equals(candidate.id()))
                    .findFirst().orElse(null);
            if (current == null || !ActiveMachineRecipe.sameDefinition(activeRecipe, current,
                    registries)) {
                thread.clearLastRecipe();
            }
        }
        int restoredStreak = Math.max(0, input.getInt("search_failure_streak"));
        int restoredRemaining = Math.max(0, Math.min(100, input.getInt("search_retry_remaining")));
        ResourceLocation restoredReason = readSearchFailureReason(input);
        String restoredPoolName = input.getString("search_failure_pool");
        ResourceLocation restoredPool = restoredPoolName.isEmpty()
                ? thread.currentRecipePoolId() : ResourceLocation.parse(restoredPoolName);
        if (restoredStreak > 0 && Objects.equals(restoredPool, thread.currentRecipePoolId())
                && thread.candidatesFor(availableCandidates).size() != 1) {
            thread.clearSearchFailure();
            thread.requestFullSearch();
        } else if (restoredStreak > 0 && restoredKey != null
                && restoredKey.equals(thread.currentSearchContextKey())
                && Objects.equals(restoredPool, thread.currentRecipePoolId()) && restoredReason != null) {
            thread.failureStreak = restoredStreak;
            thread.nextSearchTick = (controller.getLevel() == null ? 0L : controller.getLevel().getGameTime())
                    + restoredRemaining;
            thread.lastSearchFailureReason = restoredReason;
            thread.lastSearchFailureKey = restoredKey;
            thread.searchFailureRecipePoolId = restoredPool;
            thread.failureCandidates = thread.candidatesFor(availableCandidates);
            thread.updateFailureResourceMatchers(thread.failureCandidates);
        } else {
            thread.clearSearchFailure();
        }
        thread.restartPending = thread.lastRecipe != null && thread.isIdle()
                && thread.searchFailureKey() == null && !thread.hasFullSearchPending();
        return thread;
    }

    private static @Nullable ResourceLocation readSearchFailureReason(CompoundTag input) {
        if (input.contains("search_failure_reason_id")) {
            return FailureStatusMigration.factoryReasonId(input.getString("search_failure_reason_id"));
        }
        return FailureStatusMigration.factoryReasonId(input.getString("search_failure_reason"));
    }

    private static List<MachineRecipe> candidatesForMachine(MachineControllerBlockEntity controller,
                                                             @Nullable List<MachineRecipe> candidates) {
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId == null) return List.of();
        List<MachineRecipe> source = candidates == null
                ? RecipeRegistry.catalogForPool(recipePoolId).recipes() : candidates;
        return source.stream().filter(recipe -> recipe != null
                && recipePoolId.equals(recipe.recipePoolId())).toList();
    }

    private boolean recipeBelongsToCurrentMachine(MachineRecipe recipe) {
        ResourceLocation recipePoolId = currentRecipePoolId();
        return recipePoolId != null && recipePoolId.equals(recipe.recipePoolId());
    }

    private @Nullable ResourceLocation currentRecipePoolId() {
        return controller.currentRecipePoolId();
    }

    private MachineRecipeCatalog currentRecipeCatalog() {
        return RecipeRegistry.catalogForPool(currentRecipePoolId());
    }

    private @Nullable Machine currentMachine() {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        return snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
    }

    private @Nullable HolderLookup.Provider registryAccess() {
        return controller.getLevel() == null ? null : controller.getLevel().registryAccess();
    }

    private static @Nullable RecipeSearchContextKey readSearchFailureKey(CompoundTag input) {
        if (!input.getBoolean("has_search_failure_key")) return null;
        CompoundTag key = input.getCompound("search_failure_key");
        return new RecipeSearchContextKey(longOr(key, "structure_version", Long.MIN_VALUE),
                longOr(key, "capability_version", Long.MIN_VALUE),
                longOr(key, "modifier_version", Long.MIN_VALUE),
                longOr(key, "component_state_version", Long.MIN_VALUE),
                longOr(key, "catalog_version", Long.MIN_VALUE),
                longOr(key, "resource_availability_epoch", Long.MIN_VALUE),
                longOr(key, "core_recipe_set_version", Long.MIN_VALUE));
    }

    private static long longOr(CompoundTag input, String key, long fallback) {
        return input.contains(key) ? input.getLong(key) : fallback;
    }
}
