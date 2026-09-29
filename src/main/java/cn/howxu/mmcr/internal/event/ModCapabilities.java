package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityContext;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityRegistry;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedflux.AppliedFluxBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class ModCapabilities {
    public static final BlockCapability<IItemHandler, Direction> ITEM_BLOCK = Capabilities.ItemHandler.BLOCK;
    public static final BlockCapability<IFluidHandler, Direction> FLUID_BLOCK = Capabilities.FluidHandler.BLOCK;
    public static final BlockCapability<IEnergyStorage, Direction> ENERGY_BLOCK = Capabilities.EnergyStorage.BLOCK;

    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        ExternalCapabilityContext context = new ExternalCapabilityContext();
        bindNativeExposures(context);
        ExternalCapabilityRegistry.global().freeze(context);
        for (IOPortKind kind : nativeCapabilityPorts()) {
            registerNativePort(event, kind, context);
        }
        MekanismBridge.get().registerCapabilities(event);
        AE2Bridge.get().registerCapabilities(event);
        AppliedFluxBridge.get().registerCapabilities(event);
        event.registerBlockEntity(ITEM_BLOCK, ModBlockEntities.BES.get("factory_controller").get(),
                (be, side) -> be instanceof FactorySchedulerBlockEntity scheduler ? scheduler.itemHandler() : null);
    }

    static Set<String> nativeCapabilityPortIds() {
        Set<String> ids = new LinkedHashSet<>();
        nativeCapabilityPorts().forEach(kind -> ids.add(kind.id()));
        return Set.copyOf(ids);
    }

    static Set<String> nativeCapabilityBlockEntityIds() {
        Set<String> ids = new LinkedHashSet<>(nativeCapabilityPortIds());
        ids.add("factory_controller");
        return Set.copyOf(ids);
    }

    private static List<IOPortKind> nativeCapabilityPorts() {
        return PortKinds.all().stream().filter(kind -> !kind.definition().bindings().isEmpty()).toList();
    }

    private static void registerNativePort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                           ExternalCapabilityContext context) {
        List<CapabilityBinding> externalBindings = externalBindings(kind);
        Set<CapabilityType> externallyExposed = externalBindings.stream()
                .filter(binding -> !context.bindings(binding.type()).isEmpty())
                .map(CapabilityBinding::type)
                .collect(Collectors.toSet());
        for (CapabilityBinding binding : externalBindings) {
            context.bindings(binding.type()).forEach(exposure -> registerExternalPort(event, kind, binding, exposure));
        }
        List<CapabilityBinding> nativeBindings = nativeTransferBindings(kind, externallyExposed);
        if (!nativeBindings.isEmpty()) registerFacetProviders(event, kind, nativeBindings);
    }

    static List<CapabilityBinding> nativeTransferBindings(IOPortKind kind, Set<CapabilityType> externallyExposed) {
        if (kind == null) return List.of();
        Set<CapabilityType> exposed = externallyExposed == null ? Set.of() : externallyExposed;
        return kind.definition().bindings().stream()
                .filter(CapabilityBinding::nativeTransferExposure)
                .filter(binding -> !exposed.contains(binding.type()))
                .toList();
    }

    static List<CapabilityBinding> externalBindings(IOPortKind kind) {
        if (kind == null) return List.of();
        return kind.definition().bindings().stream()
                .filter(CapabilityBinding::nativeTransferExposure)
                .filter(binding -> binding.externalExposure().isPresent())
                .toList();
    }

    private static void bindNativeExposures(ExternalCapabilityContext context) {
        PortKinds.all().forEach(kind -> kind.definition().bindings().stream()
                .filter(CapabilityBinding::nativeTransferExposure)
                .forEach(binding -> binding.externalExposure()
                        .ifPresent(exposure -> bindExposure(context, binding.type(), exposure))));
    }

    private static <T> void bindExposure(ExternalCapabilityContext context, CapabilityType type,
                                         CapabilityBinding.ExternalExposure<T> exposure) {
        context.bind(type, exposure);
    }

    private static <T> void registerExternalPort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                                  CapabilityBinding binding,
                                                  CapabilityBinding.ExternalExposure<T> exposure) {
        BlockCapability<T, Direction> capability = BlockCapability.createSided(exposure.id(), exposure.valueType());
        event.registerBlockEntity(capability, ModBlockEntities.BES.get(kind.id()).get(), (be, side) -> {
            if (!(be instanceof IOPortBlockEntity port) || !port.isNativeSideExposed(binding, side)) return null;
            return exposure.resolver().resolve(port, port.ioType(), side);
        });
    }

    private static void registerFacetProviders(RegisterCapabilitiesEvent event, IOPortKind kind,
                                               List<CapabilityBinding> bindings) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(ITEM_BLOCK, ModBlockEntities.BES.get(kind.id()).get(), (be, side) -> {
            if (!(be instanceof IOPortBlockEntity port)) return null;
            IItemHandler handler = itemHandler(port, bindings, side);
            return handler == null ? null : new DirectionalItemHandler(handler, canInsert, true);
        });
        event.registerBlockEntity(FLUID_BLOCK, ModBlockEntities.BES.get(kind.id()).get(), (be, side) -> {
            if (!(be instanceof IOPortBlockEntity port)) return null;
            IFluidHandler handler = fluidHandler(port, bindings, side);
            return handler == null ? null : new DirectionalFluidHandler(handler, canInsert, !canInsert);
        });
        event.registerBlockEntity(ENERGY_BLOCK, ModBlockEntities.BES.get(kind.id()).get(), (be, side) -> {
            if (!(be instanceof IOPortBlockEntity port)) return null;
            IEnergyStorage storage = energyStorage(port, bindings, side);
            return storage == null ? null : new DirectionalEnergyStorage(storage, canInsert, !canInsert);
        });
    }

    static IItemHandler itemHandler(IOPortBlockEntity port, List<CapabilityBinding> bindings, Direction side) {
        for (CapabilityBinding binding : bindings) {
            if (!port.isNativeSideExposed(binding, side)) continue;
            IItemHandler handler = CapabilityFactories.itemHandler(port.capability(binding.type()));
            if (handler != null) return handler;
        }
        return null;
    }

    static IFluidHandler fluidHandler(IOPortBlockEntity port, List<CapabilityBinding> bindings, Direction side) {
        for (CapabilityBinding binding : bindings) {
            if (!port.isNativeSideExposed(binding, side)) continue;
            IFluidHandler handler = CapabilityFactories.fluidHandler(port.capability(binding.type()));
            if (handler != null) return handler;
        }
        return null;
    }

    static IEnergyStorage energyStorage(IOPortBlockEntity port, List<CapabilityBinding> bindings, Direction side) {
        for (CapabilityBinding binding : bindings) {
            if (!port.isNativeSideExposed(binding, side)) continue;
            IEnergyStorage storage = CapabilityFactories.energyStorage(port.capability(binding.type()));
            if (storage != null) return storage;
        }
        return null;
    }

    /**
     * Legacy bridge for AE2's independently versioned Transfer capability exposure.
     * Built-in ports register the native NeoForge handler constants above instead.
     */
    public static <R extends Resource> ResourceHandler<R> resourceStorageHandler(ResourceStorage<R> storage,
                                                                                  boolean canInsert,
                                                                                  boolean canExtract) {
        return new ResourceStorageHandler<>(storage, canInsert, canExtract);
    }

    private record DirectionalItemHandler(IItemHandler handler, boolean canInsert, boolean canExtract)
            implements IItemHandler {
        @Override public int getSlots() { return handler.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return handler.getStackInSlot(slot).copy(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return canInsert ? handler.insertItem(slot, stack.copy(), simulate) : stack.copy();
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtract ? handler.extractItem(slot, amount, simulate).copy() : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return handler.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return canInsert && handler.isItemValid(slot, stack);
        }
    }

    private record DirectionalFluidHandler(IFluidHandler handler, boolean canInsert, boolean canExtract)
            implements IFluidHandler {
        @Override public int getTanks() { return handler.getTanks(); }
        @Override public FluidStack getFluidInTank(int tank) { return handler.getFluidInTank(tank).copy(); }
        @Override public int getTankCapacity(int tank) { return handler.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return canInsert && handler.isFluidValid(tank, stack);
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return canInsert ? handler.fill(resource.copy(), action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return canExtract ? handler.drain(resource.copy(), action).copy() : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return canExtract ? handler.drain(maxDrain, action).copy() : FluidStack.EMPTY;
        }
    }

    private record DirectionalEnergyStorage(IEnergyStorage storage, boolean canInsert, boolean canExtract)
            implements LongEnergyHandler {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            return canInsert ? storage.receiveEnergy(amount, simulate) : 0;
        }
        @Override public int extractEnergy(int amount, boolean simulate) {
            return canExtract ? storage.extractEnergy(amount, simulate) : 0;
        }
        @Override public int getEnergyStored() { return storage.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return storage.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return canExtract && storage.canExtract(); }
        @Override public boolean canReceive() { return canInsert && storage.canReceive(); }
        @Override public long getTransferLimit() {
            return storage instanceof LongEnergyHandler handler ? handler.getTransferLimit() : Integer.MAX_VALUE;
        }
        @Override public long getAmountAsLong() {
            return storage instanceof LongEnergyHandler handler ? handler.getAmountAsLong() : storage.getEnergyStored();
        }
        @Override public long getCapacityAsLong() {
            return storage instanceof LongEnergyHandler handler ? handler.getCapacityAsLong() : storage.getMaxEnergyStored();
        }
        @Override public long insertLong(long amount, boolean simulate) {
            if (!canInsert || amount <= 0L) return 0L;
            return storage instanceof LongEnergyHandler handler ? handler.insertLong(amount, simulate)
                    : storage.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
        }
        @Override public long extractLong(long amount, boolean simulate) {
            if (!canExtract || amount <= 0L) return 0L;
            return storage instanceof LongEnergyHandler handler ? handler.extractLong(amount, simulate)
                    : storage.extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
        }
    }

    private record ResourceStorageHandler<R extends Resource>(ResourceStorage<R> storage, boolean canInsert,
                                                               boolean canExtract) implements ResourceHandler<R> {
        @Override public int size() { return storage.size(); }
        @Override public R getResource(int slot) {
            R resource = storage.resource(slot);
            return resource == null ? emptyResource() : resource;
        }
        @Override public long getAmountAsLong(int slot) { return storage.amount(slot); }
        @Override public long getCapacityAsLong(int slot, R resource) { return storage.capacity(slot, resource); }
        @Override public boolean isValid(int slot, R resource) {
            TransferPreconditions.checkNonEmpty(resource);
            return storage.isValid(slot, resource);
        }
        @Override public int insert(int slot, R resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return canInsert ? (int) storage.insert(slot, resource, amount, transaction) : 0;
        }
        @Override public int extract(int slot, R resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return canExtract ? (int) storage.extract(slot, resource, amount, transaction) : 0;
        }
        @SuppressWarnings("unchecked")
        private R emptyResource() {
            if (storage.resourceType() == ItemResource.class) return (R) ItemResource.EMPTY;
            if (storage.resourceType() == FluidResource.class) return (R) FluidResource.EMPTY;
            throw new IllegalStateException("Missing empty resource for " + storage.resourceType().getName());
        }
    }
}
