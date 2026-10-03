package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** @author howxu <dev@howxu.cn> */
public final class StressOutputBlockEntity extends GeneratingKineticBlockEntity implements MachineComponentTile, CapabilityHost, MachinePort {
    private final StressPortCapability port = new StressPortCapability(this, StressInterfaceKind.OUTPUT,
            () -> capacity, () -> stress, this::contributionChanged);
    private final MachineComponent component = new MachineComponent(StressInterfaceKind.OUTPUT, port.directions());
    private float clientGeneratedRpm;
    private final StressInterfacePersistence recovery = new StressInterfacePersistence();
    private boolean clientPacketState;
    private float clientBaseCapacity;

    public StressOutputBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(StressInterfaceKind.OUTPUT.id()).get(), pos, state);
    }

    @Override public IOPortKind kind() { return StressInterfaceKind.OUTPUT; }
    @Override public MachineComponent provideComponent() { return component; }
    @Override public CapabilitySnapshot capabilitySnapshot() { return port.snapshot(); }
    @Override public void onMachineFormed(BlockPos controllerPos) { port.bind(controllerPos); }
    @Override public void onMachineUnformed(BlockPos controllerPos) { port.unbind(controllerPos); }
    @Override public float calculateStressApplied() { return lastStressApplied = 0F; }
    @Override
    public float calculateAddedStressCapacity() {
        return lastCapacityProvided = recovery.settling() ? 0F
                : (level != null ? level.isClientSide() : clientPacketState) ? clientBaseCapacity : (float) port.baseStress();
    }

    @Override
    public float getGeneratedSpeed() {
        if (recovery != null && recovery.settling()) return recovery.oldGeneratedRpm();
        return (level != null ? level.isClientSide() : clientPacketState) ? clientGeneratedRpm : (float) port.generatedRpm();
    }

    private void contributionChanged() {
        if (level == null || level.isClientSide()) return;
        settleSavedNetwork();
        // Clearing a source must also remove its capacity when Create retains a faster external source.
        if (hasNetwork() && getGeneratedSpeed() == 0F) notifyStressCapacityChange(0F);
        calculateAddedStressCapacity();
        updateGeneratedRotation();
        setChanged();
    }

    @Override public void tick() { settleSavedNetwork(); port.tickOutputGrace(); super.tick(); port.tickAppearance(); port.notifyStateTransition(); }

    @Override public ModelData getModelData() { return port.modelData(); }

    @Override public void initialize() { settleSavedNetwork(); super.initialize(); }

    @Override
    public float getTheoreticalSpeed() {
        return recovery != null && recovery.settling() ? recovery.oldRpm() : super.getTheoreticalSpeed();
    }

    /** Consumes old capacity only in native accounting, never reactivating saved generation.
     * @author howxu <dev@howxu.cn>
     */
    public void settleSavedNetwork() {
        if (!recovery.pending() || level == null || level.isClientSide()) return;
        float oldRpm = recovery.oldRpm();
        network = recovery.networkId();
        KineticNetwork restored = getOrCreateNetwork();
        recovery.settle(restored, this);
        lastStressApplied = 0F;
        lastCapacityProvided = 0F;
        reActivateSource = false;
        if (!hasSource() && port.generatedRpm() == 0D) {
            // Native removal needs the old theoretical speed to find dependants. Generated
            // speed remains zero throughout. Keep this inert member until native reattachment:
            // removing the last loaded member would discard the other members' unloaded ledger.
            setSpeed(oldRpm);
            detachKinetics();
            setSpeed(0F);
            updateSpeed = false;
            onSpeedChanged(oldRpm);
            sendData();
        }
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
        reActivateSource = false;
        super.onChunkUnloaded();
    }

    @Override public void remove() { settleSavedNetwork(); port.clear(); reActivateSource = false; super.remove(); }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        float base = calculateAddedStressCapacity();
        super.write(tag, registries, clientPacket);
        port.writeAppearance(tag);
        if (clientPacket) {
            tag.putFloat("StressBase", base);
            tag.putFloat("GeneratedRpm", getGeneratedSpeed());
        } else recovery.write(tag, getGeneratedSpeed());
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
        clientBaseCapacity = clientPacket ? tag.getFloat("StressBase") : 0F;
        if (clientPacket) clientGeneratedRpm = tag.getFloat("GeneratedRpm");
        else {
            lastStressApplied = 0F;
            lastCapacityProvided = 0F;
            port.networkChanged(null);
            clientGeneratedRpm = 0F;
            reActivateSource = false;
            if (!hasSource()) speed = 0F;
            updateSpeed = true;
        }
    }
}
