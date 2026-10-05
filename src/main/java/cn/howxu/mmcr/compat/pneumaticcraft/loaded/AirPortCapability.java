package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.compat.pneumaticcraft.AirFailureReasons;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Validates recipe mutations without restricting native signed/overpressure states. @author howxu <dev@howxu.cn> */
public final class AirPortCapability implements MachineCapability, PneumaticAirFacet, PresentationFacet, SyncFacet {
    private final AirPortBlockEntity host;
    private final CapabilityView view;

    public AirPortCapability(AirPortBlockEntity host) {
        this.host = host;
        view = CapabilityFactories.view(type(), directions(), Set.of(PneumaticAirFacet.class, PresentationFacet.class, SyncFacet.class));
    }

    @Override public CapabilityType type() { return AirInterfaceKind.TYPE; }
    @Override public CapabilityDirections directions() { return CapabilityDirections.of(host.ioType()); }
    @Override public CapabilityView view() { return view; }
    @Override public Object queryIdentity() { return host.airHandler(); }
    @Override public AirState state() {
        IAirHandlerMachine handler = host.airHandler();
        return new AirState(host.getBlockPos(), handler.getAir(), handler.getVolume(),
                handler.getDangerPressure(), handler.getCriticalPressure());
    }

    @Override
    public CapabilityResult validate(long amount, boolean insert, float minPressure) {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || host.isRemoved() || !level.hasChunkAt(host.getBlockPos())
                || level.getBlockEntity(host.getBlockPos()) != host) return failure(AirFailureReasons.UNAVAILABLE);
        if (insert != (host.ioType() == IOType.OUTPUT)) return failure(AirFailureReasons.MISSING_INTERFACE);
        if (amount < 0L || amount > Integer.MAX_VALUE || !Float.isFinite(minPressure) || minPressure < 0F
                || insert && minPressure != 0F) return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        AirState state = state();
        if (!insert && state.pressure() < minPressure) return failure(AirFailureReasons.INSUFFICIENT_PRESSURE);
        if (!insert && amount > Math.max(0L, state.air())) return failure(AirFailureReasons.INSUFFICIENT_AIR);
        long after = (long) state.air() + (insert ? amount : -amount);
        if (insert && (amount > state.outputCapacity() || after > Integer.MAX_VALUE
                || amount > 0L && after < -state.volume())) return failure(AirFailureReasons.OUTPUT_BLOCKED);
        return CapabilityResult.successful();
    }

    @Override
    public CapabilityResult apply(long amount, boolean insert, float minPressure) {
        CapabilityResult result = validate(amount, insert, minPressure);
        if (!result.success()) return result;
        if (amount != 0L) host.airHandler().addAir(insert ? (int) amount : -(int) amount);
        host.observeAirChanges();
        return CapabilityResult.successful();
    }

    @Override public CapabilityOperation prepare(CapabilityRequest request) {
        return () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
    }

    @Override public List<CapabilityDisplay> displays(CapabilityView ignored) {
        return List.of(new CapabilityDisplay(PneumaticIds.AIR.toString(), Integer.toString(state().air()), "", Optional.empty()));
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        AirState state = state();
        buffer.writeInt(state.air());
        buffer.writeInt(state.volume());
        buffer.writeFloat(state.dangerPressure());
        buffer.writeFloat(state.criticalPressure());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        int air = buffer.readInt();
        int volume = buffer.readInt();
        float danger = buffer.readFloat();
        float critical = buffer.readFloat();
        IAirHandlerMachine handler = host.airHandler();
        host.validateClientAirState(volume, danger, critical);
        // Native addAir floors vacuum and may overflow an int delta. Network state is not a recipe mutation.
        handler.setBaseVolume(volume);
        CompoundTag nativeState = ((CompoundTag) handler.serializeNBT()).copy();
        nativeState.putInt("Air", air);
        handler.deserializeNBT(nativeState);
    }

    private CapabilityResult failure(FailureReason reason) {
        return CapabilityResult.failure(ExecutionStatus.blocked(PneumaticIds.AIR, PneumaticIds.AIR,
                FailureOccurrence.at(reason, PneumaticIds.AIR, FailurePhase.CAPABILITY_COMMIT,
                        null, null, Map.of())));
    }
}
