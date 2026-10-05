package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.pressure.PressureTier;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/** One official native store shared by all recipe and pipe views. @author howxu <dev@howxu.cn> */
public final class AirPortBlockEntity extends IOPortBlockEntity {
    private final AirInterfaceKind kind;
    private final IAirHandlerMachine airHandler;
    private final AirPortCapability capability;
    private final CapabilitySnapshot snapshot;
    private int observedAir;
    private int observedVolume;

    public AirPortBlockEntity(BlockPos pos, BlockState state, AirInterfaceKind kind) {
        super(typeForKind(kind), pos, state);
        this.kind = kind;
        airHandler = PneumaticRegistry.getInstance().getAirHandlerMachineFactory()
                .createAirHandler(PressureTier.TIER_TWO, 10_000);
        airHandler.setConnectableFaces(EnumSet.allOf(Direction.class));
        capability = new AirPortCapability(this);
        snapshot = new CapabilitySnapshot(List.of(capability));
        captureBaseline();
    }

    public IAirHandlerMachine airHandler() { return airHandler; }
    public AirPortCapability airCapability() { return capability; }
    @Override public AirInterfaceKind kind() { return kind; }
    @Override public IOType ioType() { return kind.ioType(); }
    @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }

    public void updateConnections() {
        airHandler.setConnectableFaces(EnumSet.allOf(Direction.class));
        if (level != null && !level.isClientSide()) invalidateCapabilities();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateConnections();
        captureBaseline();
    }

    private void captureBaseline() {
        observedAir = airHandler.getAir();
        observedVolume = airHandler.getVolume();
        initializeAvailabilityBaseline();
    }

    /** Also observes direct addAir calls by an external handler, without replacing the native handler. */
    public void observeAirChanges() {
        if (level == null || level.isClientSide() || isRemoved()) return;
        int air = airHandler.getAir();
        int volume = airHandler.getVolume();
        if (air == observedAir && volume == observedVolume) return;
        observedAir = air;
        observedVolume = volume;
        notifyStorageChanged();
    }

    @Override
    protected void sendStorageSnapshot() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick() {
        if (level == null || level.isClientSide() || isRemoved()) return;
        airHandler.tick(this);
        observeAirChanges();
    }

    public void clientTick() {
        if (level != null && level.isClientSide() && !isRemoved()) airHandler.tick(this);
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        CompoundTag nativeState = ((CompoundTag) airHandler.serializeNBT()).copy();
        nativeState.putInt("Volume", airHandler.getVolume());
        output.put("air_handler", nativeState);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            CompoundTag nativeState = input.getCompound("air_handler");
            // Old saves only contain native Air/Leaking; retain their original fixed volume.
            int volume = nativeState.contains("Volume", Tag.TAG_INT) ? nativeState.getInt("Volume") : 10_000;
            airHandler.setBaseVolume(volume > 0 ? volume : 10_000);
            airHandler.deserializeNBT(nativeState);
        } finally {
            endLoadingAdditional();
            captureBaseline();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag output = super.getUpdateTag(registries);
        CompoundTag nativeState = output.getCompound("air_handler");
        nativeState.putFloat("DangerPressure", airHandler.getDangerPressure());
        nativeState.putFloat("CriticalPressure", airHandler.getCriticalPressure());
        return output;
    }

    @Override
    public void handleUpdateTag(CompoundTag input, HolderLookup.Provider registries) {
        validateClientUpdate(input);
        super.handleUpdateTag(input, registries);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        validateClientUpdate(packet.getTag());
        super.onDataPacket(net, packet, registries);
    }

    private void validateClientUpdate(CompoundTag input) {
        CompoundTag state = input.getCompound("air_handler");
        if (!state.contains("Air", Tag.TAG_INT) || !state.contains("Volume", Tag.TAG_INT)
                || !state.contains("DangerPressure", Tag.TAG_FLOAT) || !state.contains("CriticalPressure", Tag.TAG_FLOAT)) {
            throw new IllegalArgumentException("Incomplete air sync state");
        }
        validateClientAirState(state.getInt("Volume"), state.getFloat("DangerPressure"), state.getFloat("CriticalPressure"));
    }

    void validateClientAirState(int volume, float danger, float critical) {
        if (level != null && !level.isClientSide()) throw new IllegalStateException("Air sync is client-bound");
        if (volume <= 0 || Float.compare(danger, airHandler.getDangerPressure()) != 0
                || Float.compare(critical, airHandler.getCriticalPressure()) != 0) {
            throw new IllegalArgumentException("Invalid air sync state");
        }
    }
}
