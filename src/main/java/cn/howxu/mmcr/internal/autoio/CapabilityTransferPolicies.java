package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.transfer.TransferPolicy;
import cn.howxu.mmcr.api.capability.transfer.TransferResult;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Map;
import java.util.Optional;

/**
 * Built-in automatic IO policies selected by capability identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityTransferPolicies {
    private static final Identifier ITEM_ID = MMCR.id("item");
    private static final Identifier FLUID_ID = MMCR.id("fluid");
    private static final Identifier ENERGY_ID = MMCR.id("energy");
    private static final Map<Identifier, TransferPolicy> POLICIES = Map.of(
            ITEM_ID, new ItemPolicy(),
            FLUID_ID, new FluidPolicy(),
            ENERGY_ID, new EnergyPolicy());

    private CapabilityTransferPolicies() {}

    public static Optional<TransferPolicy> policyFor(MachineCapability capability) {
        if (capability == null || capability.type() == null) return Optional.empty();
        return Optional.ofNullable(POLICIES.get(capability.type().id()));
    }

    private static TransferResult moved(long amount) {
        return new TransferResult(amount > 0L, amount, null);
    }

    private static TransferResult blocked(String reason) {
        return new TransferResult(false, 0L,
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
            if (!(capability instanceof ItemBusCapability item)) return false;
            ResourceStorage<ItemResource> storage = item.storage();
            if (item.ioType() == IOType.OUTPUT) {
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
        public TransferResult transfer(MachineCapability capability, Direction side) {
            if (!(capability instanceof ItemBusCapability item)) return blocked("unsupported_capability");
            if (!hasWork(item)) return blocked("no_work");
            ResourceHandler<ItemResource> adjacent = adjacentItem(item, side);
            if (adjacent == null) return blocked("no_target");
            ResourceHandler<ItemResource> internal = resourceHandler(item.storage());
            int limit = item.transferLimit();
            long moved = item.ioType() == IOType.INPUT
                    ? ResourceHandlerUtil.move(adjacent, internal, resource -> true, limit, null)
                    : ResourceHandlerUtil.move(internal, adjacent, resource -> true, limit, null);
            return moved(moved);
        }

        @Override
        public TransferResult eject(MachineCapability capability, Direction side) {
            if (!(capability instanceof ItemBusCapability item)) return blocked("unsupported_capability");
            if (!hasStoredContents(item)) return blocked("no_work");
            ResourceHandler<ItemResource> adjacent = adjacentItem(item, side);
            if (adjacent == null) return blocked("no_target");
            int limit = item.transferLimit();
            long moved = ResourceHandlerUtil.move(resourceHandler(item.storage()), adjacent,
                    resource -> true, limit, null);
            return moved(moved);
        }

        private static boolean hasStoredContents(ItemBusCapability item) {
            ResourceStorage<ItemResource> storage = item.storage();
            for (int slot = 0; slot < storage.size(); slot++) {
                if (storage.amount(slot) > 0L) return true;
            }
            return false;
        }

        private static ResourceHandler<ItemResource> adjacentItem(MachineCapability capability, Direction side) {
            if (!(capability instanceof ItemBusCapability item) || !canWork(item.level(), side)) return null;
            return item.level().getCapability(ModCapabilities.ITEM_BLOCK, adjacent(item.position(), side), side.getOpposite());
        }
    }

    private static final class FluidPolicy implements TransferPolicy {
        @Override
        public boolean hasWork(MachineCapability capability) {
            if (!(capability instanceof FluidHatchCapability fluid)) return false;
            ResourceStorage<FluidResource> storage = fluid.storage();
            long amount = storage.amount(0);
            FluidResource resource = storage.resource(0);
            return fluid.ioType() == IOType.OUTPUT
                    ? amount > 0L
                    : amount < storage.capacity(0, isEmpty(resource) ? null : resource);
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentFluid(capability, side) != null;
        }

        @Override
        public TransferResult transfer(MachineCapability capability, Direction side) {
            if (!(capability instanceof FluidHatchCapability fluid)) return blocked("unsupported_capability");
            if (!hasWork(fluid)) return blocked("no_work");
            ResourceHandler<FluidResource> adjacent = adjacentFluid(fluid, side);
            if (adjacent == null) return blocked("no_target");
            ResourceHandler<FluidResource> internal = resourceHandler(fluid.storage());
            int limit = fluid.transferLimit();
            long moved = fluid.ioType() == IOType.INPUT
                    ? ResourceHandlerUtil.move(adjacent, internal, resource -> true, limit, null)
                    : ResourceHandlerUtil.move(internal, adjacent, resource -> true, limit, null);
            return moved(moved);
        }

        @Override
        public TransferResult eject(MachineCapability capability, Direction side) {
            if (!(capability instanceof FluidHatchCapability fluid)) return blocked("unsupported_capability");
            if (fluid.storage().amount(0) <= 0L) return blocked("no_work");
            ResourceHandler<FluidResource> adjacent = adjacentFluid(fluid, side);
            if (adjacent == null) return blocked("no_target");
            int limit = fluid.transferLimit();
            long moved = ResourceHandlerUtil.move(resourceHandler(fluid.storage()), adjacent,
                    resource -> true, limit, null);
            return moved(moved);
        }

        private static ResourceHandler<FluidResource> adjacentFluid(MachineCapability capability, Direction side) {
            if (!(capability instanceof FluidHatchCapability fluid) || !canWork(fluid.level(), side)) return null;
            return fluid.level().getCapability(ModCapabilities.FLUID_BLOCK, adjacent(fluid.position(), side), side.getOpposite());
        }
    }

    private static final class EnergyPolicy implements TransferPolicy {
        @Override
        public boolean hasWork(MachineCapability capability) {
            if (!(capability instanceof EnergyHatchCapability energy)) return false;
            LongValueStorage storage = energy.storage();
            return energy.ioType() == IOType.OUTPUT
                    ? storage.amount() > 0L
                    : storage.amount() < storage.capacity();
        }

        @Override
        public boolean hasAdjacentTarget(MachineCapability capability, Direction side) {
            return adjacentEnergy(capability, side) != null;
        }

        @Override
        public TransferResult transfer(MachineCapability capability, Direction side) {
            if (!(capability instanceof EnergyHatchCapability energy)) return blocked("unsupported_capability");
            if (!hasWork(energy)) return blocked("no_work");
            EnergyHandler adjacent = adjacentEnergy(energy, side);
            if (adjacent == null) return blocked("no_target");
            EnergyHandler internal = energyHandler(energy.storage());
            int limit = (int) Math.min(energy.storage().transferLimit(), Integer.MAX_VALUE);
            long moved = energy.ioType() == IOType.INPUT
                    ? EnergyHandlerUtil.move(adjacent, internal, limit, null)
                    : EnergyHandlerUtil.move(internal, adjacent, limit, null);
            return moved(moved);
        }

        @Override
        public TransferResult eject(MachineCapability capability, Direction side) {
            if (!(capability instanceof EnergyHatchCapability energy)) return blocked("unsupported_capability");
            if (energy.storage().amount() <= 0L) return blocked("no_work");
            EnergyHandler adjacent = adjacentEnergy(energy, side);
            if (adjacent == null) return blocked("no_target");
            int limit = (int) Math.min(energy.storage().transferLimit(), Integer.MAX_VALUE);
            long moved = EnergyHandlerUtil.move(energyHandler(energy.storage()), adjacent, limit, null);
            return moved(moved);
        }

        private static EnergyHandler adjacentEnergy(MachineCapability capability, Direction side) {
            if (!(capability instanceof EnergyHatchCapability energy) || !canWork(energy.level(), side)) return null;
            return energy.level().getCapability(ModCapabilities.ENERGY_BLOCK, adjacent(energy.position(), side), side.getOpposite());
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

    private static EnergyHandler energyHandler(LongValueStorage storage) {
        return new EnergyHandler() {
            @Override public long getAmountAsLong() { return storage.amount(); }
            @Override public long getCapacityAsLong() { return storage.capacity(); }
            @Override public int insert(int amount, TransactionContext transaction) {
                storage.updateSnapshots(transaction);
                return (int) storage.insert(amount, false);
            }
            @Override public int extract(int amount, TransactionContext transaction) {
                storage.updateSnapshots(transaction);
                return (int) storage.extract(amount, false);
            }
        };
    }
}
