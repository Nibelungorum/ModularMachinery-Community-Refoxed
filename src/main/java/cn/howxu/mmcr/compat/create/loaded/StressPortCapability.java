package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.compat.create.StressState;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.compat.create.StressContributions;
import cn.howxu.mmcr.compat.create.StressContributions.Contribution;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/** Stable internal facet for a native kinetic entity; never creates a network on query.
 * @author howxu <dev@howxu.cn>
 */
public final class StressPortCapability implements MachineCapability, CapabilityView, StressFacet {
    private final KineticBlockEntity entity;
    private final StressInterfaceKind kind;
    private final DoubleSupplier capacity;
    private final DoubleSupplier stress;
    private final Runnable onChanged;
    private final StressContributions contributions = new StressContributions();
    private final Map<StressSession, Set<Integer>> owners = new IdentityHashMap<>();
    private final CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(this));
    private BlockPos controllerPos;
    private StressState notifiedState;
    private Object notifiedNetwork;
    private KineticNetwork nativeNetwork;

    public StressPortCapability(KineticBlockEntity entity, StressInterfaceKind kind,
                                DoubleSupplier capacity, DoubleSupplier stress, Runnable onChanged) {
        this.entity = entity;
        this.kind = kind;
        this.capacity = capacity;
        this.stress = stress;
        this.onChanged = onChanged;
    }

    public CapabilitySnapshot snapshot() { return snapshot; }
    public double baseStress() { return contributions.baseStress(); }
    public double generatedRpm() { return contributions.generatedRpm(); }
    @Override public CapabilityType type() { return StressInterfaceKind.TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.of(kind.ioType()); }
    @Override public IOType ioType() { return kind.ioType(); }
    @Override public CapabilityView view() { return this; }
    @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(StressFacet.class); }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return () -> failure(CreateFailureReasons.MISSING_RECIPE_SCOPE, null);
    }

    @Override
    public Object networkIdentity() {
        if (!serverEntity() || !entity.hasNetwork()) return null;
        return nativeNetwork != null && entity.network.equals(nativeNetwork.id)
                && nativeNetwork.members.containsKey(entity) ? nativeNetwork : null;
    }

    public void networkChanged(KineticNetwork network) {
        nativeNetwork = network;
        notifyStateTransition();
    }

    @Override
    public StressState state() {
        boolean connected = networkIdentity() != null;
        double generated = entity.getGeneratedSpeed();
        double localRpm = kind.ioType() == IOType.INPUT ? entity.getTheoreticalSpeed() : generated;
        return new StressState(entity.getBlockPos(), entity.getSpeed(), entity.getTheoreticalSpeed(), generated,
                baseStress(), (float) baseStress() * Math.abs((float) localRpm), connected ? capacity.getAsDouble() : 0D,
                connected ? stress.getAsDouble() : 0D, connected, entity.isOverStressed());
    }

    @Override public boolean stressEnabled() { return StressImpact.isEnabled(); }

    @Override
    public boolean acceptsGeneratedRpm(StressSession session, int requirementIndex, double rpm) {
        return kind.ioType() == IOType.OUTPUT && validRpm(rpm)
                && contributions.accepts(session, requirementIndex, rpm);
    }

    private static boolean validRpm(double rpm) {
        return Double.isFinite(rpm) && rpm != 0D && Float.isFinite((float) rpm) && (float) rpm != 0F
                && Math.abs(rpm) <= AllConfigs.server().kinetics.maxRotationSpeed.get();
    }

    @Override
    public double ownedBaseStress(StressSession session, int requirementIndex) {
        Contribution owned = contributions.get(session, requirementIndex);
        return owned == null ? 0D : owned.baseStress();
    }

    @Override
    public double ownedActualStress(StressSession session, int requirementIndex) {
        Contribution owned = contributions.get(session, requirementIndex);
        return owned == null || kind.ioType() != IOType.INPUT ? 0D
                : ((float) baseStress() - (float) (baseStress() - owned.baseStress()))
                * (double) Math.abs(entity.getTheoreticalSpeed());
    }

    @Override
    public CapabilityResult apply(StressSession session, int requirementIndex, double baseStress, double generatedRpm) {
        checkThread();
        if (session == null || controllerPos == null) return failure(CreateFailureReasons.MISSING_RECIPE_SCOPE, requirementIndex);
        if (entity instanceof StressInputBlockEntity input) input.settleSavedNetwork();
        else if (entity instanceof StressOutputBlockEntity output) output.settleSavedNetwork();
        boolean preflighted = session.committing(requirementIndex);
        if (!serverEntity()) return failure(CreateFailureReasons.MISSING_ROTATION, requirementIndex);
        if (kind.ioType() == IOType.INPUT) {
            if (networkIdentity() == null || entity.getTheoreticalSpeed() == 0F) {
                return failure(CreateFailureReasons.MISSING_ROTATION, requirementIndex);
            }
            if (!preflighted && entity.isOverStressed()) return failure(CreateFailureReasons.INSUFFICIENT_STRESS, requirementIndex);
        }
        if (kind.ioType() == IOType.INPUT && generatedRpm != 0D) {
            return failure(CreateFailureReasons.INVALID_OUTPUT_RPM, requirementIndex);
        }
        if (kind.ioType() == IOType.OUTPUT) {
            if (!validRpm(generatedRpm)) return failure(CreateFailureReasons.INVALID_OUTPUT_RPM, requirementIndex);
            if (!acceptsGeneratedRpm(session, requirementIndex, generatedRpm)) {
                return failure(CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM, requirementIndex);
            }
        }
        Contribution previous = contributions.get(session, requirementIndex);
        double nextBase = contributions.baseStress() - (previous == null ? 0D : previous.baseStress()) + baseStress;
        double rpm = kind.ioType() == IOType.INPUT ? Math.abs(entity.getTheoreticalSpeed()) : Math.abs(generatedRpm);
        if (!positiveFloat(baseStress) || !positiveFloat(nextBase) || !positiveFloat((float) nextBase * (float) rpm)) {
            return failure(CreateFailureReasons.INSUFFICIENT_STRESS, requirementIndex);
        }
        if (kind.ioType() == IOType.INPUT) {
            // Aggregate-preflighted relocations may temporarily retain load on old ports.
            // Standalone applications still recheck this port's delta against the live budget.
            double delta = ((float) nextBase - (float) contributions.baseStress()) * rpm;
            if (stressEnabled() && (!Double.isFinite(capacity.getAsDouble()) || !Double.isFinite(stress.getAsDouble())
                    || !preflighted && delta > capacity.getAsDouble() - stress.getAsDouble())) {
                return failure(CreateFailureReasons.INSUFFICIENT_STRESS, requirementIndex);
            }
        } else if (networkIdentity() != null) {
            double oldCapacity = (float) baseStress() * Math.abs((float) generatedRpm());
            double nextCapacity = (float) nextBase * Math.abs((float) generatedRpm);
            double networkCapacity = capacity.getAsDouble() - oldCapacity + nextCapacity;
            if (!Double.isFinite(networkCapacity) || !Float.isFinite((float) networkCapacity)) {
                return failure(CreateFailureReasons.INSUFFICIENT_STRESS, requirementIndex);
            }
        }
        double beforeBase = baseStress();
        double beforeRpm = generatedRpm();
        contributions.replace(session, requirementIndex, new Contribution(baseStress, generatedRpm));
        owners.computeIfAbsent(session, ignored -> new HashSet<>()).add(requirementIndex);
        session.track(this, requirementIndex, kind.ioType() == IOType.INPUT
                ? RecipeModifier.IOType.INPUT : RecipeModifier.IOType.OUTPUT);
        changed(beforeBase, beforeRpm);
        // Native source activation can merge networks. Roll back this owner/index if the
        // resulting network is no longer representable by Create's float bookkeeping.
        if (!Double.isFinite(capacity.getAsDouble()) || !Double.isFinite(stress.getAsDouble())) {
            release(session, requirementIndex);
            return failure(CreateFailureReasons.INSUFFICIENT_STRESS, requirementIndex);
        }
        return CapabilityResult.successful();
    }

    private static boolean positiveFloat(double value) {
        return Double.isFinite(value) && value > 0D && Float.isFinite((float) value) && (float) value > 0F;
    }

    @Override
    public void release(StressSession session) {
        checkThread();
        double base = baseStress();
        double rpm = generatedRpm();
        contributions.release(session);
        owners.remove(session);
        changed(base, rpm);
    }

    @Override
    public void release(StressSession session, int requirementIndex) {
        checkThread();
        double base = baseStress();
        double rpm = generatedRpm();
        contributions.release(session, requirementIndex);
        Set<Integer> indexes = owners.get(session);
        if (indexes != null) {
            indexes.remove(requirementIndex);
            if (indexes.isEmpty()) owners.remove(session);
        }
        changed(base, rpm);
    }

    public void clear() {
        checkThread();
        double base = baseStress();
        double rpm = generatedRpm();
        for (StressSession owner : owners.keySet()) contributions.release(owner);
        owners.clear();
        changed(base, rpm);
    }

    private void changed(double beforeBase, double beforeRpm) {
        if (beforeBase == baseStress() && beforeRpm == generatedRpm()) return;
        onChanged.run();
        notifyStateTransition();
    }

    public void bind(BlockPos controllerPos) {
        if (this.controllerPos != null && !this.controllerPos.equals(controllerPos)) clear();
        this.controllerPos = controllerPos.immutable();
        notifyStateTransition();
        refreshAppearance();
    }

    public void unbind(BlockPos controllerPos) {
        if (!controllerPos.equals(this.controllerPos)) return;
        clear();
        this.controllerPos = null;
        notifiedState = null;
        notifiedNetwork = null;
        refreshAppearance();
    }

    private void refreshAppearance() {
        if (serverEntity()) entity.getLevel().sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(),
                entity.getBlockState(), 3);
    }

    public void notifyStateTransition() {
        if (controllerPos == null || !serverEntity()) return;
        StressState current = state();
        Object network = networkIdentity();
        if (current.equals(notifiedState) && network == notifiedNetwork) return;
        notifiedState = current;
        notifiedNetwork = network;
        if (entity.getLevel().hasChunkAt(controllerPos)
                && entity.getLevel().getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller
                && !controller.isRemoved()) {
            controller.resourceAvailabilityNotifier().notifyAvailability(ResourceAvailabilityNotifier.Reason.ENERGY_AVAILABLE, type());
        }
    }

    private boolean serverEntity() {
        return entity.getLevel() != null && !entity.getLevel().isClientSide() && !entity.isRemoved()
                && !entity.isChunkUnloaded();
    }

    private void checkThread() {
        if (entity.getLevel() != null && entity.getLevel().getServer() != null
                && !entity.getLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Create stress contributions require the server thread");
        }
    }

    private static CapabilityResult failure(FailureReason reason, Integer index) {
        return CapabilityResult.failure(ExecutionStatus.blocked(StressInterfaceKind.TYPE.id(), StressInterfaceKind.TYPE.id(),
                FailureOccurrence.at(reason, StressInterfaceKind.TYPE.id(), FailurePhase.CAPABILITY_COMMIT,
                        null, index, Map.of())));
    }
}
