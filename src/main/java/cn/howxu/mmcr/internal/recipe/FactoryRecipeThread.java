package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
    public static final int IDLE_TIMEOUT_TICKS = 200;

    private final boolean coreThread;
    private final boolean baseThread;
    private final String threadName;
    private final String laneId;
    private final Set<MachineRecipe> recipeSet = new LinkedHashSet<>();
    private int idleTicks;
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeStructureVersion = Long.MIN_VALUE;
    private long lastRecipeCapabilityVersion = Long.MIN_VALUE;
    private long lastRecipeModifierVersion = Long.MIN_VALUE;
    private long lastRecipeComponentStateVersion = Long.MIN_VALUE;
    private long lastRecipeCatalogVersion = Long.MIN_VALUE;
    private Runnable finishContinuation = () -> { };
    private int failureStreak;
    private long nextSearchTick = Long.MIN_VALUE;
    private @Nullable RecipeSearchContextKey lastSearchFailureKey;
    private @Nullable String lastSearchFailureReason;
    private @Nullable RecipeSearchContextKey currentSearchContextKey;
    private boolean resourceWakePending;
    private long recipeSetVersion;
    private long currentSearchGameTime;
    private boolean searchGameTimeSet;
    private Map<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> failureResourceMatchers = Map.of();
    private List<MachineRecipe> failureCandidates = List.of();
    private List<MachineRecipe> filteredCandidateSource = List.of();
    private long filteredCandidateCatalogVersion = Long.MIN_VALUE;
    private long filteredCandidateRecipeSetVersion = Long.MIN_VALUE;
    private List<MachineRecipe> filteredCandidates = List.of();

    private FactoryRecipeThread(MachineControllerBlockEntity controller,
                                  boolean coreThread, boolean baseThread, String threadName) {
        super(controller);
        this.coreThread = coreThread;
        this.baseThread = baseThread;
        this.threadName = threadName == null ? "" : threadName;
        this.laneId = baseThread ? "base" : coreThread ? "core-" + this.threadName
                : this.threadName.startsWith("factory-") ? this.threadName : "factory";
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller) {
        return simple(controller, "factory");
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, String laneId) {
        return new FactoryRecipeThread(controller, false, false, laneId);
    }

    public static FactoryRecipeThread base(MachineControllerBlockEntity controller) {
        return new FactoryRecipeThread(controller, false, true, "");
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller,
                                           String threadName, Set<MachineRecipe> recipes) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, true, false, threadName);
        thread.recipeSet.addAll(recipes == null ? Set.of() : recipes);
        return thread;
    }

    public Set<MachineRecipe> recipeSet() { return Set.copyOf(recipeSet); }
    public List<MachineRecipe> candidatesFor(List<MachineRecipe> candidates) {
        return candidatesFor(candidates, Long.MIN_VALUE);
    }

    public List<MachineRecipe> candidatesFor(List<MachineRecipe> candidates, long catalogVersion) {
        if (!coreThread || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        if (filteredCandidateSource.equals(candidates)
                && filteredCandidateCatalogVersion == catalogVersion
                && filteredCandidateRecipeSetVersion == recipeSetVersion) {
            return filteredCandidates;
        }
        filteredCandidateSource = Collections.unmodifiableList(new ArrayList<>(candidates));
        filteredCandidateCatalogVersion = catalogVersion;
        filteredCandidateRecipeSetVersion = recipeSetVersion;
        filteredCandidates = candidates.stream().filter(recipeSet::contains).toList();
        return filteredCandidates;
    }

    public void replaceRecipeSet(Set<MachineRecipe> recipes) {
        if (!coreThread) return;
        Set<MachineRecipe> replacement = recipes == null ? Set.of() : Set.copyOf(recipes);
        if (recipeSet.equals(replacement)) return;
        recipeSet.clear();
        recipeSet.addAll(replacement);
        recipeSetVersion++;
    }

    public boolean isCoreThread() { return coreThread; }
    public boolean isBaseThread() { return baseThread; }
    public String threadName() { return threadName; }
    @Override public String laneId() { return laneId; }
    public long coreRecipeSetVersion() { return recipeSetVersion; }

    public boolean canSearch(long gameTime, RecipeSearchContextKey current) {
        return lastSearchFailureKey == null
                || !lastSearchFailureKey.equals(current)
                || gameTime >= nextSearchTick;
    }

    public void recordSearchFailure(RecipeSearchContextKey key, long gameTime) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        failureStreak = Math.min(Integer.MAX_VALUE, failureStreak + 1);
        lastSearchFailureKey = key;
        nextSearchTick = gameTime + retryDelay(failureStreak);
        lastSearchFailureReason = failureReason();
        resourceWakePending = false;
    }

    public void wakeSearch() {
        nextSearchTick = Long.MIN_VALUE;
        resourceWakePending = true;
    }

    public boolean matchesAvailability(ResourceAvailabilityNotifier.Reason reason, @Nullable Object resource) {
        if (reason == null || !matchesFailureReason(reason)) return false;
        if (resource == null) {
            return reason == ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY
                    && (lastSearchFailureReason.equals("no_output_capacity")
                    || lastSearchFailureReason.equals("finish")
                    || !failureResourceMatchers.getOrDefault(reason, List.of()).isEmpty());
        }
        return failureResourceMatchers.getOrDefault(reason, List.of()).stream().anyMatch(matcher -> matcher.test(resource));
    }

    public void clearSearchFailure() {
        failureStreak = 0;
        lastSearchFailureKey = null;
        lastSearchFailureReason = null;
        nextSearchTick = Long.MIN_VALUE;
        resourceWakePending = false;
        failureResourceMatchers = Map.of();
        failureCandidates = List.of();
    }

    public @Nullable String searchFailureReason() {
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

    public boolean isTimedOut(boolean recipeLockUsed) {
        return !baseThread && !coreThread && !recipeLockUsed && isIdle() && idleTicks >= IDLE_TIMEOUT_TICKS;
    }
    public void tickIdle() { idleTicks = isIdle() ? idleTicks + 1 : 0; }

    @Override protected void onStarted() {
        idleTicks = 0;
        clearSearchFailure();
        currentSearchContextKey = null;
        searchGameTimeSet = false;
        failureResourceMatchers = Map.of();
        failureCandidates = List.of();
        MachineRecipe recipe = runtime.recipe();
        if (recipe != null) {
            ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
            lastRecipe = recipe;
            lastRecipeStructureVersion = snapshot.structure().version();
            lastRecipeCapabilityVersion = snapshot.capabilityVersion();
            lastRecipeModifierVersion = snapshot.modifierVersion();
            lastRecipeComponentStateVersion = snapshot.stateVersion();
            lastRecipeCatalogVersion = RecipeRegistry.catalog(recipe.machineId()).version();
        }
    }
    @Override
    protected void onFinished() {
        idleTicks = 0;
        if (lastRecipe != null && lastRecipeCatalogVersion != Long.MIN_VALUE) {
            MachineRecipeCatalog catalog = RecipeRegistry.catalog(lastRecipe.machineId());
            MachineRecipe current = catalog.recipes().stream()
                    .filter(candidate -> lastRecipe.id().equals(candidate.id()))
                    .findFirst().orElse(null);
            if (lastRecipeCatalogVersion != catalog.version()
                    && (current == null || !ActiveMachineRecipe.sameDefinition(lastRecipe, current))) {
                clearLastRecipe();
            }
        }
    }

    @Override
    protected void onPendingStartCatalogChanged() {
        clearSearchFailure();
    }

    @Override
    protected void onStartSearchFailed(@Nullable cn.howxu.mmcr.api.capability.status.ExecutionStatus failure) {
        super.onStartSearchFailed(failure);
        armSearchFailure();
        updateFailureResourceMatchers(failureCandidates);
    }

    public void setFinishContinuation(Runnable finishContinuation) {
        this.finishContinuation = finishContinuation == null ? () -> { } : finishContinuation;
    }

    @Override protected void onRecipeFinished() {
        finishContinuation.run();
    }

    @Override
    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        return searchAndStartRecipe(candidates, availableParallelism, structureVersion, null);
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                        long structureVersion, @Nullable Identifier lockedRecipeId) {
        setSearchContextKey(null);
        clearSearchGameTime();
        List<MachineRecipe> filtered = candidatesFor(candidates);
        failureCandidates = filtered.stream().filter(Objects::nonNull).toList();
        boolean started = super.searchAndStartRecipe(filtered, availableParallelism, structureVersion, lockedRecipeId);
        if (!started && runtime.failure() == null) failureCandidates = List.of();
        return started;
    }

    public boolean searchAndStartRecipe(FactorySearchContext context, long structureVersion,
                                        @Nullable Identifier lockedRecipeId) {
        return searchAndStartRecipe(context, context == null ? null : context.orderedCandidates(),
                structureVersion, lockedRecipeId);
    }

    public boolean searchAndStartRecipe(FactorySearchContext context, List<MachineRecipe> candidates,
                                        long structureVersion, @Nullable Identifier lockedRecipeId) {
        if (context == null) return false;
        setSearchContextKey(contextSearchContextKey(context, lockedRecipeId));
        setSearchGameTime(context.gameTime());
        List<MachineRecipe> filtered = candidatesFor(candidates, context.catalogVersion());
        failureCandidates = filtered.stream().filter(Objects::nonNull).toList();
        boolean started = super.searchAndStartRecipe(context, filtered, structureVersion, lockedRecipeId);
        if (!started && runtime.failure() == null) failureCandidates = List.of();
        return started;
    }

    public boolean tryRestartLastRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                        long structureVersion, long capabilityVersion,
                                        long modifierVersion, long componentStateVersion,
                                        @Nullable Identifier lockedRecipeId) {
        setSearchContextKey(null);
        clearSearchGameTime();
        return tryRestartLastRecipe(candidates, availableParallelism, structureVersion, capabilityVersion,
                modifierVersion, componentStateVersion, lockedRecipeId, Long.MIN_VALUE, null);
    }

    public boolean tryRestartLastRecipe(FactorySearchContext context, List<MachineRecipe> candidates,
                                        int availableParallelism, long structureVersion, long capabilityVersion,
                                        long modifierVersion, long componentStateVersion,
                                        @Nullable Identifier lockedRecipeId) {
        if (context != null) {
            setSearchContextKey(contextSearchContextKey(context, lockedRecipeId));
            setSearchGameTime(context.gameTime());
        } else {
            setSearchContextKey(null);
            clearSearchGameTime();
        }
        return tryRestartLastRecipe(candidates, availableParallelism, structureVersion, capabilityVersion,
                modifierVersion, componentStateVersion, lockedRecipeId,
                context == null ? Long.MIN_VALUE : context.catalogVersion(), context);
    }

    private boolean tryRestartLastRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                          long structureVersion, long capabilityVersion,
                                          long modifierVersion, long componentStateVersion,
                                          @Nullable Identifier lockedRecipeId, long catalogVersion,
                                          @Nullable FactorySearchContext context) {
        if (lockedRecipeId != null || lastRecipe == null || availableParallelism <= 0
                || lastRecipeStructureVersion != structureVersion
                || lastRecipeCapabilityVersion != capabilityVersion
                || lastRecipeModifierVersion != modifierVersion
                || lastRecipeComponentStateVersion != componentStateVersion
                || !candidatesFor(candidates, catalogVersion).contains(lastRecipe)) return false;
        MachineRecipe retryRecipe = lastRecipe;
        failureCandidates = List.of(retryRecipe);
        return startRecipe(retryRecipe, availableParallelism, structureVersion, context);
    }

    @Override
    protected void onStartFailed() {
        onStartFailed(null);
    }

    @Override
    protected void onStartFailed(@Nullable RecipeSearchContextKey contextKey) {
        List<MachineRecipe> candidates = failureCandidates.isEmpty() && lastRecipe != null
                ? List.of(lastRecipe) : failureCandidates;
        clearLastRecipe();
        armSearchFailure(contextKey);
        updateFailureResourceMatchers(candidates);
    }

    private void clearLastRecipe() {
        lastRecipe = null;
        lastRecipeStructureVersion = Long.MIN_VALUE;
        lastRecipeCapabilityVersion = Long.MIN_VALUE;
        lastRecipeModifierVersion = Long.MIN_VALUE;
        lastRecipeComponentStateVersion = Long.MIN_VALUE;
        lastRecipeCatalogVersion = Long.MIN_VALUE;
    }

    @Override
    protected void onRecipeFailure() {
        List<MachineRecipe> candidates = lastRecipe == null ? failureCandidates : List.of(lastRecipe);
        armSearchFailureFromLiveContext();
        updateFailureResourceMatchers(candidates);
    }

    private void armSearchFailure() {
        armSearchFailure(null);
    }

    private void armSearchFailure(@Nullable RecipeSearchContextKey preferredKey) {
        Identifier lockedRecipeId = currentSearchContextKey == null
                ? controller.lockedRecipeId() : currentSearchContextKey.lockedRecipeId();
        RecipeSearchContextKey key = preferredKey != null ? preferredKey
                : currentSearchContextKey != null ? currentSearchContextKey : currentSearchContextKey(lockedRecipeId);
        long gameTime = searchGameTimeSet ? currentSearchGameTime
                : controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
        recordSearchFailure(key, gameTime);
    }

    private void armSearchFailureFromLiveContext() {
        Identifier lockedRecipeId = currentSearchContextKey == null
                ? controller.lockedRecipeId() : currentSearchContextKey.lockedRecipeId();
        RecipeSearchContextKey key = currentSearchContextKey(lockedRecipeId);
        long gameTime = searchGameTimeSet ? currentSearchGameTime
                : controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
        recordSearchFailure(key, gameTime);
    }

    private @Nullable String failureReason() {
        return runtime.failure() == null ? null
                : runtime.failure().details().get("reason");
    }

    private boolean matchesFailureReason(ResourceAvailabilityNotifier.Reason reason) {
        if (lastSearchFailureReason == null) return false;
        return switch (reason) {
            case INPUT_AVAILABLE -> lastSearchFailureReason.equals("insufficient_resource")
                    || lastSearchFailureReason.equals("per_tick");
            case ENERGY_AVAILABLE -> lastSearchFailureReason.equals("insufficient_energy");
            case OUTPUT_CAPACITY -> lastSearchFailureReason.equals("insufficient_resource")
                    || lastSearchFailureReason.equals("no_output_capacity")
                    || lastSearchFailureReason.equals("finish");
            case MODULE_CONNECTION -> lastSearchFailureReason.equals("module_connection");
        };
    }

    private void updateFailureResourceMatchers(List<MachineRecipe> candidates) {
        EnumMap<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers =
                new EnumMap<>(ResourceAvailabilityNotifier.Reason.class);
        if (lastSearchFailureReason == null) {
            failureResourceMatchers = Map.of();
            return;
        }
        if (lastSearchFailureReason.equals("module_connection")) {
            matchers.put(ResourceAvailabilityNotifier.Reason.MODULE_CONNECTION,
                    List.of(resource -> resource instanceof ModuleConnectionStatus));
        }
        for (MachineRecipe recipe : candidates) {
            for (MachineRequirement requirement : recipe.runtimeRequirements()) {
                if (requirement instanceof ItemRequirement item) {
                    if (item.io() == RecipeModifier.IOType.INPUT
                            && (lastSearchFailureReason.equals("insufficient_resource")
                            || lastSearchFailureReason.equals("per_tick"))) {
                        addMatcher(matchers, ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE, itemMatcher(item));
                    } else if (item.io() == RecipeModifier.IOType.OUTPUT
                            && (lastSearchFailureReason.equals("insufficient_resource")
                            || lastSearchFailureReason.equals("no_output_capacity")
                            || lastSearchFailureReason.equals("finish"))) {
                        Predicate<Object> matcher = outputItemMatcher(item);
                        if (matcher != null) addMatcher(matchers, ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, matcher);
                    }
                } else if (requirement instanceof FluidRequirement fluid) {
                    if (fluid.io() == RecipeModifier.IOType.INPUT
                            && (lastSearchFailureReason.equals("insufficient_resource")
                            || lastSearchFailureReason.equals("per_tick"))) {
                        Predicate<Object> matcher = fluidMatcher(fluid);
                        if (matcher != null) addMatcher(matchers, ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE, matcher);
                    } else if (fluid.io() == RecipeModifier.IOType.OUTPUT
                            && (lastSearchFailureReason.equals("insufficient_resource")
                            || lastSearchFailureReason.equals("no_output_capacity")
                            || lastSearchFailureReason.equals("finish"))) {
                        Predicate<Object> matcher = outputFluidMatcher(fluid);
                        if (matcher != null) addMatcher(matchers, ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, matcher);
                    }
                } else if (requirement instanceof EnergyRequirement energy
                         && energy.io() == RecipeModifier.IOType.INPUT
                         && lastSearchFailureReason.equals("insufficient_energy")) {
                    CapabilityType type = new CapabilityType(EnergyRequirement.TYPE.id());
                    addMatcher(matchers, ResourceAvailabilityNotifier.Reason.ENERGY_AVAILABLE, type::equals);
                } else if (requirement instanceof EnergyRequirement energy
                        && energy.io() == RecipeModifier.IOType.OUTPUT
                        && lastSearchFailureReason.equals("no_output_capacity")) {
                    CapabilityType type = new CapabilityType(EnergyRequirement.TYPE.id());
                    addMatcher(matchers, ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, type::equals);
                }
            }
        }
        failureResourceMatchers = matchers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }

    private static void addMatcher(Map<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers,
                                   ResourceAvailabilityNotifier.Reason reason, Predicate<Object> matcher) {
        matchers.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(matcher);
    }

    private static Predicate<Object> itemMatcher(ItemRequirement requirement) {
        return resource -> resource instanceof ItemResource item
                && requirement.item() != null
                && requirement.item().test(item.toStack(Math.min(item.getMaxStackSize(), Integer.MAX_VALUE)))
                && requirement.components().matches(item.toStack(Math.min(item.getMaxStackSize(), Integer.MAX_VALUE)));
    }

    private static @Nullable Predicate<Object> outputItemMatcher(ItemRequirement requirement) {
        ItemStack stack = requirement.stack(null);
        if (stack.isEmpty()) return null;
        ItemResource expected = ItemResource.of(stack);
        return expected::equals;
    }

    private static @Nullable Predicate<Object> fluidMatcher(FluidRequirement requirement) {
        if (requirement.fluid() == null) return null;
        return resource -> resource instanceof FluidResource fluid
                && requirement.fluid().test(fluid.toStack(1));
    }

    private static @Nullable Predicate<Object> outputFluidMatcher(FluidRequirement requirement) {
        if (requirement.stack().isEmpty()) return null;
        FluidResource expected = FluidResource.of(requirement.stack());
        return expected::equals;
    }

    private RecipeSearchContextKey currentSearchContextKey() {
        return currentSearchContextKey(controller.lockedRecipeId());
    }

    private RecipeSearchContextKey currentSearchContextKey(@Nullable Identifier lockedRecipeId) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        var machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        MachineRecipeCatalog catalog = RecipeRegistry.catalog(machine == null ? null : machine.registryName());
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(), catalog.version(),
                controller.resourceAvailabilityEpoch(), lockedRecipeId, recipeSetVersion);
    }

    private RecipeSearchContextKey contextSearchContextKey(FactorySearchContext context,
                                                           @Nullable Identifier lockedRecipeId) {
        return new RecipeSearchContextKey(context.snapshot().structure().version(),
                context.snapshot().capabilityVersion(), context.snapshot().modifierVersion(),
                context.snapshot().stateVersion(), context.catalogVersion(),
                searchResourceEpoch(context.resourceAvailabilityEpoch()), lockedRecipeId, recipeSetVersion);
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
        runtime.rebindCurrentVersions();
        if (lastRecipe == null) return;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        lastRecipeStructureVersion = snapshot.structure().version();
        lastRecipeCapabilityVersion = snapshot.capabilityVersion();
        lastRecipeModifierVersion = snapshot.modifierVersion();
        lastRecipeComponentStateVersion = snapshot.stateVersion();
    }

    public void save(ValueOutput output) {
        output.putBoolean("core", coreThread);
        output.putBoolean("base", baseThread);
        output.putString("name", threadName);
        output.putInt("idle_ticks", idleTicks);
        output.putBoolean("has_last", lastRecipe != null);
        if (lastRecipe != null) {
            output.putString("last_recipe", lastRecipe.id().toString());
            output.putLong("last_structure_version", lastRecipeStructureVersion);
            output.putLong("last_capability_version", lastRecipeCapabilityVersion);
            output.putLong("last_modifier_version", lastRecipeModifierVersion);
            output.putLong("last_component_state_version", lastRecipeComponentStateVersion);
            output.putLong("last_catalog_version", lastRecipeCatalogVersion);
        }
        output.putInt("search_failure_streak", failureStreak);
        long gameTime = controller.getLevel() == null ? 0L : controller.getLevel().getGameTime();
        long remaining = lastSearchFailureKey == null || nextSearchTick == Long.MIN_VALUE
                ? 0L : Math.max(0L, nextSearchTick - gameTime);
        output.putInt("search_retry_remaining", (int) Math.min(100L, remaining));
        output.putString("search_failure_reason", lastSearchFailureReason == null ? "" : lastSearchFailureReason);
        if (lastSearchFailureKey != null) {
            output.putBoolean("has_search_failure_key", true);
            ValueOutput key = output.child("search_failure_key");
            key.putLong("structure_version", lastSearchFailureKey.structureVersion());
            key.putLong("capability_version", lastSearchFailureKey.capabilityVersion());
            key.putLong("modifier_version", lastSearchFailureKey.modifierVersion());
            key.putLong("component_state_version", lastSearchFailureKey.componentStateVersion());
            key.putLong("catalog_version", lastSearchFailureKey.catalogVersion());
            key.putLong("resource_availability_epoch", lastSearchFailureKey.resourceAvailabilityEpoch());
            key.putBoolean("has_locked_recipe", lastSearchFailureKey.lockedRecipeId() != null);
            if (lastSearchFailureKey.lockedRecipeId() != null) {
                key.putString("locked_recipe", lastSearchFailureKey.lockedRecipeId().toString());
            }
            key.putLong("core_recipe_set_version", lastSearchFailureKey.coreRecipeSetVersion());
        }
        runtime.save(output.child("runtime"));
    }

    public static FactoryRecipeThread load(ValueInput input, MachineControllerBlockEntity controller) {
        return load(input, controller, controller.lockedRecipeId(), null);
    }

    public static FactoryRecipeThread load(ValueInput input, MachineControllerBlockEntity controller,
                                           @Nullable Identifier lockedRecipeId,
                                           @Nullable List<MachineRecipe> candidates) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller,
                input.getBooleanOr("core", false), input.getBooleanOr("base", false), input.getStringOr("name", ""));
        List<MachineRecipe> availableCandidates = candidates == null ? catalogCandidates(controller) : candidates;
        RecipeSearchContextKey restoredKey = readSearchFailureKey(input);
        if (thread.coreThread) thread.recipeSet.addAll(availableCandidates);
        thread.idleTicks = input.getIntOr("idle_ticks", 0);
        if (input.getBooleanOr("has_last", false)) {
            String recipeName = input.getStringOr("last_recipe", "");
            Identifier recipeId = recipeName.isEmpty() ? null : Identifier.parse(recipeName);
            thread.lastRecipe = recipeId == null ? null : availableCandidates.stream()
                    .filter(candidate -> candidate != null && recipeId.equals(candidate.id()))
                    .findFirst().orElseGet(() -> RecipeRegistry.getRecipe(recipeId));
            if (thread.lastRecipe != null) {
                thread.lastRecipeStructureVersion = input.getLongOr("last_structure_version", Long.MIN_VALUE);
                thread.lastRecipeCapabilityVersion = input.getLongOr("last_capability_version", Long.MIN_VALUE);
                thread.lastRecipeModifierVersion = input.getLongOr("last_modifier_version", Long.MIN_VALUE);
                thread.lastRecipeComponentStateVersion = input.getLongOr("last_component_state_version", Long.MIN_VALUE);
                thread.lastRecipeCatalogVersion = input.getLongOr("last_catalog_version",
                        RecipeRegistry.catalog(thread.lastRecipe.machineId()).version());
            }
        }
        thread.runtime.load(input.childOrEmpty("runtime"), controller.resourceDomain());
        MachineRecipe activeRecipe = thread.runtime.recipe();
        if (activeRecipe != null) {
            MachineRecipe current = availableCandidates.stream()
                    .filter(candidate -> candidate != null && activeRecipe.id().equals(candidate.id()))
                    .findFirst().orElse(null);
            if (current == null || !ActiveMachineRecipe.sameDefinition(activeRecipe, current)) {
                thread.clearLastRecipe();
            }
        }
        int restoredStreak = Math.max(0, input.getIntOr("search_failure_streak", 0));
        int restoredRemaining = Math.max(0, Math.min(100, input.getIntOr("search_retry_remaining", 0)));
        if (!thread.coreThread && restoredStreak > 0 && restoredKey != null
                && restoredKey.equals(thread.currentSearchContextKey(lockedRecipeId))) {
            thread.failureStreak = restoredStreak;
            thread.nextSearchTick = (controller.getLevel() == null ? 0L : controller.getLevel().getGameTime())
                    + restoredRemaining;
            String reason = input.getStringOr("search_failure_reason", "");
            thread.lastSearchFailureReason = reason.isEmpty() ? null : reason;
            thread.lastSearchFailureKey = restoredKey;
            thread.failureCandidates = thread.lastRecipe == null
                    ? List.copyOf(availableCandidates) : List.of(thread.lastRecipe);
            thread.updateFailureResourceMatchers(thread.failureCandidates);
        } else {
            thread.clearSearchFailure();
        }
        return thread;
    }

    private static List<MachineRecipe> catalogCandidates(MachineControllerBlockEntity controller) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        MachineRecipeCatalog catalog = RecipeRegistry.catalog(snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() == null
                ? null : snapshot.structure().configuredMachine().registryName()
                : snapshot.structure().machine().registryName());
        return catalog.recipes();
    }

    private static @Nullable RecipeSearchContextKey readSearchFailureKey(ValueInput input) {
        if (!input.getBooleanOr("has_search_failure_key", false)) return null;
        ValueInput key = input.childOrEmpty("search_failure_key");
        String locked = key.getStringOr("locked_recipe", "");
        Identifier lockedRecipe = key.getBooleanOr("has_locked_recipe", false) && !locked.isEmpty()
                ? Identifier.parse(locked) : null;
        return new RecipeSearchContextKey(key.getLongOr("structure_version", Long.MIN_VALUE),
                key.getLongOr("capability_version", Long.MIN_VALUE),
                key.getLongOr("modifier_version", Long.MIN_VALUE),
                key.getLongOr("component_state_version", Long.MIN_VALUE),
                key.getLongOr("catalog_version", Long.MIN_VALUE),
                key.getLongOr("resource_availability_epoch", Long.MIN_VALUE), lockedRecipe,
                key.getLongOr("core_recipe_set_version", Long.MIN_VALUE));
    }
}
