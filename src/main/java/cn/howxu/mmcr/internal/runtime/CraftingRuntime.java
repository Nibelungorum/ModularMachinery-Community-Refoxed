package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeFinishContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeStartContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeTickContext;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns one recipe lifecycle. Capability plans are the only mutable-resource boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingRuntime {
    private final MachineControllerBlockEntity controller;
    private final ComponentRuntime components;
    private @Nullable ActiveMachineRecipe activeRecipe;
    private @Nullable CraftingPlan startPlan;
    private @Nullable CraftingPlan finishPlan;
    private List<MachineRequirement> effectiveRequirements = List.of();
    private List<MachineOutput> effectiveOutputs = List.of();
    private Set<Integer> consumedAtStart = Set.of();
    private Set<Integer> retainedInputs = Set.of();
    private @Nullable ExecutionStatus failure;
    private long structureVersion = Long.MIN_VALUE;
    private long capabilityVersion = Long.MIN_VALUE;
    private long modifierVersion = Long.MIN_VALUE;
    private long componentStateVersion = Long.MIN_VALUE;
    private @Nullable StructureClaimRegistry.ResourceDomain resourceDomain;
    private CraftingStatus status = CraftingStatus.IDLE;
    private boolean finishCommitInProgress;
    private boolean smartInterfaceChangePending;

    public CraftingRuntime(MachineControllerBlockEntity controller, ComponentRuntime components) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (components == null) throw new IllegalArgumentException("components must not be null");
        this.controller = controller;
        this.components = components;
    }

    public CraftingStatus start(MachineRecipe recipe, int requestedParallelism) {
        if (recipe == null || requestedParallelism <= 0) {
            return fail("invalid_start");
        }
        if (active()) return status;

        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        if (!runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) {
            return fail("module_connection");
        }
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return fail("recipe_behavior");
        int effectiveParallelism = Math.max(1, Math.min(requestedParallelism, runtime.maxParallelism()));
        List<MachineRequirement> requirements = recipe.runtimeRequirements(contextModifiers(runtime));
        List<MachineOutput> outputs = recipe.runtimeMachineOutputs(contextModifiers(runtime));
        MachineBehaviorContext machineContext = controller.behaviorContext();
        RecipeStartContext startContext = new RecipeStartContext(machineContext, recipe, requestedParallelism,
                effectiveParallelism, duration(recipe, runtime), requirements, outputs);
        try {
            behavior.beforeStart().accept(startContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeStart", runtime, recipe, exception);
            return fail("behavior_before_start");
        }
        if (startContext.cancelled()) {
            failure = null;
            status = CraftingStatus.IDLE;
            return status;
        }
        RecipeStartContext.ExecutionSnapshot effective = startContext.snapshot();
        CraftingContext context = context(runtime);
        PlanningResult result = context.planInputs(effective.requirements(), requestedParallelism,
                Set.of(), Set.of());
        CraftingPlan plan = result.plan();
        if (!result.successful() || plan == null) {
            return fail(result.failure());
        }
        if (!plan.commitInputs()) {
            return fail(plan.failure());
        }

        activeRecipe = new ActiveMachineRecipe(recipe, plan.parallelism(), effective);
        activeRecipe.setParallelism(plan.parallelism());
        startPlan = plan;
        finishPlan = null;
        effectiveRequirements = MachineRequirement.copyList(effective.requirements());
        effectiveOutputs = MachineOutput.copyList(effective.outputs());
        captureInputState(effectiveRequirements, plan);
        captureVersions(runtime);
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    public CraftingStatus tick() {
        if (!active()) return status;
        if (!versionsCurrent()) return invalidate("version_invalidated");
        if (activeRecipe.isFinishPending()) return status;

        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return waiting(failure("recipe_behavior"));
        try {
            behavior.recipeTick().accept(new RecipeTickContext(controller.behaviorContext(), activeRecipe.getRecipe(),
                    activeRecipe.getTick(), activeRecipe.getTotalTick(), activeRecipe.getParallelism(),
                    effectiveRequirements(), effectiveOutputs()));
        } catch (RuntimeException exception) {
            logCallbackFailure("recipeTick", runtime, activeRecipe.getRecipe(), exception);
        }
        CraftingContext context = context(runtime);
        PlanningResult result = context.planInputs(effectiveRequirements(), activeRecipe.getParallelism(),
                consumedAtStart, retainedInputs);
        CraftingPlan tickPlan = result.plan();
        if (!result.successful() || tickPlan == null || tickPlan.parallelism() < activeRecipe.getParallelism()) {
            return waiting(result.failure());
        }
        if (!tickPlan.commit()) return waiting(tickPlan.failure());

        int gameTime = currentGameTime();
        if (activeRecipe.needsFinishCommit()) {
            activeRecipe.beginFinishCommit();
            status = CraftingStatus.working();
            return status;
        }
        activeRecipe.applyTickGrant(true, false, gameTime);
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    public CraftingStatus finish() {
        if (!active()) return status;
        if (!versionsCurrent()) return invalidate("version_invalidated");
        if (!activeRecipe.isFinishPending()) return status;
        if (!activeRecipe.shouldRetryFinish(currentGameTime())) return status;

        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        RecipeBehavior behavior = recipeBehavior(runtime);
        if (behavior == null) return finishBlocked(failure("recipe_behavior"));
        CraftingContext context = context(runtime);
        RecipeFinishContext finishContext = new RecipeFinishContext(controller.behaviorContext(),
                activeRecipe.getRecipe(), activeRecipe.getMaxParallelism(), activeRecipe.getParallelism(),
                effectiveOutputs());
        try {
            behavior.beforeFinish().accept(finishContext);
        } catch (RuntimeException exception) {
            logCallbackFailure("beforeFinish", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure("behavior_before_finish"));
        }
        if (finishContext.cancelled()) return finishBlocked(failure("behavior_before_finish_cancelled"));
        if (finishContext.outputsDiscarded()) {
            activeRecipe.applyTickGrant(true, true, currentGameTime());
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
            result = context.planOutputRequirements(effectiveRequirements(), finishContext.outputs(),
                    activeRecipe.getParallelism(), activeRecipe.getRecipe().allowPartialOutputs());
        } catch (IllegalArgumentException exception) {
            logCallbackFailure("beforeFinish.output_validation", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure("invalid_outputs"));
        } catch (RuntimeException exception) {
            logFinishFailure("planning", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure("finish"));
        }
        finishPlan = result.plan();
        if (!result.successful() || finishPlan == null) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return finishBlocked(result.failure());
        }
        finishCommitInProgress = true;
        boolean committed;
        try {
            committed = finishPlan.commit();
        } catch (RuntimeException exception) {
            logFinishFailure("commit", runtime, activeRecipe.getRecipe(), exception);
            return finishBlocked(failure("finish"));
        } finally {
            finishCommitInProgress = false;
        }
        if (!committed) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return finishBlocked(finishPlan.failure());
        }
        if (smartInterfaceChangePending) {
            smartInterfaceChangePending = false;
            return invalidate("smart_interface_changed");
        }

        activeRecipe.applyTickGrant(true, true, currentGameTime());
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

    public boolean active() {
        return activeRecipe != null;
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
        String lockedRecipeId = controller.lockedRecipeId() == null ? "" : controller.lockedRecipeId().toString();
        return new CraftingStateSnapshot(activeRecipe == null ? null : activeRecipe.getRecipe().id(), status, failure,
                structureVersion == Long.MIN_VALUE ? 0L : structureVersion,
                capabilityVersion == Long.MIN_VALUE ? 0L : capabilityVersion,
                modifierVersion == Long.MIN_VALUE ? 0L : modifierVersion,
                tickCount(), totalTick(), parallelism(), recipe == null ? 1 : recipe.getMaxParallelism(),
                !lockedRecipeId.isEmpty(), lockedRecipeId);
    }

    public @Nullable MachineRecipe recipe() {
        return activeRecipe == null ? null : activeRecipe.getRecipe();
    }

    public @Nullable ActiveMachineRecipe activeRecipe() {
        return activeRecipe;
    }

    public @Nullable String failureUnloc() {
        return failure == null ? null : failureUnloc(failure);
    }

    public int tickCount() {
        return activeRecipe == null ? 0 : activeRecipe.getTick();
    }

    public int totalTick() {
        return activeRecipe == null ? 0 : activeRecipe.getTotalTick();
    }

    public int parallelism() {
        return activeRecipe == null ? 0 : activeRecipe.getParallelism();
    }

    public int maxParallelism() {
        return activeRecipe == null ? 1 : activeRecipe.getMaxParallelism();
    }

    public boolean finishPending() {
        return activeRecipe != null && activeRecipe.isFinishPending();
    }

    public boolean shouldRetryFinish() {
        return activeRecipe != null && activeRecipe.shouldRetryFinish(currentGameTime());
    }

    public void recordSearchFailure(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null ? failure("recipe_search") : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    public boolean versionsCurrent() {
        if (!active()) return true;
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
        return structureVersion == runtime.structure().version()
                && capabilityVersion == runtime.capabilityVersion()
                && modifierVersion == runtime.modifierVersion()
                && componentStateVersion == runtime.stateVersion();
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        return resourceDomain;
    }

    public void invalidate() {
        activeRecipe = null;
        startPlan = null;
        finishPlan = null;
        clearEffectiveRecipe();
        consumedAtStart = Set.of();
        retainedInputs = Set.of();
        resourceDomain = null;
        smartInterfaceChangePending = false;
        failure = null;
        status = CraftingStatus.IDLE;
    }

    public void invalidateForSmartInterfaceChange() {
        if (!active()) return;
        if (finishCommitInProgress) {
            smartInterfaceChangePending = true;
            return;
        }
        invalidate("smart_interface_changed");
    }

    public void restore(ActiveMachineRecipe restored, @Nullable StructureClaimRegistry.ResourceDomain domain,
                        long restoredStructureVersion, long restoredCapabilityVersion,
                        long restoredModifierVersion, long restoredComponentStateVersion) {
        if (restored == null || restored.getRecipe() == null) {
            failLoad();
            return;
        }
        ControllerRuntimeSnapshot runtime = controller.currentRuntimeSnapshot();
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
                    duration(restored.getRecipe(), runtime), requirements, outputs));
            if (restored.getTotalTick() < 1 || restored.getTick() < 0
                    || restored.getTick() > restored.getTotalTick()
                    || (restored.isFinishPending() && restored.getTick() != restored.getTotalTick() - 1)) {
                failLoad();
                return;
            }
        }
        activeRecipe = restored;
        startPlan = null;
        finishPlan = null;
        resourceDomain = domain;
        structureVersion = restoredStructureVersion;
        capabilityVersion = restoredCapabilityVersion;
        modifierVersion = restoredModifierVersion;
        componentStateVersion = restoredComponentStateVersion;
        effectiveRequirements = MachineRequirement.copyList(requirements);
        effectiveOutputs = MachineOutput.copyList(outputs);
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(ItemRequirement.TYPE.equals(requirement.type()) || FluidRequirement.TYPE.equals(requirement.type()))
                    || requirement.io() != RecipeModifier.IOType.INPUT) continue;
            if (restored.inputConsumptionPlan().consumedBatches(index) > 0) consumed.add(index);
            else retained.add(index);
        }
        consumedAtStart = Set.copyOf(consumed);
        retainedInputs = Set.copyOf(retained);
        List<Integer> consumedBatches = new java.util.ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            consumedBatches.add(consumed.contains(index) ? 1 : 0);
        }
        activeRecipe.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(consumedBatches));
        status = CraftingStatus.working();
        failure = null;
    }

    public void rebindCurrentVersions() {
        if (active()) captureVersions(controller.currentRuntimeSnapshot());
    }

    public void save(ValueOutput output) {
        boolean present = activeRecipe != null && activeRecipe.getRecipe() != null;
        output.putBoolean("active", present);
        if (present) {
            output.putLong("structure_version", structureVersion);
            output.putLong("capability_version", capabilityVersion);
            output.putLong("modifier_version", modifierVersion);
            output.putLong("component_state_version", componentStateVersion);
            activeRecipe.serialize(output.child("recipe"));
        }
    }

    public void load(ValueInput input, @Nullable StructureClaimRegistry.ResourceDomain domain) {
        boolean active = input.getBooleanOr("active", false);
        if (!active) {
            invalidate();
            return;
        }
        ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.load(input.childOrEmpty("recipe"));
        if (!loaded.successful()) {
            failLoad();
            return;
        }
        restore(loaded.recipe(), domain,
                input.getLongOr("structure_version", Long.MIN_VALUE),
                input.getLongOr("capability_version", Long.MIN_VALUE),
                input.getLongOr("modifier_version", Long.MIN_VALUE),
                input.getLongOr("component_state_version", Long.MIN_VALUE));
    }

    private void failLoad() {
        invalidate();
        failure = failure("recipe_load");
        status = CraftingStatus.failure(failureUnloc(failure));
    }

    private CraftingStatus waiting(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null ? failure("per_tick") : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        if (activeRecipe != null && activeRecipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
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
        failure = nextFailure == null ? failure("finish") : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private CraftingStatus invalidate(String reason) {
        failure = failure(reason);
        activeRecipe = null;
        startPlan = null;
        finishPlan = null;
        clearEffectiveRecipe();
        consumedAtStart = Set.of();
        retainedInputs = Set.of();
        resourceDomain = null;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private CraftingStatus fail(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null ? failure("start") : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        return status;
    }

    private CraftingStatus fail(String reason) {
        return fail(failure(reason));
    }

    private CraftingContext context(ControllerRuntimeSnapshot runtime) {
        return new CraftingContext(new CapabilitySnapshot(components.capabilities()), contextModifiers(runtime));
    }

    private RecipeBehavior recipeBehavior(ControllerRuntimeSnapshot runtime) {
        MachineBehavior behavior = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() == null ? null : runtime.structure().configuredMachine().behavior()
                : runtime.structure().machine().behavior();
        return behavior instanceof RecipeBehavior recipeBehavior ? recipeBehavior : null;
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

    private List<RecipeModifier> contextModifiers(ControllerRuntimeSnapshot runtime) {
        return runtime.foundModifiers().values().stream().flatMap(List::stream).toList();
    }

    private void captureVersions(ControllerRuntimeSnapshot runtime) {
        structureVersion = runtime.structure().version();
        capabilityVersion = runtime.capabilityVersion();
        modifierVersion = runtime.modifierVersion();
        componentStateVersion = runtime.stateVersion();
        resourceDomain = controller.resourceDomain();
    }

    private void captureInputState(List<MachineRequirement> requirements, CraftingPlan plan) {
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(ItemRequirement.TYPE.equals(requirement.type()) || FluidRequirement.TYPE.equals(requirement.type()))
                    || requirement.io() != RecipeModifier.IOType.INPUT) continue;
            if (plan.hasOperations(index)) consumed.add(index);
            else retained.add(index);
        }
        consumedAtStart = Set.copyOf(consumed);
        retainedInputs = Set.copyOf(retained);
        List<Integer> consumedBatches = new java.util.ArrayList<>(requirements.size());
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

    private List<MachineOutput> effectiveOutputs() {
        return activeRecipe != null && activeRecipe.hasEffectiveExecutionSnapshot()
                ? activeRecipe.effectiveOutputs() : effectiveOutputs;
    }

    private int duration(MachineRecipe recipe, ControllerRuntimeSnapshot runtime) {
        List<RecipeModifier> modifiers = new ArrayList<>(recipe.modifiers());
        modifiers.addAll(contextModifiers(runtime));
        double levelMultiplier = runtime.foundLevels().values().stream()
                .mapToDouble(level -> level.modifier().durationMultiplier())
                .reduce(1D, (left, right) -> left * right);
        int levelModifiedDuration = (int) Math.round(recipe.getRecipeTotalTickTime() * levelMultiplier);
        return Math.max(1, cn.howxu.mmcr.api.recipe.IntegrationTypeHelper.asInt(
                cn.howxu.mmcr.api.recipe.IntegrationTypeHelper.applyDuration(modifiers, levelModifiedDuration)));
    }

    private int currentGameTime() {
        if (controller.getLevel() == null) return 0;
        long gameTime = controller.getLevel().getGameTime();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime));
    }

    private static ExecutionStatus failure(String reason) {
        return new ExecutionStatus(MMCR.id("crafting_runtime"), StatusSeverity.BLOCKED,
                MMCR.id("crafting_runtime"), Map.of("reason", reason));
    }

    private static String failureUnloc(ExecutionStatus status) {
        if (status == null) return "";
        return switch (status.details().getOrDefault("reason", "")) {
            case "module_connection" -> "gui.mmcr.controller.failure.module_connection";
            case "level_insufficient" -> "gui.mmcr.controller.failure.level_insufficient";
            case "insufficient_energy" -> "gui.mmcr.controller.failure.missing_energy";
            case "version_invalidated" -> "gui.mmcr.controller.failure.structure_changed";
            case "smart_interface_changed" -> "gui.mmcr.controller.failure.smart_interface_changed";
            case "finish", "no_output_capacity" -> "gui.mmcr.controller.failure.missing_output";
            case "per_tick" -> "gui.mmcr.controller.failure.missing_input";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }
}
