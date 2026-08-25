package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.autoio.AutoIOConfig;
import cn.howxu.mmcr.api.capability.transfer.TransferPolicy;
import cn.howxu.mmcr.internal.autoio.CapabilityTransferPolicies;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.TreeMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public abstract class IOPortBlockEntity extends LinkedAppearanceBlockEntity implements MachineComponentTile, CapabilityHost {
    private static final String AUTO_IO_KEY = "auto_io";
    private static final String AUTO_IO_CAPABILITIES_KEY = "auto_io_capabilities";
    private static final int AUTO_IO_MIN_DELAY = 5;
    private static final int AUTO_IO_MAX_DELAY = 60;
    private final AutoIOConfig legacyAutoIOConfig = new AutoIOConfig();
    private final Map<CapabilityType, AutoIOConfig> autoIOConfigs = new LinkedHashMap<>();
    private boolean autoIOCacheDirty = true;
    private final Map<CapabilityType, AutoIOState> autoIOStates = new LinkedHashMap<>();

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
        setChanged();
        sendStorageSnapshot();
    }

    protected void sendStorageSnapshot() {
        if (level != null && !level.isClientSide()) PktPortStorageSyncPayload.sendToViewers(this);
    }

    @Override
    public abstract CapabilitySnapshot capabilitySnapshot();

    public ResourceStorage<ItemResource> itemStorage() {
        throw new IllegalStateException("Port does not expose item storage: " + kind().id());
    }

    public ResourceStorage<FluidResource> fluidStorage() {
        throw new IllegalStateException("Port does not expose fluid storage: " + kind().id());
    }

    public LongValueStorage getEnergyStorage() {
        throw new IllegalStateException("Port does not expose energy storage: " + kind().id());
    }

    public AutoIOConfig autoIOConfig() {
        List<MachineCapability> capabilities = capabilitySnapshot().capabilities();
        return capabilities.size() == 1 ? autoIOConfig(capabilities.getFirst().type()) : legacyAutoIOConfig;
    }

    public AutoIOConfig autoIOConfig(CapabilityType type) {
        if (type == null) throw new IllegalArgumentException("Capability type must not be null");
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
        return capability == null ? AUTO_IO_MAX_DELAY
                : autoIOStates.getOrDefault(capability.type(), new AutoIOState()).delay;
    }

    public boolean hasAutoIOWork() {
        return hasAutoIOTransferWork();
    }

    protected boolean hasAutoIOTransferWork() {
        for (MachineCapability capability : capabilitySnapshot().capabilities()) {
            TransferPolicy policy = CapabilityTransferPolicies.policyFor(capability).orElse(null);
            if (policy != null && policy.hasWork(capability)) return true;
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
        if (type == null) return;
        AutoIOConfig config = autoIOConfig(type);
        if (config.enabled() == enabled) return;
        config.setEnabled(enabled);
        markAutoIOConfigChanged();
    }

    public boolean isAutoIOSideExposed(Direction side) {
        return isAutoIOSideExposed(autoIOCapabilityType(), side);
    }

    public boolean isAutoIOSideExposed(CapabilityType type, Direction side) {
        return side == null || type != null && capability(type) != null && autoIOConfig(type).isSideEnabled(side);
    }

    public void toggleAutoIOEnabled() {
        setAutoIOEnabled(!autoIOConfig().enabled());
    }

    public void setAutoIOSide(Direction side, boolean enabled) {
        setAutoIOSide(autoIOCapabilityType(), side, enabled);
    }

    public void setAutoIOSide(CapabilityType type, Direction side, boolean enabled) {
        if (type == null || side == null) return;
        AutoIOConfig config = autoIOConfig(type);
        if (config.isSideEnabled(side) == enabled) return;
        config.setSide(side, enabled);
        markAutoIOConfigChanged();
    }

    public void setAllAutoIOSides(boolean enabled) {
        setAllAutoIOSides(autoIOCapabilityType(), enabled);
    }

    public void setAllAutoIOSides(CapabilityType type, boolean enabled) {
        if (type == null) return;
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
        return new MachineComponent(kind(), ioType());
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
            AutoIOConfig config = autoIOConfig(capability.type());
            TransferPolicy policy = CapabilityTransferPolicies.policyFor(capability).orElse(null);
            if (!config.enabled() || config.enabledSides().isEmpty() || policy == null) continue;
            AutoIOState state = autoIOStates.computeIfAbsent(capability.type(), ignored -> new AutoIOState());
            if (rebuiltCandidates) rebuildAutoIOCandidates(capability, policy, config, state);
            if (state.candidateSides.isEmpty()) {
                if (rebuiltCandidates) {
                    state.ticksUntilTransfer = AUTO_IO_MIN_DELAY - 1;
                    continue;
                }
                if (state.ticksUntilTransfer > 0) {
                    state.ticksUntilTransfer--;
                    continue;
                }
                rebuildAutoIOCandidates(capability, policy, config, state);
                if (state.candidateSides.isEmpty()) continue;
            }
            if (!policy.hasWork(capability)) continue;
            if (state.ticksUntilTransfer > 0) {
                state.ticksUntilTransfer--;
                continue;
            }

            boolean moved = false;
            for (Direction side : state.candidateSides) {
                moved |= policy.transfer(capability, side).successful();
            }
            if (moved) incrementAutoIOSuccess(state);
            else decrementAutoIOSuccess(state);
            state.ticksUntilTransfer = state.delay - 1;
        }
    }

    private void rebuildAutoIOCandidates(MachineCapability capability, TransferPolicy policy,
                                         AutoIOConfig config, AutoIOState state) {
        state.candidateSides.clear();
        for (Direction side : config.enabledSides()) {
            if (policy.hasAdjacentTarget(capability, side)) state.candidateSides.add(side);
        }
    }

    private @Nullable MachineCapability autoIOCapability() {
        List<MachineCapability> capabilities = capabilitySnapshot().capabilities();
        for (MachineCapability capability : capabilities) {
            if (CapabilityTransferPolicies.policyFor(capability).isPresent()) return capability;
        }
        return capabilities.size() == 1 ? capabilities.getFirst() : null;
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
        if (level == null || level.isClientSide() || ioType() != IOType.INPUT || isUsedByActiveRecipe()) return false;
        MachineCapability capability = capability(type);
        if (capability == null || capability.ioType() != IOType.INPUT) return false;
        TransferPolicy policy = capability == null ? null : CapabilityTransferPolicies.policyFor(capability).orElse(null);
        if (capability == null || policy == null) return false;
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        for (int index = sides.size() - 1; index > 0; index--) {
            int swapIndex = level.getRandom().nextInt(index + 1);
            Direction side = sides.get(index);
            sides.set(index, sides.get(swapIndex));
            sides.set(swapIndex, side);
        }
        boolean moved = false;
        for (Direction side : sides) {
            moved |= policy.eject(capability, side).successful();
        }
        return moved;
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
        int max = (AUTO_IO_MAX_DELAY - AUTO_IO_MIN_DELAY) / 5;
        if (state.successCounter < max) state.successCounter++;
        state.delay = Math.max(AUTO_IO_MIN_DELAY, AUTO_IO_MAX_DELAY - state.successCounter * 5);
    }

    private void decrementAutoIOSuccess(AutoIOState state) {
        if (state.successCounter > 0) state.successCounter--;
        state.delay = Math.max(AUTO_IO_MIN_DELAY, AUTO_IO_MAX_DELAY - state.successCounter * 5);
    }

    protected void tick() {
        kind().tick(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        autoIOConfig().save(output.child(AUTO_IO_KEY));
        ValueOutput profiles = output.child(AUTO_IO_CAPABILITIES_KEY);
        for (MachineCapability capability : capabilitySnapshot().capabilities()) {
            autoIOConfig(capability.type()).save(profiles.child(capability.type().id().toString()));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        legacyAutoIOConfig.loadInto(input.childOrEmpty(AUTO_IO_KEY));
        autoIOConfigs.clear();
        boolean loadedProfile = false;
        Optional<ValueInput> profiles = input.child(AUTO_IO_CAPABILITIES_KEY);
        if (profiles.isPresent()) {
            for (MachineCapability capability : capabilitySnapshot().capabilities()) {
                Optional<ValueInput> profile = profiles.get().child(capability.type().id().toString());
                if (profile.isPresent()) {
                    autoIOConfig(capability.type()).loadInto(profile.get());
                    loadedProfile = true;
                }
            }
        }
        if (!loadedProfile && capabilitySnapshot().capabilities().size() == 1) {
            autoIOConfigs.put(capabilitySnapshot().capabilities().getFirst().type(), legacyAutoIOConfig);
        }
        markAutoIOCacheDirty();
    }

    private static final class AutoIOState {
        private final EnumSet<Direction> candidateSides = EnumSet.noneOf(Direction.class);
        private int successCounter;
        private int delay = AUTO_IO_MAX_DELAY;
        private int ticksUntilTransfer;
    }

    @Override
    protected Identifier resolveLinkedAppearance(TreeMap<BlockPos, Identifier> linkedControllers) {
        return linkedControllers.get(linkedControllers.firstKey());
    }
}
