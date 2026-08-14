package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Objects;
import java.util.Optional;

/**
 * Persists the active host/module controller coordinates for a module coupler.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ModuleCouplerBlockEntity extends BlockEntity {
    private static final String HOST_KEY = "host";
    private static final String MODULE_KEY = "module";
    private static final String DIMENSION_KEY = "dimension";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";

    private GlobalPos connectedHost;
    private GlobalPos connectedModule;

    public ModuleCouplerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MODULE_BRIDGE.get(), pos, state);
    }

    public Optional<GlobalPos> connectedHost() {
        return Optional.ofNullable(connectedHost);
    }

    public Optional<GlobalPos> connectedModule() {
        return Optional.ofNullable(connectedModule);
    }

    public void setConnection(GlobalPos host, GlobalPos module) {
        if (host == null || module == null) throw new IllegalArgumentException("Module coupler connection requires host and module positions");
        if (Objects.equals(connectedHost, host) && Objects.equals(connectedModule, module)) return;
        connectedHost = host;
        connectedModule = module;
        requestRefresh();
    }

    public void clearConnection() {
        if (connectedHost == null && connectedModule == null) return;
        connectedHost = null;
        connectedModule = null;
        requestRefresh();
    }

    public void requestRefresh() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeGlobalPos(output.child(HOST_KEY), connectedHost);
        writeGlobalPos(output.child(MODULE_KEY), connectedModule);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        connectedHost = readGlobalPos(input.childOrEmpty(HOST_KEY));
        connectedModule = readGlobalPos(input.childOrEmpty(MODULE_KEY));
        if (connectedHost == null || connectedModule == null) {
            connectedHost = null;
            connectedModule = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        super.onDataPacket(net, input);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static void writeGlobalPos(ValueOutput output, GlobalPos pos) {
        if (pos == null) return;
        output.putString(DIMENSION_KEY, pos.dimension().identifier().toString());
        output.putInt(X_KEY, pos.pos().getX());
        output.putInt(Y_KEY, pos.pos().getY());
        output.putInt(Z_KEY, pos.pos().getZ());
    }

    private static GlobalPos readGlobalPos(ValueInput input) {
        String dimension = input.getStringOr(DIMENSION_KEY, "");
        if (dimension.isBlank()) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
        return GlobalPos.of(key, new BlockPos(
                input.getIntOr(X_KEY, 0),
                input.getIntOr(Y_KEY, 0),
                input.getIntOr(Z_KEY, 0)));
    }
}
