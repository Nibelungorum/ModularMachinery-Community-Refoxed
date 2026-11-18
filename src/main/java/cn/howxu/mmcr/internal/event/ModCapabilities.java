package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.api.capability.external.ExternalCapabilityContext;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityRegistry;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedflux.AppliedFluxBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModCapabilities {
    public static final BlockCapability<ResourceHandler<ItemResource>, Direction> ITEM_BLOCK =
            Capabilities.Item.BLOCK;
    public static final BlockCapability<ResourceHandler<FluidResource>, Direction> FLUID_BLOCK =
            Capabilities.Fluid.BLOCK;
    public static final BlockCapability<EnergyHandler, Direction> ENERGY_BLOCK =
            Capabilities.Energy.BLOCK;
    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        ExternalCapabilityContext context = new ExternalCapabilityContext();
        ExternalCapabilityRegistry.global().freeze(context);
        for (IOPortKind kind : nativeCapabilityPorts()) {
            registerNativePort(event, kind, context);
        }
        MekanismBridge.get().registerCapabilities(event);
        AE2Bridge.get().registerCapabilities(event);
        AppliedFluxBridge.get().registerCapabilities(event);
        event.registerBlockEntity(
                ITEM_BLOCK,
                ModBlockEntities.BES.get("factory_controller").get(),
                (be, side) -> be instanceof FactorySchedulerBlockEntity scheduler
                        ? resourceStorageHandler(scheduler.itemStorage(), true, true)
                        : null);
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
        return PortKinds.all().stream()
                .filter(kind -> !kind.definition().bindings().isEmpty())
                .toList();
    }

    static void registerNativePort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                   ExternalCapabilityContext adapters) {
        var bindings = kind.bindings().stream().filter(CapabilityBinding::nativeTransferExposure)
                .filter(binding -> binding.directions().supports(kind.ioType())).toList();
        Map<Identifier, Route> global = new LinkedHashMap<>();
        Map<Identifier, Route> local = new LinkedHashMap<>();
        for (CapabilityBinding binding : bindings) {
            for (var exposure : adapters.bindings(binding.type())) {
                addRoute(kind, global, new Route(binding, exposure));
            }
            binding.externalExposure().ifPresent(exposure -> addRoute(kind, local, new Route(binding, exposure)));
        }
        local.forEach((id, route) -> global.merge(id, route, (adapter, override) -> {
            if (adapter.exposure().valueType() != override.exposure().valueType()) {
                throw conflictingRoutes(kind, adapter, override);
            }
            return override;
        }));
        global.values().forEach(route -> registerExternalPort(event, kind, route.binding(), route.exposure()));

        // A local resolver owns its binding. Other bindings can still expose native facets, but must
        // never bypass an explicit provider that declined a side or returned null for its ID.
        var fallback = bindings.stream().filter(binding -> binding.externalExposure().isEmpty()).toList();
        if (!fallback.isEmpty()) {
            if (!global.containsKey(ITEM_BLOCK.name())) registerItemPort(event, kind, fallback);
            if (!global.containsKey(FLUID_BLOCK.name())) registerFluidPort(event, kind, fallback);
            if (!global.containsKey(ENERGY_BLOCK.name())) registerEnergyPort(event, kind, fallback);
        }
    }

    /** One external handler and the port binding whose direction and side policy guard it.
     * @author howxu <dev@howxu.cn>
     */
    private record Route(CapabilityBinding binding, CapabilityBinding.ExternalExposure<?> exposure) { }

    private static void addRoute(IOPortKind kind, Map<Identifier, Route> routes, Route route) {
        Route previous = routes.putIfAbsent(route.exposure().id(), route);
        if (previous != null) throw conflictingRoutes(kind, previous, route);
    }

    private static IllegalArgumentException conflictingRoutes(IOPortKind kind, Route first, Route second) {
        return new IllegalArgumentException("Conflicting external capability " + first.exposure().id()
                + " on port " + kind.id() + " for bindings " + first.binding().type().id()
                + " and " + second.binding().type().id() + " (" + first.exposure().valueType().getName()
                + ", " + second.exposure().valueType().getName() + ")");
    }

    private static <T> void registerExternalPort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                                  CapabilityBinding binding,
                                                  CapabilityBinding.ExternalExposure<T> exposure) {
        BlockCapability<T, Direction> capability = BlockCapability.createSided(exposure.id(), exposure.valueType());
        event.registerBlockEntity(
                capability,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port)
                            || !port.isNativeSideExposed(binding, side)) return null;
                    return exposure.resolver().resolve(port, port.ioType(), side);
                });
    }

    private static void registerItemPort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                         List<CapabilityBinding> bindings) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(
                ITEM_BLOCK,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port)
                            || port.ioType() != kind.ioType()) return null;
                    ResourceStorage<ItemResource> storage = resourceStorage(port, bindings, side, ItemResource.class,
                            true);
                    if (storage == null) return null;
                    return resourceStorageHandler(storage, canInsert, true);
                });
    }

    private static void registerFluidPort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                          List<CapabilityBinding> bindings) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(
                FLUID_BLOCK,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port)
                            || port.ioType() != kind.ioType()) return null;
                    ResourceStorage<FluidResource> storage = resourceStorage(port, bindings, side, FluidResource.class,
                            true);
                    if (storage == null) return null;
                    return resourceStorageHandler(storage, canInsert, !canInsert);
                });
    }

    private static void registerEnergyPort(RegisterCapabilitiesEvent event, IOPortKind kind,
                                           List<CapabilityBinding> bindings) {
        boolean canInsert = kind.ioType() == IOType.INPUT;
        event.registerBlockEntity(
                ENERGY_BLOCK,
                ModBlockEntities.BES.get(kind.id()).get(),
                (be, side) -> {
                    if (!(be instanceof IOPortBlockEntity port)
                            || port.ioType() != kind.ioType()) return null;
                    LongValueStorage storage = valueStorage(port, bindings, side);
                    if (storage == null) return null;
                    return new DirectionalEnergyHandler(energyHandler(storage), canInsert, !canInsert);
                });
    }

    static <R> ResourceStorage<R> resourceStorage(IOPortBlockEntity port,
                                                   List<CapabilityBinding> bindings, Direction side,
                                                   Class<R> resourceType) {
        return resourceStorage(port, bindings, side, resourceType, false);
    }

    private static <R> ResourceStorage<R> resourceStorage(IOPortBlockEntity port,
                                                          List<CapabilityBinding> bindings, Direction side,
                                                          Class<R> resourceType, boolean requireTransferFacet) {
        for (CapabilityBinding binding : bindings) {
            if (!port.isNativeSideExposed(binding, side)) continue;
            var capability = port.capability(binding.type());
            if (requireTransferFacet && capability.facet(TransferFacet.class).isEmpty()) {
                continue;
            }
            ResourceStorage<R> storage = CapabilityFactories.resourceStorage(capability, resourceType);
            if (storage != null) return storage;
        }
        return null;
    }

    private static LongValueStorage valueStorage(IOPortBlockEntity port, List<CapabilityBinding> bindings,
                                                 Direction side) {
        for (CapabilityBinding binding : bindings) {
            if (!port.isNativeSideExposed(binding, side)) continue;
            LongValueStorage storage = CapabilityFactories.valueStorage(port.capability(binding.type()), LongValueStorage.class);
            if (storage != null) return storage;
        }
        return null;
    }

    public static <R extends Resource> ResourceHandler<R> resourceStorageHandler(ResourceStorage<R> storage,
                                                                                 boolean canInsert,
                                                                                 boolean canExtract) {
        return new ResourceStorageHandler<>(storage, canInsert, canExtract);
    }

    private record DirectionalEnergyHandler(LongEnergyHandler handler, boolean canInsert,
                                            boolean canExtract) implements LongEnergyHandler {

        @Override
            public long getAmountAsLong() {
                return handler.getAmountAsLong();
            }

            @Override
            public long getCapacityAsLong() {
                return handler.getCapacityAsLong();
            }

            @Override
            public long getTransferLimit() {
                return handler.getTransferLimit();
            }

            @Override
            public int insert(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                if (!canInsert) return 0;
                return handler.insert(amount, tx);
            }

            @Override
            public int extract(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                if (!canExtract) return 0;
                return handler.extract(amount, tx);
            }

            @Override
            public long insertLong(long amount, TransactionContext tx) {
                if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
                if (!canInsert) return 0L;
                return handler.insertLong(amount, tx);
            }

            @Override
            public long extractLong(long amount, TransactionContext tx) {
                if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
                if (!canExtract) return 0L;
                return handler.extractLong(amount, tx);
            }
        }

    private static LongEnergyHandler energyHandler(LongValueStorage storage) {
        return new LongEnergyHandler() {
            @Override public long getAmountAsLong() { return storage.amount(); }
            @Override public long getCapacityAsLong() { return storage.capacity(); }
            @Override public long getTransferLimit() { return storage.transferLimit(); }
            @Override public int insert(int amount, TransactionContext tx) { return (int) storage.insert(amount, tx); }
            @Override public int extract(int amount, TransactionContext tx) { return (int) storage.extract(amount, tx); }
            @Override public long insertLong(long amount, TransactionContext tx) {
                if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
                return storage.insert(amount, tx);
            }
            @Override public long extractLong(long amount, TransactionContext tx) {
                if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
                return storage.extract(amount, tx);
            }
        };
    }

    private record ResourceStorageHandler<R extends Resource>(ResourceStorage<R> storage, boolean canInsert,
                                                              boolean canExtract) implements ResourceHandler<R> {

        @Override
        public int size() {
            return storage.size();
        }

        @Override
        public R getResource(int slot) {
                R resource = storage.resource(slot);
                return resource == null ? emptyResource() : resource;
            }

        @Override
        public long getAmountAsLong(int slot) {
            return storage.amount(slot);
        }

        @Override
        public long getCapacityAsLong(int slot, R resource) {
            return storage.capacity(slot, resource);
        }

        @Override
        public boolean isValid(int slot, R resource) {
                TransferPreconditions.checkNonEmpty(resource);
                return storage.isValid(slot, resource);
            }

        @Override
        public int insert(int slot, R resource, int amount, TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return canInsert ? (int) storage.insert(slot, resource, amount, tx) : 0;
            }

        @Override
        public int extract(int slot, R resource, int amount, TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return canExtract ? (int) storage.extract(slot, resource, amount, tx) : 0;
            }

            @SuppressWarnings("unchecked")
            private R emptyResource() {
                if (storage.resourceType() == ItemResource.class) return (R) ItemResource.EMPTY;
                if (storage.resourceType() == FluidResource.class) return (R) FluidResource.EMPTY;
                throw new IllegalStateException("Missing empty resource for " + storage.resourceType().getName());
            }
        }

}
