package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.autoio.AutoIOConfig;
import cn.howxu.mmcr.internal.autoio.CapabilityTransferPolicies;
import cn.howxu.mmcr.internal.autoio.AutoIoHandler;
import cn.howxu.mmcr.internal.autoio.AutoIoResult;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.TreeMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public abstract class IOPortBlockEntity extends LinkedAppearanceBlockEntity implements MachineComponentTile, CapabilityHost {
    private static final String AUTO_IO_CAPABILITIES_KEY = "auto_io_capabilities";
    private final Map<CapabilityType, AutoIOConfig> autoIOConfigs = new LinkedHashMap<>();
    private boolean autoIOCacheDirty = true;
    private boolean loadingAdditional;
    private final Map<CapabilityType, AutoIOState> autoIOStates = new LinkedHashMap<>();
    private final Map<AvailabilityKey, AvailabilityState> availabilityStates = new LinkedHashMap<>();

    protected IOPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected static IOPortKind kindFromState(BlockState state, IOPortKind fallback) {
        if (!(state.getBlock() instanceof IOPortBlock portBlock)) {
            return fallback;
        }
        IOPortKind kind = portBlock.kind();
        if (kind.ioType() != fallback.ioType()
                || kind.itemBusSize().isPresent() != fallback.itemBusSize().isPresent()
                || kind.fluidHatchSize().isPresent() != fallback.fluidHatchSize().isPresent()
                || kind.energyHatchSize().isPresent() != fallback.energyHatchSize().isPresent()
                || kind.extendedItemBusSize().isPresent() != fallback.extendedItemBusSize().isPresent()
                || kind.extendedFluidHatchSize().isPresent() != fallback.extendedFluidHatchSize().isPresent()
                || kind.extendedEnergyHatchSize().isPresent() != fallback.extendedEnergyHatchSize().isPresent()
                || kind.combinedPortSize().isPresent() != fallback.combinedPortSize().isPresent()
                || kind.extendedCombinedPortSize().isPresent() != fallback.extendedCombinedPortSize().isPresent()) {
            return fallback;
        }
        return kind;
    }

    protected static BlockEntityType<?> typeFromState(BlockState state, IOPortKind fallback) {
        return typeForKind(kindFromState(state, fallback));
    }

    protected static BlockEntityType<?> typeForKind(IOPortKind kind) {
        return ModBlockEntities.BES.get(kind.id()).get();
    }

    public abstract IOType ioType();

    public abstract IOPortKind kind();

    @Override
    public void setChanged() {
        super.setChanged();
    }

    protected final void notifyStorageChanged() {
        if (loadingAdditional) return;
        setChanged();
        sendStorageSnapshot();
        notifyAvailabilityChanges();
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (level != null && level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller) {
                controller.notifyCapabilityPresentationChanged();
            }
        }
    }

    private void notifyAvailabilityChanges() {
        for (MachineCapability capability : capabilitySnapshot().capabilities()) {
            LongValueStorage valueStorage = CapabilityFactories.valueStorage(capability, LongValueStorage.class);
            IEnergyStorage energyStorage = CapabilityFactories.energyStorage(capability);
            Object resource = valueStorage == null && energyStorage == null ? null : capability.type();
            List<Object> resources = new ArrayList<>();
            List<SlotAvailability> slots = new ArrayList<>();
            if (resource != null) resources.add(resource);
            long amount = 0L;
            if (valueStorage != null) {
                amount = valueStorage.amount();
                slots.add(new SlotAvailability(resource, amount));
            } else if (energyStorage != null) {
                amount = energyStorage instanceof cn.howxu.mmcr.internal.storage.LongEnergyHandler storage
                        ? storage.getAmountAsLong() : energyStorage.getEnergyStored();
                slots.add(new SlotAvailability(resource, amount));
            } else {
                IItemHandler itemHandler = CapabilityFactories.itemHandler(capability);
                IFluidHandler fluidHandler = CapabilityFactories.fluidHandler(capability);
                int slotCount = itemHandler == null ? fluidHandler == null ? 0 : fluidHandler.getTanks() : itemHandler.getSlots();
                for (int slot = 0; slot < slotCount; slot++) {
                    Object nativeResource = itemHandler == null ? fluidHandler.getFluidInTank(slot).copy()
                            : itemHandler.getStackInSlot(slot).copy();
                    long slotAmount = itemHandler == null
                            ? fluidHandler instanceof cn.howxu.mmcr.internal.storage.LongFluidStorage storage
                                    ? storage.amount(slot) : ((FluidStack) nativeResource).getAmount()
                            : itemHandler instanceof cn.howxu.mmcr.internal.storage.LongItemStorage storage
                                    ? storage.amount(slot) : ((ItemStack) nativeResource).getCount();
                    Object slotResource = nativeResource;
                    amount += slotAmount;
                    slots.add(new SlotAvailability(slotResource, slotAmount));
                    if (slotAmount > 0L && !isEmptyNativeResource(nativeResource)) {
                        resources.add(slotResource);
                        if (resource == null) resource = slotResource;
                    }
                }
            }
            AvailabilityState previous = availabilityStates.put(new AvailabilityKey(capability.type(), capability.directions()),
                    new AvailabilityState(amount, List.copyOf(resources), List.copyOf(slots)));
            long previousAmount = previous == null ? 0L : previous.amount();
            List<Object> previousResources = previous == null ? List.of() : previous.resources();
            boolean resourceChanged = previous != null && !resources.equals(previousResources);
            if (amount > previousAmount && capability.directions().supports(IOType.INPUT)) {
                ResourceAvailabilityNotifier.Reason reason = valueStorage != null || energyStorage != null
                                ? ResourceAvailabilityNotifier.Reason.ENERGY_AVAILABLE
                                : ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE;
                for (Object available : resources) notifyControllers(reason, available);
            } else if (resourceChanged && capability.directions().supports(IOType.INPUT)) {
                for (Object available : resources) {
                    if (!previousResources.contains(available)) {
                        notifyControllers(ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE, available);
                    }
                }
            }
            if (capability.directions().supports(IOType.OUTPUT)) {
                List<SlotAvailability> previousSlots = previous == null ? List.of() : previous.slots();
                List<Object> notified = new ArrayList<>();
                for (int slot = 0; slot < previousSlots.size(); slot++) {
                    SlotAvailability previousSlot = previousSlots.get(slot);
                    SlotAvailability currentSlot = slot < slots.size()
                            ? slots.get(slot) : new SlotAvailability(null, 0L);
                    if (previousSlot.amount() <= 0L || previousSlot.resource() == null) continue;
                    boolean amountReleased = currentSlot.amount() < previousSlot.amount();
                    boolean resourceReleased = !Objects.equals(previousSlot.resource(), currentSlot.resource());
                    if ((amountReleased || resourceReleased) && notified.add(previousSlot.resource())) {
                        notifyControllers(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY,
                                previousSlot.resource());
                    }
                }
            }
        }
    }

    private static boolean isEmptyNativeResource(Object resource) {
        return resource instanceof ItemStack itemStack && itemStack.isEmpty()
                || resource instanceof FluidStack fluidStack && fluidStack.isEmpty();
    }

    private void notifyControllers(ResourceAvailabilityNotifier.Reason reason, @Nullable Object resource) {
        if (level == null || level.isClientSide()) return;
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller) {
                controller.notifyResourceAvailability(reason, resource);
            }
        }
    }

    private record AvailabilityState(long amount, List<Object> resources, List<SlotAvailability> slots) { }

    private record AvailabilityKey(CapabilityType type, CapabilityDirections directions) { }

    private record SlotAvailability(@Nullable Object resource, long amount) { }

    public void dropContents() {
    }

    public void onBlockRemoved() {
        dropContents();
    }

    protected void notifyControllerOfInputChange() {
        if (loadingAdditional || ioType() != IOType.INPUT || level == null || level.isClientSide() || linkedControllerPos() == null) return;
        if (level.getBlockEntity(linkedControllerPos()) instanceof MachineControllerBlockEntity controller) {
            controller.onRecipeInputsChanged();
        }
    }

    protected final void beginLoadingAdditional() {
        loadingAdditional = true;
    }

    protected final void endLoadingAdditional() {
        loadingAdditional = false;
    }

    protected void sendStorageSnapshot() {
        if (level != null && !level.isClientSide()) PktPortStorageSyncPayload.sendToViewers(this);
    }

    @Override
    public abstract CapabilitySnapshot capabilitySnapshot();

    protected final MachineCapability createCapability(CapabilityType type) {
        if (type == null) throw new IllegalArgumentException("Capability type must not be null");
        CapabilityBinding binding = kind().definition().bindings().stream()
                .filter(candidate -> candidate.type().equals(type))
                .findFirst()
                .orElseGet(() -> {
                    CapabilityDefinition definition = Optional.ofNullable(CapabilityRegistry.get(type))
                            .orElseThrow(() -> new IllegalStateException("Capability is not registered: " + type.id()));
                    return new CapabilityBinding(type, CapabilityDirections.of(ioType()), definition.factory(),
                            (ignored, tier) -> true);
                });
        return createCapability(binding);
    }

    protected final MachineCapability createCapability(CapabilityBinding binding) {
        if (binding == null) throw new IllegalArgumentException("Capability binding must not be null");
        return binding.factory().create(new CapabilityCreationContext() {
            @Override
            public CapabilityHost host() {
                return IOPortBlockEntity.this;
            }

            @Override
            public IOType ioType() {
                return IOPortBlockEntity.this.ioType();
            }

            @Override
            public CapabilityDirections directions() {
                return binding.directions();
            }

            @Override
            public <T> Optional<T> service(Class<T> serviceType) {
                return serviceType.isInstance(IOPortBlockEntity.this)
                        ? Optional.of(serviceType.cast(IOPortBlockEntity.this)) : Optional.empty();
            }

            @Override
            public Runnable onChanged() {
                return IOPortBlockEntity.this::markAutoIOCacheDirty;
            }
        });
    }

    /** Native item handler used by requirement execution and external capability exposure. */
    public IItemHandler nativeItemHandler() {
        throw new IllegalStateException("Port does not expose an item handler: " + kind().id());
    }

    /** Native fluid handler used by requirement execution and external capability exposure. */
    public IFluidHandler nativeFluidHandler() {
        throw new IllegalStateException("Port does not expose a fluid handler: " + kind().id());
    }

    /** Native energy storage used by requirement execution and external capability exposure. */
    public IEnergyStorage nativeEnergyStorage() {
        throw new IllegalStateException("Port does not expose an energy storage: " + kind().id());
    }

    public AutoIOConfig autoIOConfig() {
        MachineCapability capability = autoIOCapability();
        return capability == null ? new AutoIOConfig() : autoIOConfig(capability.type());
    }

    public AutoIOConfig autoIOConfig(CapabilityType type) {
        if (type == null) throw new IllegalArgumentException("Capability type must not be null");
        if (autoIOCapability(type) == null) return new AutoIOConfig();
        return autoIOConfigs.computeIfAbsent(type, ignored -> new AutoIOConfig());
    }

    public @Nullable MachineCapability capability(CapabilityType type) {
        if (type == null) return null;
        return capabilitySnapshot().capabilities().stream()
                .filter(capability -> type.equals(capability.type()))
                .findFirst().orElse(null);
    }

    public int autoIOCandidateCount() {
        MachineCapability capability = autoIOCapability();
        return capability == null ? 0 : autoIOStates.getOrDefault(capability.type(), new AutoIOState()).candidateSides.size();
    }

    public int autoIODelay() {
        MachineCapability capability = autoIOCapability();
        int minimum = ServerConfig.autoIoMinDelayTicks();
        int maximum = ServerConfig.autoIoMaxDelayTicks();
        return capability == null ? maximum
                : Math.clamp(autoIOStates.getOrDefault(capability.type(), new AutoIOState()).delay, minimum, maximum);
    }

    public boolean hasAutoIOWork() {
        return hasAutoIOTransferWork();
    }

    protected boolean hasAutoIOTransferWork() {
        for (MachineCapability capability : capabilitySnapshot().capabilities()) {
            AutoIoHandler handler = transferHandler(capability).orElse(null);
            if (handler != null && handler.hasWork(capability)) return true;
        }
        return false;
    }

    public record AdjacentSide(Direction side, BlockState state, ItemStack icon, Component name) {
    }

    public AdjacentSide adjacentSide(Direction side) {
        BlockState adjacentState = level == null || side == null
                ? Blocks.AIR.defaultBlockState()
                : level.getBlockState(worldPosition.relative(side));
        ItemStack icon = adjacentState.getBlock().asItem() == Items.AIR
                ? ItemStack.EMPTY
                : adjacentState.getBlock().asItem().getDefaultInstance();
        return new AdjacentSide(side, adjacentState, icon, adjacentState.getBlock().getName());
    }

    public void setAutoIOEnabled(boolean enabled) {
        setAutoIOEnabled(autoIOCapabilityType(), enabled);
    }

    public void setAutoIOEnabled(CapabilityType type, boolean enabled) {
        if (type == null || autoIOCapability(type) == null) return;
        AutoIOConfig config = autoIOConfig(type);
        if (config.enabled() == enabled) return;
        config.setEnabled(enabled);
        markAutoIOConfigChanged();
    }

    public boolean isAutoIOSideExposed(Direction side) {
        return isAutoIOSideExposed(autoIOCapabilityType(), side);
    }

    public boolean isAutoIOSideExposed(CapabilityType type, Direction side) {
        return type != null && autoIOCapability(type) != null
                && (side == null || autoIOConfig(type).isSideEnabled(side));
    }

    /**
     * Checks whether a binding may expose its native provider without requiring AutoIO support.
     */
    public boolean isNativeSideExposed(CapabilityBinding binding, Direction side) {
        if (binding == null || !binding.directions().supports(ioType()) || capability(binding.type()) == null) {
            return false;
        }
        AutoIOConfig config = autoIOConfigs.get(binding.type());
        return side == null || config == null || config.isSideEnabled(side);
    }

    public void toggleAutoIOEnabled() {
        setAutoIOEnabled(!autoIOConfig().enabled());
    }

    public void setAutoIOSide(Direction side, boolean enabled) {
        setAutoIOSide(autoIOCapabilityType(), side, enabled);
    }

    public void setAutoIOSide(CapabilityType type, Direction side, boolean enabled) {
        if (type == null || side == null || autoIOCapability(type) == null) return;
        AutoIOConfig config = autoIOConfig(type);
        if (config.isSideEnabled(side) == enabled) return;
        config.setSide(side, enabled);
        markAutoIOConfigChanged();
    }

    public void setAllAutoIOSides(boolean enabled) {
        setAllAutoIOSides(autoIOCapabilityType(), enabled);
    }

    public void setAllAutoIOSides(CapabilityType type, boolean enabled) {
        if (type == null || autoIOCapability(type) == null) return;
        AutoIOConfig config = autoIOConfig(type);
        if (config.enabledSides().size() == (enabled ? Direction.values().length : 0)) return;
        config.setAllSides(enabled);
        markAutoIOConfigChanged();
    }

    public void toggleAutoIOSide(Direction side) {
        if (side == null) return;
        setAutoIOSide(side, !autoIOConfig().isSideEnabled(side));
    }

    private void markAutoIOConfigChanged() {
        markAutoIOCacheDirty();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void markAutoIOCacheDirty() {
        autoIOCacheDirty = true;
    }

    protected boolean consumeAutoIOCacheDirty() {
        boolean dirty = autoIOCacheDirty;
        autoIOCacheDirty = false;
        return dirty;
    }

    @Override
    public MachineComponent provideComponent() {
        return new MachineComponent(kind(), CapabilityDirections.of(ioType()));
    }

    @Override
    public ComponentClaimPolicy claimPolicy() {
        return ComponentClaimPolicy.SHARED_SERIALIZED;
    }

    public void serverTick() {
        tick();
        maintainControllerLink();
        runAutoIOCycle();
    }

    protected void runAutoIOCycle() {
        if (level == null || level.isClientSide()) return;
        boolean rebuiltCandidates = consumeAutoIOCacheDirty();
        for (MachineCapability capability : capabilitySnapshot().capabilities()) {
            AutoIoHandler handler = transferHandler(capability).orElse(null);
            if (handler == null) {
                autoIOConfigs.remove(capability.type());
                autoIOStates.remove(capability.type());
                continue;
            }
            AutoIOConfig config = autoIOConfig(capability.type());
            if (!config.enabled() || config.enabledSides().isEmpty()) continue;
            AutoIOState state = autoIOStates.computeIfAbsent(capability.type(), ignored -> new AutoIOState());
            if (rebuiltCandidates) rebuildAutoIOCandidates(capability, handler, config, state);
            if (state.candidateSides.isEmpty()) {
                if (rebuiltCandidates) {
                    state.ticksUntilTransfer = ServerConfig.autoIoMinDelayTicks() - 1;
                    continue;
                }
                if (state.ticksUntilTransfer > 0) {
                    state.ticksUntilTransfer--;
                    continue;
                }
                rebuildAutoIOCandidates(capability, handler, config, state);
                if (state.candidateSides.isEmpty()) continue;
            }
            if (!handler.hasWork(capability)) continue;
            if (state.ticksUntilTransfer > 0) {
                state.ticksUntilTransfer--;
                continue;
            }

            boolean moved = false;
            for (Direction side : state.candidateSides) {
                AutoIoResult result = handler.transfer(capability, side, null, 0L);
                moved |= result.successful();
            }
            if (moved) incrementAutoIOSuccess(state);
            else decrementAutoIOSuccess(state);
            state.ticksUntilTransfer = state.delay - 1;
        }
    }

    private void rebuildAutoIOCandidates(MachineCapability capability, AutoIoHandler handler,
                                         AutoIOConfig config, AutoIOState state) {
        state.candidateSides.clear();
        for (Direction side : config.enabledSides()) {
            if (handler.hasAdjacentTarget(capability, side)) state.candidateSides.add(side);
        }
    }

    private @Nullable MachineCapability autoIOCapability() {
        List<MachineCapability> capabilities = capabilitySnapshot().capabilities();
        return capabilities.stream()
                .filter(capability -> transferHandler(capability).isPresent())
                .findFirst().orElse(null);
    }

    private @Nullable MachineCapability autoIOCapability(CapabilityType type) {
        if (type == null) return null;
        return capabilitySnapshot().capabilities().stream()
                .filter(capability -> type.equals(capability.type()))
                .filter(capability -> transferHandler(capability).isPresent())
                .findFirst().orElse(null);
    }

    private @Nullable CapabilityType autoIOCapabilityType() {
        MachineCapability capability = autoIOCapability();
        return capability == null ? null : capability.type();
    }

    public boolean ejectContents() {
        CapabilityType type = autoIOCapabilityType();
        return type != null && ejectContents(type);
    }

    public boolean ejectContents(CapabilityType type) {
        return ejectContents(type, false);
    }

    public boolean ejectContents(CapabilityType type, boolean allResources) {
        if (level == null || level.isClientSide() || ioType() != IOType.INPUT) return false;
        MachineCapability capability = capability(type);
        if (capability == null || !capability.directions().supports(IOType.INPUT)) return false;
        AutoIoHandler handler = capability == null ? null : transferHandler(capability).orElse(null);
        if (capability == null || handler == null) return false;
        if (isUsedByActiveRecipe()) return false;
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        for (int index = sides.size() - 1; index > 0; index--) {
            int swapIndex = level.getRandom().nextInt(index + 1);
            Direction side = sides.get(index);
            sides.set(index, sides.get(swapIndex));
            sides.set(swapIndex, side);
        }
        boolean moved = false;
        List<Object> resources = handler.ejectionResources(capability);
        if (resources.isEmpty()) return ejectResource(handler, capability, null, sides);
        int resourceCount = allResources ? resources.size() : 1;
        for (int resourceIndex = 0; resourceIndex < resourceCount; resourceIndex++) {
            moved |= ejectResource(handler, capability, resources.get(resourceIndex), sides);
        }
        return moved;
    }

    private static boolean ejectResource(AutoIoHandler handler, MachineCapability capability,
                                         @Nullable Object resource, List<Direction> sides) {
        long remaining = Integer.MAX_VALUE;
        boolean moved = false;
        for (Direction side : sides) {
            AutoIoResult result = handler.transfer(capability, side, resource, remaining);
            moved |= result.successful();
            remaining -= Math.min(remaining, result.amount());
            if (remaining == 0L) break;
        }
        return moved;
    }

    private static Optional<AutoIoHandler> transferHandler(MachineCapability capability) {
        if (capability == null || capability.type() == null
                || capability.facet(TransferFacet.class).isEmpty()) {
            return Optional.empty();
        }
        CapabilityTransferPolicies.ensureRegistered();
        return CapabilityTransferPolicies.handlerFor(capability);
    }

    protected boolean isUsedByActiveRecipe() {
        if (level == null) return false;
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller
                    && controller.isPortUsedByActiveRecipe(worldPosition)) return true;
        }
        return false;
    }

    private void incrementAutoIOSuccess(AutoIOState state) {
        int minimum = ServerConfig.autoIoMinDelayTicks();
        int maximum = ServerConfig.autoIoMaxDelayTicks();
        int step = ServerConfig.autoIoSuccessDelayStepTicks();
        int max = (maximum - minimum) / step;
        if (state.successCounter < max) state.successCounter++;
        state.delay = Math.max(minimum, maximum - state.successCounter * step);
    }

    private void decrementAutoIOSuccess(AutoIOState state) {
        int minimum = ServerConfig.autoIoMinDelayTicks();
        int maximum = ServerConfig.autoIoMaxDelayTicks();
        int step = ServerConfig.autoIoSuccessDelayStepTicks();
        if (state.successCounter > 0) state.successCounter--;
        state.delay = Math.max(minimum, maximum - state.successCounter * step);
    }

    protected void tick() {
        kind().tick(this);
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        CompoundTag profiles = null;
        for (MachineCapability capability : capabilitySnapshot().capabilities()) {
            if (transferHandler(capability).isEmpty()) continue;
            if (profiles == null) profiles = new CompoundTag();
            CompoundTag profile = new CompoundTag();
            autoIOConfig(capability.type()).save(profile);
            profiles.put(capability.type().id().toString(), profile);
        }
        if (profiles != null) output.put(AUTO_IO_CAPABILITIES_KEY, profiles);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        autoIOConfigs.clear();
        if (input.contains(AUTO_IO_CAPABILITIES_KEY)) {
            CompoundTag profiles = input.getCompound(AUTO_IO_CAPABILITIES_KEY);
            for (MachineCapability capability : capabilitySnapshot().capabilities()) {
                if (transferHandler(capability).isEmpty()) continue;
                String key = capability.type().id().toString();
                if (profiles.contains(key)) {
                    autoIOConfig(capability.type()).loadInto(profiles.getCompound(key));
                }
            }
        }
        markAutoIOCacheDirty();
    }

    @Override
    protected MachineAppearanceSpec.TextureSource resolveLinkedAppearance(
            TreeMap<BlockPos, MachineAppearanceSpec.TextureSource> linkedControllers) {
        return linkedControllers.isEmpty() ? DEFAULT_APPEARANCE_SOURCE : linkedControllers.firstEntry().getValue();
    }

    private static final class AutoIOState {
        private final EnumSet<Direction> candidateSides = EnumSet.noneOf(Direction.class);
        private int successCounter;
        private int delay = ServerConfig.autoIoMaxDelayTicks();
        private int ticksUntilTransfer;
    }

}
