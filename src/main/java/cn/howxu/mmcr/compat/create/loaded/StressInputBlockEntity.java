package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** @author howxu <dev@howxu.cn> */
public final class StressInputBlockEntity extends KineticBlockEntity implements MachineComponentTile, CapabilityHost, MachinePort {
    private final StressPortCapability port = new StressPortCapability(this, StressInterfaceKind.INPUT,
            () -> capacity, () -> stress, this::contributionChanged);
    private final MachineComponent component = new MachineComponent(StressInterfaceKind.INPUT, port.directions());
    private final StressInterfacePersistence recovery = new StressInterfacePersistence();
    private boolean clientPacketState;
    private float clientBaseStress;

    public StressInputBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(StressInterfaceKind.INPUT.id()).get(), pos, state);
    }

    @Override public IOPortKind kind() { return StressInterfaceKind.INPUT; }
    @Override public MachineComponent provideComponent() { return component; }
    @Override public CapabilitySnapshot capabilitySnapshot() { return port.snapshot(); }
    @Override public void onMachineFormed(BlockPos controllerPos) { port.bind(controllerPos); }
    @Override public void onMachineUnformed(BlockPos controllerPos) { port.unbind(controllerPos); }

    @Override
    public float calculateStressApplied() {
        return lastStressApplied = recovery.settling() ? 0F
                : (level != null ? level.isClientSide() : clientPacketState) ? clientBaseStress : (float) port.baseStress();
    }

    @Override public float calculateAddedStressCapacity() { return lastCapacityProvided = 0F; }

    private void contributionChanged() {
        if (level == null || level.isClientSide()) return;
        settleSavedNetwork();
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        else calculateStressApplied();
        setChanged();
        sendData();
    }

    @Override public void tick() { settleSavedNetwork(); super.tick(); port.tickAppearance(); port.notifyStateTransition(); }

    @Override public ModelData getModelData() { return port.modelData(); }

    @Override public void initialize() { settleSavedNetwork(); super.initialize(); }

    @Override
    public float getTheoreticalSpeed() {
        return recovery != null && recovery.settling() ? recovery.oldRpm() : super.getTheoreticalSpeed();
    }

    /** Settles saved native debit before propagation or recipe contribution changes.
     * @author howxu <dev@howxu.cn>
     */
    public void settleSavedNetwork() {
        if (!recovery.pending() || level == null || level.isClientSide()) return;
        network = recovery.networkId();
        KineticNetwork restored = getOrCreateNetwork();
        recovery.settle(restored, this);
        lastStressApplied = 0F;
        lastCapacityProvided = 0F;
        port.networkChanged(restored);
        restored.updateNetwork();
        restored.sync();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (port != null) port.notifyStateTransition();
    }

    @Override
    public void updateFromNetwork(float capacity, float stress, int size) {
        super.updateFromNetwork(capacity, stress, size);
        if (port != null) port.notifyStateTransition();
    }

    @Override
    public void setNetwork(Long network) {
        if (recovery != null) settleSavedNetwork();
        super.setNetwork(network);
        if (port != null) port.networkChanged(hasNetwork() ? getOrCreateNetwork() : null);
    }

    @Override
    public void onChunkUnloaded() {
        settleSavedNetwork();
        port.clear();
        super.onChunkUnloaded();
    }

    @Override public void remove() { settleSavedNetwork(); port.clear(); super.remove(); }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        float base = calculateStressApplied();
        super.write(tag, registries, clientPacket);
        port.writeAppearance(tag);
        if (clientPacket) tag.putFloat("StressBase", base);
        else recovery.write(tag, 0F);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (!clientPacket) {
            port.clear();
            if (level != null && !level.isClientSide() && hasNetwork()
                    && (!tag.contains("Network") || network != tag.getCompound("Network").getLong("Id"))) setNetwork(null);
            recovery.read(wasMoved ? new CompoundTag() : tag);
        }
        super.read(tag, registries, clientPacket);
        port.readAppearance(tag);
        clientPacketState = clientPacket;
        clientBaseStress = clientPacket ? tag.getFloat("StressBase") : 0F;
        if (!clientPacket) {
            lastStressApplied = 0F;
            lastCapacityProvided = 0F;
            port.networkChanged(null);
            updateSpeed = true;
        }
    }
}
