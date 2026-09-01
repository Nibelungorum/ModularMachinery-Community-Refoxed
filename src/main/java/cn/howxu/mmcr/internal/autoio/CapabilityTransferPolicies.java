package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.transfer.TransferContext;
import cn.howxu.mmcr.api.capability.transfer.TransferPolicy;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.api.capability.transfer.TransferStrategyRegistry;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Map;
import java.util.Optional;

/**
 * Built-in automatic IO policies registered by capability identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityTransferPolicies {
    static {
        TransferStrategyRegistry.register(BuiltinCapabilityDefinitions.ITEM_TYPE, new ItemPolicy());
        TransferStrategyRegistry.register(BuiltinCapabilityDefinitions.FLUID_TYPE, new FluidPolicy());
        TransferStrategyRegistry.register(BuiltinCapabilityDefinitions.ENERGY_TYPE, new EnergyPolicy());
    }

    private CapabilityTransferPolicies() {
    }

    /**
     * Forces class initialization before generic callers query the registry.
     */
    public static void ensureRegistered() {
    }

    public static Optional<TransferPolicy> policyFor(MachineCapability capability) {
        if (capability == null || capability.type() == null) return Optional.empty();
        return TransferStrategyRegistry.policyFor(capability.type());
    }

    private static TransferResult blocked(String reason) {
        return TransferResult.blocked(
                new ExecutionStatus(MMCR.id("auto_io"), StatusSeverity.BLOCKED,
                        MMCR.id("auto_io"), Map.of("reason", reason)));
    }

    private static boolean canWork(Level level, Direction side) {
        return level != null && !level.isClientSide() && side != null;
    }

    private static BlockPos adjacent(BlockPos position, Direction side) {
        return position.relative(side);
    }

    private static final class ItemPolicy implements TransferPolicy {
        @Override
        public boolean hasWork(MachineCapability capability) {
            ResourceStorage<ItemResource> storage = CapabilityFactories.resourceStorage(capability, ItemResource.class);
            if (storage == null) return false;
            if (capability.ioType() == IOType.OUTPUT) {
                for (int slot = 0; slot < storage.size(); slot++) {
                    if (storage.amount(slot) > 0L) return true;
                }
                return false;
            }
            for (int slot = 0; slot < storage.size(); slot++) {
                ItemResource resource = storage.resource(slot);
                if (storage.amount(slot) < storage.capacity(slot, isEmpty(resource) ? null : resource)) return true;
            }
            return false;
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentItem(capability, side) != null;
        }

        @Override
        public TransferResult transfer(TransferContext context) {
            MachineCapability capability = context.capability();
            ResourceStorage<ItemResource> storage = CapabilityFactories.resourceStorage(capability, ItemResource.class);
            TransferFacet transfer = transferFacet(capability);
            if (storage == null || transfer == null) return blocked("unsupported_capability");
            if (context.eject() ? !hasStoredContents(storage) : !hasWork(capability)) return blocked("no_work");
            ResourceHandler<ItemResource> adjacent = adjacentItem(capability, context.side());
            if (adjacent == null) return blocked("no_target");
            ResourceHandler<ItemResource> internal = resourceHandler(storage);
            int limit = (int) Math.min(transfer.transferLimit(), Integer.MAX_VALUE);
            long moved = context.eject()
                    ? moveResource(internal, adjacent, limit, context)
                    : capability.ioType() == IOType.INPUT
                    ? moveResource(adjacent, internal, limit, context)
                    : moveResource(internal, adjacent, limit, context);
            return TransferResult.moved(moved);
        }

        private static boolean hasStoredContents(ResourceStorage<ItemResource> storage) {
            for (int slot = 0; slot < storage.size(); slot++) {
                if (storage.amount(slot) > 0L) return true;
            }
            return false;
        }

        private static ResourceHandler<ItemResource> adjacentItem(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || !canWork(transfer.level(), side)) return null;
            return transfer.level().getCapability(ModCapabilities.ITEM_BLOCK,
                    adjacent(transfer.position(), side), side.getOpposite());
        }
    }

    private static final class FluidPolicy implements TransferPolicy {
        @Override
        public boolean hasWork(MachineCapability capability) {
            ResourceStorage<FluidResource> storage = CapabilityFactories.resourceStorage(capability, FluidResource.class);
            if (storage == null) return false;
            if (capability.ioType() == IOType.OUTPUT) return hasStoredContents(storage);
            for (int slot = 0; slot < storage.size(); slot++) {
                FluidResource resource = storage.resource(slot);
                if (storage.amount(slot) < storage.capacity(slot, isEmpty(resource) ? null : resource)) return true;
            }
            return false;
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentFluid(capability, side) != null;
        }

        @Override
        public TransferResult transfer(TransferContext context) {
            MachineCapability capability = context.capability();
            ResourceStorage<FluidResource> storage = CapabilityFactories.resourceStorage(capability, FluidResource.class);
            TransferFacet transfer = transferFacet(capability);
            if (storage == null || transfer == null) return blocked("unsupported_capability");
            if (context.eject() ? !hasStoredContents(storage) : !hasWork(capability)) return blocked("no_work");
            ResourceHandler<FluidResource> adjacent = adjacentFluid(capability, context.side());
            if (adjacent == null) return blocked("no_target");
            ResourceHandler<FluidResource> internal = resourceHandler(storage);
            int limit = (int) Math.min(transfer.transferLimit(), Integer.MAX_VALUE);
            long moved = context.eject()
                    ? moveResource(internal, adjacent, limit, context)
                    : capability.ioType() == IOType.INPUT
                    ? moveResource(adjacent, internal, limit, context)
                    : moveResource(internal, adjacent, limit, context);
            return TransferResult.moved(moved);
        }

        private static boolean hasStoredContents(ResourceStorage<FluidResource> storage) {
            for (int slot = 0; slot < storage.size(); slot++) {
                if (storage.amount(slot) > 0L) return true;
            }
            return false;
        }

        private static ResourceHandler<FluidResource> adjacentFluid(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || !canWork(transfer.level(), side)) return null;
            return transfer.level().getCapability(ModCapabilities.FLUID_BLOCK,
                    adjacent(transfer.position(), side), side.getOpposite());
        }
    }

    private static final class EnergyPolicy implements TransferPolicy {
        @Override
        public boolean hasWork(MachineCapability capability) {
            LongValueStorage storage = CapabilityFactories.valueStorage(capability, LongValueStorage.class);
            if (storage == null) return false;
            return capability.ioType() == IOType.OUTPUT
                    ? storage.amount() > 0L
                    : storage.amount() < storage.capacity();
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentEnergy(capability, side) != null;
        }

        @Override
        public TransferResult transfer(TransferContext context) {
            MachineCapability capability = context.capability();
            LongValueStorage storage = CapabilityFactories.valueStorage(capability, LongValueStorage.class);
            TransferFacet transfer = transferFacet(capability);
            if (storage == null || transfer == null) return blocked("unsupported_capability");
            if (context.eject() ? storage.amount() <= 0L : !hasWork(capability)) return blocked("no_work");
            EnergyHandler adjacent = adjacentEnergy(capability, context.side());
            if (adjacent == null) return blocked("no_target");
            EnergyHandler internal = energyHandler(storage, transfer.transferLimit());
            long limit = transfer.transferLimit();
            long moved = context.eject()
                    ? moveEnergy(internal, adjacent, limit, context)
                    : capability.ioType() == IOType.INPUT
                    ? moveEnergy(adjacent, internal, limit, context)
                    : moveEnergy(internal, adjacent, limit, context);
            return TransferResult.moved(moved);
        }

        private static EnergyHandler adjacentEnergy(MachineCapability capability, Direction side) {
            TransferFacet transfer = transferFacet(capability);
            if (transfer == null || !canWork(transfer.level(), side)) return null;
            return transfer.level().getCapability(ModCapabilities.ENERGY_BLOCK,
                    adjacent(transfer.position(), side), side.getOpposite());
        }
    }

    private static TransferFacet transferFacet(MachineCapability capability) {
        return capability == null ? null : capability.facet(TransferFacet.class).orElse(null);
    }

    private static <R extends Resource> long moveResource(ResourceHandler<R> from, ResourceHandler<R> to,
                                                          int limit, TransferContext context) {
        if (!context.simulate()) {
            return ResourceHandlerUtil.move(from, to, resource -> true, limit, context.transaction());
        }
        try (Transaction transaction = Transaction.open(context.transaction())) {
            return ResourceHandlerUtil.move(from, to, resource -> true, limit, transaction);
        }
    }

    private static <R extends Resource> ResourceHandler<R> resourceHandler(ResourceStorage<R> storage) {
        return new ResourceHandler<>() {
            @Override public int size() { return storage.size(); }
            @Override public R getResource(int slot) { return resourceOrEmpty(storage, slot); }
            @Override public long getAmountAsLong(int slot) { return storage.amount(slot); }
            @Override public long getCapacityAsLong(int slot, R resource) { return storage.capacity(slot, resource); }
            @Override public boolean isValid(int slot, R resource) { return storage.isValid(slot, resource); }
            @Override public int insert(int slot, R resource, int amount, TransactionContext transaction) {
                return (int) storage.insert(slot, resource, amount, transaction);
            }
            @Override public int extract(int slot, R resource, int amount, TransactionContext transaction) {
                return (int) storage.extract(slot, resource, amount, transaction);
            }
        };
    }

    private static boolean isEmpty(Resource resource) {
        return resource == null || resource.isEmpty();
    }

    private static <R extends Resource> R resourceOrEmpty(ResourceStorage<R> storage, int slot) {
        R resource = storage.resource(slot);
        return resource == null ? emptyResource(storage.resourceType()) : resource;
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource> R emptyResource(Class<R> resourceType) {
        // NeoForge ResourceHandler requires a concrete empty resource, unlike ResourceStorage.
        if (resourceType == ItemResource.class) return (R) ItemResource.EMPTY;
        if (resourceType == FluidResource.class) return (R) FluidResource.EMPTY;
        throw new IllegalArgumentException("Missing empty resource for " + resourceType.getName());
    }

    private static EnergyHandler energyHandler(LongValueStorage storage, long transferLimit) {
        return new LongEnergyHandler() {
            @Override public long getAmountAsLong() { return storage.amount(); }
            @Override public long getCapacityAsLong() { return storage.capacity(); }
            @Override public long getTransferLimit() { return transferLimit; }
            @Override public int insert(int amount, TransactionContext transaction) {
                return (int) storage.insert(amount, transaction);
            }
            @Override public int extract(int amount, TransactionContext transaction) {
                return (int) storage.extract(amount, transaction);
            }
            @Override public long insertLong(long amount, TransactionContext transaction) {
                if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
                return storage.insert(amount, transaction);
            }
            @Override public long extractLong(long amount, TransactionContext transaction) {
                if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
                return storage.extract(amount, transaction);
            }
        };
    }

    private static long moveEnergy(EnergyHandler from, EnergyHandler to, long requested, TransferContext context) {
        if (requested <= 0L) return 0L;
        if (from instanceof LongEnergyHandler longFrom && to instanceof LongEnergyHandler longTo) {
            return moveLongEnergy(longFrom, longTo, requested, context);
        }
        // EnergyHandler exposes int-sized transfers; continue long-backed transfers on later ticks.
        long boundedRequest = Math.min(requested, Integer.MAX_VALUE);
        if (!context.simulate()) {
            return moveIntEnergy(from, to, boundedRequest, context.transaction());
        }
        try (Transaction transaction = Transaction.open(context.transaction())) {
            return moveIntEnergy(from, to, boundedRequest, transaction);
        }
    }

    private static long moveIntEnergy(EnergyHandler from, EnergyHandler to, long requested,
                                      TransactionContext transaction) {
        long moved = 0L;
        while (moved < requested) {
            int chunk = (int) (requested - moved);
            int chunkMoved = EnergyHandlerUtil.move(from, to, chunk, transaction);
            if (chunkMoved <= 0) break;
            moved += chunkMoved;
            if (chunkMoved < chunk) break;
        }
        return moved;
    }

    private static long moveLongEnergy(LongEnergyHandler from, LongEnergyHandler to, long requested,
                                       TransferContext context) {
        long targetSpace = to.getAmountAsLong() >= to.getCapacityAsLong()
                ? 0L
                : to.getCapacityAsLong() - to.getAmountAsLong();
        long amount = Math.min(requested, Math.min(from.getAmountAsLong(), targetSpace));
        amount = Math.min(amount, Math.min(from.getTransferLimit(), to.getTransferLimit()));
        if (amount <= 0L) return 0L;

        try (Transaction transaction = Transaction.open(context.transaction())) {
            long extracted = from.extractLong(amount, transaction);
            if (extracted != amount) return 0L;
            long inserted = to.insertLong(extracted, transaction);
            if (inserted != extracted) return 0L;
            if (!context.simulate()) transaction.commit();
            return inserted;
        }
    }
}
