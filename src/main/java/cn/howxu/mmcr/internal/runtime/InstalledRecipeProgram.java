package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import net.neoforged.neoforge.energy.IEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.util.SaturatingLong;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main-thread live binding of one immutable program. Only closed built-in FE operations are reused;
 * wrappers, dynamic admission and mixed sets use the live fixed planner and native commit fencing.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InstalledRecipeProgram {
    private final ExecutionIdentity identity;
    private final CompiledRecipeProgram program;
    private final CraftingRuntime owner;
    private final RecipeCommitLedger ledger;
    private final CraftingContext planningContext;
    private final @Nullable List<MachineRequirement> finishRequirements;
    private final List<MachineRequirement> continuousRequirements;
    private final List<Integer> continuousIndexes;
    private final Map<Integer, OutputPolicy> continuousPolicies;
    private final @Nullable List<EnergyRule> energyRules;
    private final IEnergyStorage[] storages;
    private final long[] virtualAmounts;
    private boolean cancelled;
    private long lastAttemptRound = Long.MIN_VALUE;
    private long lastAttemptGameTime = Long.MIN_VALUE;

    public InstalledRecipeProgram(ExecutionIdentity identity, CompiledRecipeProgram program, CraftingRuntime owner) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.program = Objects.requireNonNull(program, "program");
        this.owner = Objects.requireNonNull(owner, "owner");
        ledger = owner.ledger();
        planningContext = owner.bindInstalledPlanningContext();
        finishRequirements = owner.installedFinishRequirements();
        continuousRequirements = List.copyOf(owner.installedTickRequirements());
        continuousIndexes = owner.installedTickIndexes();
        continuousPolicies = owner.installedTickOutputPolicies(continuousRequirements);
        List<IEnergyStorage> bindings = new ArrayList<>();
        Map<IEnergyStorage, Integer> storageIds = new IdentityHashMap<>();
        List<EnergyRule> rules = new ArrayList<>();
        boolean compatible = program.tick().requiresCompatibility();
        List<MachineRequirement> requirements = continuousRequirements;
        for (int index = 0; index < requirements.size() && !compatible; index++) {
            MachineRequirement requirement = requirements.get(index);
            if (!(requirement instanceof EnergyRequirement energy)
                    || !(RequirementHandlerRegistry.handlerFor(energy.type()) instanceof EnergyRequirementHandler)) {
                compatible = true;
                break;
            }
            boolean insert = energy.io() == RecipeModifier.IOType.OUTPUT;
            List<MachineCapability> candidates = RequirementPlanner.matchingCapabilities(energy,
                    owner.controller().componentRuntime().capabilities());
            if (insert) candidates = candidates.stream()
                    .sorted(Comparator.comparingInt(MachineCapability::outputPriority).reversed()).toList();
            int[] ids = new int[candidates.size()];
            for (int candidate = 0; candidate < candidates.size(); candidate++) {
                if (!(candidates.get(candidate) instanceof EnergyHatchCapability hatch)
                        || !(hatch.energyStorage() instanceof LongEnergyStorage)) {
                    compatible = true;
                    break;
                }
                IEnergyStorage storage = hatch.energyStorage();
                Integer id = storageIds.get(storage);
                if (id == null) {
                    id = bindings.size();
                    storageIds.put(storage, id);
                    bindings.add(storage);
                }
                ids[candidate] = id;
            }
            boolean partial = continuousPolicies.getOrDefault(continuousIndexes.get(index), OutputPolicy.REQUIRE_FULL)
                    == OutputPolicy.ALLOW_PARTIAL;
            rules.add(new EnergyRule(continuousIndexes.get(index), SaturatingLong.multiply(energy.fePerTick(), program.parallelism()),
                    insert, partial, ids, new long[ids.length]));
        }
        energyRules = compatible ? null : List.copyOf(rules);
        storages = bindings.toArray(IEnergyStorage[]::new);
        virtualAmounts = new long[storages.length];
        ledger.bindInstalledProgram(this);
    }

    public ExecutionIdentity identity() { return identity; }
    public CompiledRecipeProgram program() { return program; }
    public RecipeCommitLedger ledger() { return ledger; }
    public CraftingRuntime owner() { return owner; }
    public boolean cancelled() { return cancelled; }
    public boolean current() { return !cancelled && owner.installedCurrent(this); }

    /** Completion rules and their live binding are ready, including main-thread compatibility/callbacks. */
    public boolean finishReady() {
        return current() && !ledger.state().outputPaid() && ledger.state().phase() != RecipeCommitLedger.Phase.ERROR_PAUSED;
    }

    /** The coordinator may charge a finish-only attempt only after the existing retry gate opens. */
    public boolean finishAttemptReady() {
        return ledger.state().phase() == RecipeCommitLedger.Phase.WAITING_FINISH
                && ledger.state().finalTickPaid() && owner.finishPending() && finishReady() && owner.shouldRetryFinish();
    }

    CraftingContext planningContext() { return planningContext; }

    /** Null means finish outputs must be validated after the main-thread beforeFinish callback. */
    @Nullable List<MachineRequirement> finishRequirements() { return finishRequirements; }

    public boolean tryAttempt(long roundId, long gameTime) {
        if (lastAttemptRound == roundId || lastAttemptGameTime == gameTime
                || ledger.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED || !current()
                || !owner.finishPending() && owner.controller().activeWorkMode() != MachineWorkMode.SYNC
                && !owner.installedPreparationCurrent(roundId, gameTime)
                || !owner.claimInstalledAttempt(gameTime)) return false;
        lastAttemptRound = roundId;
        lastAttemptGameTime = gameTime;
        return true;
    }

    /** Executes resource operations only. Caller owns payment journaling, commit and phase advancement. */
    public ExecutionStatus executeContinuous() {
        if (!current() || owner.finishPending() || ledger.state().phase() != RecipeCommitLedger.Phase.READY) {
            return owner.invalidInstalledStatus();
        }
        if (energyRules == null) return owner.executeFixedTick(planningContext, continuousRequirements,
                continuousIndexes, continuousPolicies);
        for (int index = 0; index < storages.length; index++) virtualAmounts[index] = amount(storages[index]);
        for (EnergyRule rule : energyRules) {
            Arrays.fill(rule.amounts(), 0L);
            long remaining = rule.required();
            for (int candidate = 0; candidate < rule.ids().length && remaining > 0L; candidate++) {
                int id = rule.ids()[candidate];
                IEnergyStorage storage = storages[id];
                long available = rule.insert() ? capacity(storage) - virtualAmounts[id] : virtualAmounts[id];
                long transferable = rule.insert() ? available : Math.min(available,
                        SaturatingLong.multiply(limit(storage), program.parallelism()));
                long moved = Math.min(remaining, Math.max(0L, transferable));
                rule.amounts()[candidate] = moved;
                virtualAmounts[id] += rule.insert() ? moved : -moved;
                remaining -= moved;
            }
            if (remaining > 0L && (!rule.insert() || !rule.partial())
                    || rule.insert() && rule.required() > 0L && remaining == rule.required()) {
                return owner.resourceFailure(rule.insert(), rule.index(), rule.required(), rule.required() - remaining);
            }
        }
        ledger.beginNativeExecution();
        for (EnergyRule rule : energyRules) {
            for (int candidate = 0; candidate < rule.ids().length; candidate++) {
                long amount = rule.amounts()[candidate];
                if (amount == 0L) continue;
                IEnergyStorage storage = storages[rule.ids()[candidate]];
                if (!current()) return owner.invalidInstalledStatus();
                long moved = move(storage, amount, rule.insert());
                // Native hatches use one operation per candidate, even at parallelism > 1.
                if (!current()) return owner.invalidInstalledStatus();
                if (moved != amount) return owner.resourceCommitFailure(rule.insert(), rule.index(), amount, moved);
            }
        }
        return null;
    }

    /** One already-qualified, already-charged attempt; never calls tryAttempt again. */
    public boolean advance() { return !cancelled && owner.advanceInstalled(this); }

    public void cancel() {
        if (cancelled) return;
        cancelled = true;
        SharedIoCoordinator.executionChanged(this);
        ledger.unbindInstalledProgram(this);
    }

    private static long amount(IEnergyStorage storage) {
        return storage instanceof LongEnergyHandler handler ? handler.getAmountAsLong() : storage.getEnergyStored();
    }
    private static long capacity(IEnergyStorage storage) {
        return storage instanceof LongEnergyHandler handler ? handler.getCapacityAsLong() : storage.getMaxEnergyStored();
    }
    private static long limit(IEnergyStorage storage) {
        return storage instanceof LongEnergyHandler handler ? handler.getTransferLimit() : Integer.MAX_VALUE;
    }
    private long move(IEnergyStorage storage, long amount, boolean insert) {
        if (insert && storage instanceof LongEnergyStorage nativeStorage) {
            return nativeStorage.forceInsert(amount, false);
        }
        // One candidate may span multiple native per-call limits at installed parallelism.
        long remaining = amount;
        while (remaining > 0L) {
            if (!current()) break;
            long chunk = Math.min(remaining, limit(storage));
            if (chunk <= 0L) break;
            long moved = storage instanceof LongEnergyHandler handler
                    ? insert ? handler.insertLong(chunk, false) : handler.extractLong(chunk, false)
                    : insert ? storage.receiveEnergy((int) chunk, false) : storage.extractEnergy((int) chunk, false);
            remaining -= moved;
            if (moved != chunk) break;
        }
        return amount - remaining;
    }

    /** Mutable attempt amounts remain main-thread local. @author howxu <dev@howxu.cn> */
    private record EnergyRule(int index, long required, boolean insert, boolean partial, int[] ids, long[] amounts) { }
}
