package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        CompoundTag host = new CompoundTag();
        CompoundTag module = new CompoundTag();
        writeGlobalPos(host, connectedHost);
        writeGlobalPos(module, connectedModule);
        output.put(HOST_KEY, host);
        output.put(MODULE_KEY, module);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        connectedHost = readGlobalPos(input.getCompound(HOST_KEY));
        connectedModule = readGlobalPos(input.getCompound(MODULE_KEY));
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static void writeGlobalPos(CompoundTag output, GlobalPos pos) {
        if (pos == null) return;
        output.putString(DIMENSION_KEY, pos.dimension().location().toString());
        output.putInt(X_KEY, pos.pos().getX());
        output.putInt(Y_KEY, pos.pos().getY());
        output.putInt(Z_KEY, pos.pos().getZ());
    }

    private static GlobalPos readGlobalPos(CompoundTag input) {
        String dimension = input.getString(DIMENSION_KEY);
        if (dimension.isBlank()) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
        return GlobalPos.of(key, new BlockPos(
                input.getInt(X_KEY), input.getInt(Y_KEY), input.getInt(Z_KEY)));
    }
}
