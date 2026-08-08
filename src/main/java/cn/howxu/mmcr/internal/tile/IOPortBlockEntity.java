package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public abstract class IOPortBlockEntity extends BlockEntity implements MachineComponentTile {
    private static final Identifier DEFAULT_APPEARANCE_BASE_TEXTURE = MMCR.id("block/basic_casing");
    private static final String APPEARANCE_BASE_TEXTURE_KEY = "AppearanceBaseTexture";
    private static final String LINKED_CONTROLLER_X_KEY = "LinkedControllerX";
    private static final String LINKED_CONTROLLER_Y_KEY = "LinkedControllerY";
    private static final String LINKED_CONTROLLER_Z_KEY = "LinkedControllerZ";
    private static final String HAS_LINKED_CONTROLLER_KEY = "HasLinkedController";
    private static final int CONTROLLER_LINK_CHECK_INTERVAL_TICKS = 40;

    private Identifier appearanceBaseTexture = DEFAULT_APPEARANCE_BASE_TEXTURE;
    private @Nullable BlockPos linkedControllerPos;
    private int controllerLinkCheckCounter;

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
                || kind.energyHatchSize().isPresent() != fallback.energyHatchSize().isPresent()) {
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

    public Identifier appearanceBaseTexture() {
        return appearanceBaseTexture;
    }

    public @Nullable BlockPos linkedControllerPos() {
        return linkedControllerPos;
    }

    public void bindControllerAppearance(BlockPos controllerPos, Identifier texture) {
        linkedControllerPos = controllerPos == null ? null : controllerPos.immutable();
        setAppearanceBaseTexture(texture);
        setChanged();
    }

    public void setAppearanceBaseTexture(Identifier texture) {
        Identifier resolvedTexture = texture == null ? DEFAULT_APPEARANCE_BASE_TEXTURE : texture;
        if (resolvedTexture.equals(appearanceBaseTexture)) {
            return;
        }
        appearanceBaseTexture = resolvedTexture;
        setChanged();
        if (level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        }
    }

    public void resetAppearanceBaseTexture() {
        linkedControllerPos = null;
        setAppearanceBaseTexture(DEFAULT_APPEARANCE_BASE_TEXTURE);
        setChanged();
    }

    @Override
    public MachineComponent provideComponent() {
        return new MachineComponent(kind(), ioType());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(APPEARANCE_BASE_TEXTURE_KEY, appearanceBaseTexture.toString());
        output.putBoolean(HAS_LINKED_CONTROLLER_KEY, linkedControllerPos != null);
        if (linkedControllerPos != null) {
            output.putInt(LINKED_CONTROLLER_X_KEY, linkedControllerPos.getX());
            output.putInt(LINKED_CONTROLLER_Y_KEY, linkedControllerPos.getY());
            output.putInt(LINKED_CONTROLLER_Z_KEY, linkedControllerPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String texture = input.getStringOr(APPEARANCE_BASE_TEXTURE_KEY, DEFAULT_APPEARANCE_BASE_TEXTURE.toString());
        appearanceBaseTexture = texture.isBlank() ? DEFAULT_APPEARANCE_BASE_TEXTURE : Identifier.parse(texture);
        if (input.getBooleanOr(HAS_LINKED_CONTROLLER_KEY, false)) {
            linkedControllerPos = new BlockPos(
                    input.getIntOr(LINKED_CONTROLLER_X_KEY, 0),
                    input.getIntOr(LINKED_CONTROLLER_Y_KEY, 0),
                    input.getIntOr(LINKED_CONTROLLER_Z_KEY, 0));
        } else {
            linkedControllerPos = null;
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(MachineModelDataKeys.PORT_BASE_TEXTURE, appearanceBaseTexture)
                .build();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        requestModelDataUpdate();
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        super.onDataPacket(net, input);
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void serverTick() {
        tick();
        maintainControllerLink();
    }

    protected void tick() {
        kind().tick(this);
    }

    private void maintainControllerLink() {
        if (level == null || level.isClientSide() || linkedControllerPos == null) return;
        if (Math.floorMod(controllerLinkCheckCounter++ + worldPosition.asLong(), CONTROLLER_LINK_CHECK_INTERVAL_TICKS) != 0) return;
        if (!(level.getBlockState(linkedControllerPos).getBlock() instanceof MachineControllerBlock)
                || !(level.getBlockEntity(linkedControllerPos) instanceof MachineControllerBlockEntity controller)
                || !controller.isFormed()
                || !controller.hasLinkedPort(worldPosition)) {
            resetAppearanceBaseTexture();
        }
    }
}
