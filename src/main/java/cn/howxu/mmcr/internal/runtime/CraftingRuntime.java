package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
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
    private @Nullable ActiveMachineRecipe activeRecipe;
    private @Nullable CraftingPlan startPlan;
    private @Nullable CraftingPlan tickPlan;
    private @Nullable CraftingPlan finishPlan;
    private Set<Integer> consumedAtStart = Set.of();
    private Set<Integer> retainedInputs = Set.of();
    private @Nullable ExecutionStatus failure;
    private long structureVersion = Long.MIN_VALUE;
    private long capabilityVersion = Long.MIN_VALUE;
    private long modifierVersion = Long.MIN_VALUE;
    private @Nullable StructureClaimRegistry.ResourceDomain resourceDomain;
    private CraftingStatus status = CraftingStatus.IDLE;

    public CraftingRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
    }

    public CraftingStatus start(MachineRecipe recipe, int requestedParallelism) {
        if (recipe == null || requestedParallelism <= 0) {
            return fail("invalid_start");
        }
        if (active()) return status;

        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        if (!runtime.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) {
            return fail("module_connection");
        }
        int parallelismLimit = recipe.maxThreads() <= 0
                ? requestedParallelism : Math.min(requestedParallelism, recipe.maxThreads());
        CraftingContext context = context(runtime);
        PlanningResult result = context.planStartResult(recipe, parallelismLimit);
        CraftingPlan plan = result.plan();
        if (!result.successful() || plan == null) {
            return fail(result.failure());
        }
        if (!plan.commitInputs()) {
            return fail(plan.failure());
        }

        activeRecipe = new ActiveMachineRecipe(recipe, plan.parallelism());
        activeRecipe.setTotalTick(duration(recipe, runtime));
        startPlan = plan;
        tickPlan = null;
        finishPlan = null;
        captureInputState(recipe, plan, runtime);
        captureVersions(runtime);
        status = CraftingStatus.working();
        failure = null;
        return status;
    }

    public CraftingStatus tick() {
        if (!active()) return status;
        if (!versionsCurrent()) return invalidate("version_invalidated");
        if (activeRecipe.isFinishPending()) return status;

        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        CraftingContext context = context(runtime);
        PlanningResult result = context.planInputs(activeRecipe.getRecipe(), activeRecipe.getParallelism(),
                consumedAtStart, retainedInputs);
        tickPlan = result.plan();
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

        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        CraftingContext context = context(runtime);
        PlanningResult result = context.planOutputs(activeRecipe.getRecipe(), activeRecipe.getParallelism());
        finishPlan = result.plan();
        if (!result.successful() || finishPlan == null) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return waiting(result.failure());
        }
        if (!finishPlan.commit()) {
            activeRecipe.markFinishBlocked(currentGameTime());
            return waiting(finishPlan.failure());
        }

        activeRecipe.applyTickGrant(true, true, currentGameTime());
        activeRecipe = null;
        startPlan = null;
        tickPlan = null;
        finishPlan = null;
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
        return new CraftingStateSnapshot(activeRecipe == null ? null : activeRecipe.getRecipe().id(), status, failure,
                structureVersion == Long.MIN_VALUE ? 0L : structureVersion,
                capabilityVersion == Long.MIN_VALUE ? 0L : capabilityVersion,
                modifierVersion == Long.MIN_VALUE ? 0L : modifierVersion);
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

    public boolean finishPending() {
        return activeRecipe != null && activeRecipe.isFinishPending();
    }

    public boolean versionsCurrent() {
        if (!active()) return true;
        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        return structureVersion == runtime.structure().version()
                && capabilityVersion == runtime.capabilityVersion()
                && modifierVersion == runtime.modifierVersion();
    }

    public @Nullable StructureClaimRegistry.ResourceDomain resourceDomain() {
        return resourceDomain;
    }

    public void invalidate() {
        activeRecipe = null;
        startPlan = null;
        tickPlan = null;
        finishPlan = null;
        consumedAtStart = Set.of();
        retainedInputs = Set.of();
        resourceDomain = null;
        status = CraftingStatus.IDLE;
    }

    public void restore(ActiveMachineRecipe restored, @Nullable StructureClaimRegistry.ResourceDomain domain,
                        long restoredStructureVersion, long restoredCapabilityVersion,
                        long restoredModifierVersion) {
        if (restored == null || restored.getRecipe() == null) {
            invalidate();
            return;
        }
        activeRecipe = restored;
        startPlan = null;
        tickPlan = null;
        finishPlan = null;
        resourceDomain = domain;
        structureVersion = restoredStructureVersion;
        capabilityVersion = restoredCapabilityVersion;
        modifierVersion = restoredModifierVersion;
        ControllerRuntimeSnapshot runtime = controller.runtimeSnapshot();
        List<MachineRequirement> requirements = restored.getRecipe().runtimeRequirements(contextModifiers(runtime));
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(requirement instanceof ItemRequirement || requirement instanceof FluidRequirement)
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

    public void save(ValueOutput output) {
        boolean present = activeRecipe != null && activeRecipe.getRecipe() != null;
        output.putBoolean("active", present);
        if (present) {
            output.putLong("structure_version", structureVersion);
            output.putLong("capability_version", capabilityVersion);
            output.putLong("modifier_version", modifierVersion);
            activeRecipe.serialize(output.child("recipe"));
        }
    }

    public void load(ValueInput input, @Nullable StructureClaimRegistry.ResourceDomain domain) {
        if (!input.getBooleanOr("active", false)) {
            invalidate();
            return;
        }
        restore(ActiveMachineRecipe.from(input.childOrEmpty("recipe")), domain,
                input.getLongOr("structure_version", Long.MIN_VALUE),
                input.getLongOr("capability_version", Long.MIN_VALUE),
                input.getLongOr("modifier_version", Long.MIN_VALUE));
    }

    private CraftingStatus waiting(@Nullable ExecutionStatus nextFailure) {
        failure = nextFailure == null ? failure("per_tick") : nextFailure;
        status = CraftingStatus.failure(failureUnloc(failure));
        if (activeRecipe != null && activeRecipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
            activeRecipe = null;
            startPlan = null;
            tickPlan = null;
            finishPlan = null;
            resourceDomain = null;
        }
        return status;
    }

    private CraftingStatus invalidate(String reason) {
        failure = failure(reason);
        activeRecipe = null;
        startPlan = null;
        tickPlan = null;
        finishPlan = null;
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
        return new CraftingContext(new CapabilitySnapshot(runtime.capabilities()), contextModifiers(runtime));
    }

    private List<RecipeModifier> contextModifiers(ControllerRuntimeSnapshot runtime) {
        return runtime.foundModifiers().values().stream().flatMap(List::stream).toList();
    }

    private void captureVersions(ControllerRuntimeSnapshot runtime) {
        structureVersion = runtime.structure().version();
        capabilityVersion = runtime.capabilityVersion();
        modifierVersion = runtime.modifierVersion();
        resourceDomain = controller.resourceDomain();
    }

    private void captureInputState(MachineRecipe recipe, CraftingPlan plan, ControllerRuntimeSnapshot runtime) {
        List<MachineRequirement> requirements = recipe.runtimeRequirements(contextModifiers(runtime));
        Set<Integer> consumed = new HashSet<>();
        Set<Integer> retained = new HashSet<>();
        for (int index = 0; index < requirements.size(); index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(requirement instanceof ItemRequirement || requirement instanceof FluidRequirement)
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
            case "version_invalidated" -> "gui.mmcr.controller.failure.structure_changed";
            case "per_tick" -> "gui.mmcr.controller.failure.missing_input";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }
}
