package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.NativeCapabilityOperation;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.mekanism.HeatViewFacet;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** MMCR capability backed by a loaded Mekanism heat capacitor.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class HeatPortCapability implements LoadedMekanismBridge.HeatPort,
        HeatViewFacet, OperationFacet, PresentationFacet, SyncFacet {
    private static final CapabilityType TYPE = new CapabilityType(MekanismRecipeTypes.HEAT);

    private final IHeatCapacitor heatCapacitor;
    private final IOType ioType;
    private final CapabilityView view;
    private final AsyncPlanningFacet asyncPlanning;

    public HeatPortCapability(IHeatCapacitor heatCapacitor, IOType ioType) {
        if (heatCapacitor == null) throw new IllegalArgumentException("heatCapacitor must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.heatCapacitor = heatCapacitor;
        this.ioType = ioType;
        this.asyncPlanning = new AsyncPlanningFacet() {
            @Override
            public Object planningIdentity() {
                return heatCapacitor;
            }

            @Override
            protected AsyncCapabilitySnapshot captureSnapshotOnServerThread() {
                return new AsyncCapabilitySnapshot.Heat(type().id(), heatCapacitor.getHeat(),
                        heatCapacitor.getTemperature(), heatCapacitor.getHeatCapacity());
            }

            @Override
            protected AsyncCapabilityPlanner workerPlannerOnServerThread() {
                return new AsyncCapabilityPlanner.Heat(type().id());
            }

            @Override
            protected CapabilityResult commitNativeOnServerThread(AsyncCapabilityOperation operation) {
                if (!(operation instanceof AsyncCapabilityOperation.Heat heat)) {
                    return failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
                }
                if (heat.minimumTemperature()) {
                    return heatCapacitor.getTemperature() >= heat.value()
                            ? CapabilityResult.successful()
                            : failure(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);
                }
                try {
                    heatCapacitor.handleHeat(heat.value());
                    return CapabilityResult.successful();
                } catch (RuntimeException exception) {
                    return failure(MekanismFailureReasons.HEAT_OUTPUT_BLOCKED);
                }
            }
        };
        this.view = CapabilityFactories.view(TYPE, directions(),
                Set.of(HeatViewFacet.class, OperationFacet.class, PresentationFacet.class, SyncFacet.class,
                        AsyncPlanningFacet.class));
    }

    public HeatPortCapability(HeatPortBlockEntity port) {
        this(port.heatCapacitor(), port.ioType());
    }

    @Override
    public IHeatHandler heatHandler() {
        return heatCapacitor;
    }

    @Override
    public double heat() {
        return heatCapacitor.getHeat();
    }

    @Override
    public double temperature() {
        return heatCapacitor.getTemperature();
    }

    @Override
    public double heatCapacity() {
        return heatCapacitor.getHeatCapacity();
    }

    @Override
    public CapabilityType type() {
        return TYPE;
    }

    @Override
    public CapabilityDirections directions() {
        return CapabilityDirections.of(ioType);
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public <F extends cn.howxu.mmcr.api.capability.facet.CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        if (facetType == AsyncPlanningFacet.class) return Optional.of(facetType.cast(asyncPlanning));
        return LoadedMekanismBridge.HeatPort.super.facet(facetType);
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(this, request);
    }

    @Override
    public CapabilityOperation prepareOperation(CapabilityRequest request) {
        if (!(request instanceof CapabilityRequests.ValueRequest valueRequest)) {
            return (NativeCapabilityOperation) () -> failure(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        }
        return (NativeCapabilityOperation) () -> {
            double amount = valueRequest.amount();
            if (!valueRequest.insert() && heatCapacitor.getHeat() < amount) {
                double available = Math.max(0D, heatCapacitor.getHeat());
                return failure(MekanismFailureReasons.HEAT_INPUT_MISSING,
                        Map.of("required_heat", Long.toString(valueRequest.amount()),
                                "available_heat", Double.toString(available),
                                "shortfall", Double.toString(Math.max(0D, amount - available))));
            }
            try {
                heatCapacitor.handleHeat(valueRequest.insert() ? amount : -amount);
            } catch (RuntimeException exception) {
                return failure(MekanismFailureReasons.HEAT_OUTPUT_BLOCKED,
                        Map.of("requested_heat", Long.toString(valueRequest.amount())));
            }
            return CapabilityResult.successful();
        };
    }

    @Override
    public List<CapabilityDisplay> displays(CapabilityView ignored) {
        var unit = MekanismTemperatureDisplay.configuredUnit();
        return List.of(new CapabilityDisplay("heat",
                Double.toString(MekanismTemperatureDisplay.fromKelvin(heatCapacitor.getTemperature(), unit)),
                MekanismTemperatureDisplay.symbol(unit), Optional.empty()));
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(heatCapacitor.getHeat());
        buffer.writeDouble(heatCapacitor.getHeatCapacity());
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        double heat = buffer.readDouble();
        double capacity = buffer.readDouble();
        if (!Double.isFinite(heat) || heat < 0D || !Double.isFinite(capacity)
                || capacity != heatCapacitor.getHeatCapacity()) {
            throw new IllegalArgumentException("Invalid heat sync state");
        }
        heatCapacitor.setHeat(heat);
    }

    private CapabilityResult failure(FailureReason reason) {
        return failure(reason, Map.of());
    }

    private CapabilityResult failure(FailureReason reason, Map<String, String> details) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, type().id(), FailurePhase.CAPABILITY_COMMIT,
                null, null, details);
        return CapabilityResult.failure(ExecutionStatus.blocked(type().id(), type().id(), occurrence));
    }
}
