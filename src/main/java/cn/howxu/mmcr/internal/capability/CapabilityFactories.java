package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Built-in capability factories and shared capability contract helpers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityFactories {
    public static final CapabilityFactory ITEM_BUS = port -> new ItemBusCapability(require(port, ItemBusBlockEntity.class));
    public static final CapabilityFactory FLUID_HATCH = port -> new FluidHatchCapability(require(port, FluidHatchBlockEntity.class));
    public static final CapabilityFactory ENERGY_HATCH = port -> new EnergyHatchCapability(require(port, EnergyHatchBlockEntity.class));

    static final CapabilityType ITEM_TYPE = new CapabilityType(MMCR.id("item"));
    static final CapabilityType FLUID_TYPE = new CapabilityType(MMCR.id("fluid"));
    static final CapabilityType ENERGY_TYPE = new CapabilityType(MMCR.id("energy"));

    private CapabilityFactories() {}

    public interface CapabilityFactory {
        MachineCapability create(IOPortBlockEntity port);
    }

    static CapabilityView view(CapabilityType type, IOType ioType) {
        return new CapabilityView() {
            @Override
            public CapabilityType type() {
                return type;
            }

            @Override
            public IOType ioType() {
                return ioType;
            }
        };
    }

    static CapabilityOperation operation(CapabilityType type, IOType ioType, CapabilityRequest request) {
        return operation(type, ioType, request, null);
    }

    static CapabilityOperation operation(CapabilityType type, IOType ioType, CapabilityRequest request,
                                         CapabilityStorage storage) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (!type.equals(request.type())) throw new IllegalArgumentException("Capability request type does not match");
        if (ioType != request.ioType()) throw new IllegalArgumentException("Capability request IO type does not match");
        if (request.parallelism() <= 0) throw new IllegalArgumentException("parallelism must be positive");
        if (request instanceof CapabilityRequests.ResourceRequest<?> resourceRequest
                && storage instanceof ResourceStorage<?> resourceStorage) {
            return resourceOperation(resourceStorage, resourceRequest);
        }
        if (request instanceof CapabilityRequests.ValueRequest valueRequest
                && storage instanceof LongValueStorage valueStorage) {
            return valueOperation(valueStorage, valueRequest);
        }
        if (request instanceof CapabilityRequests.SmartValueRequest smartRequest
                && storage instanceof FloatValueStorage valueStorage) {
            return transaction -> valueStorage.set(smartRequest.interfaceType(), smartRequest.value(), transaction)
                    ? CapabilityResult.successful() : CapabilityResult.failure(failure(type, "smart_value"));
        }
        return transaction -> CapabilityResult.failure(failure(type, "unsupported_request"));
    }

    private static CapabilityOperation resourceOperation(ResourceStorage<?> storage,
                                                         CapabilityRequests.ResourceRequest<?> request) {
        return transaction -> {
            for (CapabilityRequests.ResourceAction<?> action : request.actions()) {
                if (!storage.resourceType().isInstance(action.resource())) {
                    return CapabilityResult.failure(failure(request.type(), "wrong_resource_type"));
                }
                long moved = action.insert()
                        ? storage.insertResource(action.slot(), action.resource(), action.amount(), transaction)
                        : storage.extractResource(action.slot(), action.resource(), action.amount(), transaction);
                if (moved != action.amount()) return CapabilityResult.failure(failure(request.type(), "insufficient_resource"));
            }
            return CapabilityResult.successful();
        };
    }

    private static CapabilityOperation valueOperation(LongValueStorage storage, CapabilityRequests.ValueRequest request) {
        return transaction -> {
            storage.updateSnapshots(transaction);
            long moved = request.insert()
                    ? storage.insert(request.amount(), false)
                    : storage.extract(request.amount(), false);
            return moved == request.amount()
                    ? CapabilityResult.successful()
                    : CapabilityResult.failure(failure(request.type(), "insufficient_value"));
        };
    }

    private static cn.howxu.mmcr.api.capability.status.ExecutionStatus failure(CapabilityType type, String reason) {
        return new cn.howxu.mmcr.api.capability.status.ExecutionStatus(type.id(),
                cn.howxu.mmcr.api.capability.status.StatusSeverity.BLOCKED, type.id(), java.util.Map.of("reason", reason));
    }

    private static <T> T require(IOPortBlockEntity port, Class<T> type) {
        if (!type.isInstance(port)) {
            throw new IllegalArgumentException("Capability factory cannot handle " + port.getClass().getName());
        }
        return type.cast(port);
    }
}
