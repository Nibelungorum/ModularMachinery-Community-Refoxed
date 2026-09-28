package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.TreeMap;

/**
 * Shared dynamic casing appearance state for machine components linked to formed controllers.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class LinkedAppearanceBlockEntity extends BlockEntity {
    private static final String LINKED_CONTROLLERS_KEY = "LinkedControllers";
    private static final String LINKED_CONTROLLER_X_KEY = "X";
    private static final String LINKED_CONTROLLER_Y_KEY = "Y";
    private static final String LINKED_CONTROLLER_Z_KEY = "Z";
    private static final String LINKED_CONTROLLER_SOURCE_BLOCK_KEY = "SourceBlock";
    private static final String LINKED_CONTROLLER_OVERRIDE_TEXTURE_KEY = "Texture";
    protected static final MachineAppearanceSpec.TextureSource DEFAULT_APPEARANCE_SOURCE =
            MachineAppearanceSpec.defaults().formedPortTextureSource();
    private MachineAppearanceSpec.TextureSource appearanceSource = DEFAULT_APPEARANCE_SOURCE;
    private final TreeMap<BlockPos, MachineAppearanceSpec.TextureSource> linkedControllers = new TreeMap<>(BlockPos::compareTo);
    private int controllerLinkCheckCounter;

    protected LinkedAppearanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public MachineAppearanceSpec.TextureSource appearanceSource() {
        return appearanceSource;
    }

    public ResourceLocation appearanceBaseTexture() {
        ResourceLocation overrideTexture = appearanceSource.overrideTexture();
        return overrideTexture == null ? MMCR.id("block/basic_casing") : overrideTexture;
    }

    public @Nullable BlockPos linkedControllerPos() {
        return linkedControllers.isEmpty() ? null : linkedControllers.firstKey();
    }

    public Set<BlockPos> linkedControllerPositions() {
        return Set.copyOf(linkedControllers.keySet());
    }

    public void linkControllerAppearanceSource(BlockPos controllerPos, @Nullable MachineAppearanceSpec.TextureSource source) {
        linkedControllers.put(controllerPos.immutable(), source == null ? DEFAULT_APPEARANCE_SOURCE : source);
        refreshLinkedAppearance();
    }

    public void linkControllerAppearance(BlockPos controllerPos, @Nullable ResourceLocation texture) {
        linkControllerAppearanceSource(controllerPos, texture == null ? null : new MachineAppearanceSpec.TextureSource(
                DEFAULT_APPEARANCE_SOURCE.blockId(), texture));
    }

    public void unlinkControllerAppearance(BlockPos controllerPos) {
        if (controllerPos == null || linkedControllers.remove(controllerPos) == null) return;
        refreshLinkedAppearance();
    }

    public void setAppearanceSource(@Nullable MachineAppearanceSpec.TextureSource source) {
        MachineAppearanceSpec.TextureSource resolvedSource = source == null ? DEFAULT_APPEARANCE_SOURCE : source;
        if (resolvedSource.equals(appearanceSource)) {
            return;
        }
        appearanceSource = resolvedSource;
        setChanged();
        if (level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        }
    }

    public void setAppearanceBaseTexture(@Nullable ResourceLocation texture) {
        setAppearanceSource(texture == null ? null : new MachineAppearanceSpec.TextureSource(
                DEFAULT_APPEARANCE_SOURCE.blockId(), texture));
    }

    public void resetAppearanceSource() {
        linkedControllers.clear();
        refreshLinkedAppearance();
    }

    public void resetAppearanceBaseTexture() {
        resetAppearanceSource();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var controllers = output.childrenList(LINKED_CONTROLLERS_KEY);
        for (var entry : linkedControllers.entrySet()) {
            var controller = entry.getKey();
            var controllerOutput = controllers.addChild();
            controllerOutput.putInt(LINKED_CONTROLLER_X_KEY, controller.getX());
            controllerOutput.putInt(LINKED_CONTROLLER_Y_KEY, controller.getY());
            controllerOutput.putInt(LINKED_CONTROLLER_Z_KEY, controller.getZ());
            controllerOutput.putString(LINKED_CONTROLLER_SOURCE_BLOCK_KEY, entry.getValue().blockId().toString());
            ResourceLocation overrideTexture = entry.getValue().overrideTexture();
            if (overrideTexture != null) {
                controllerOutput.putString(LINKED_CONTROLLER_OVERRIDE_TEXTURE_KEY, overrideTexture.toString());
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        linkedControllers.clear();
        for (var controllerInput : input.childrenListOrEmpty(LINKED_CONTROLLERS_KEY)) {
            BlockPos controllerPos = new BlockPos(
                    controllerInput.getIntOr(LINKED_CONTROLLER_X_KEY, 0),
                    controllerInput.getIntOr(LINKED_CONTROLLER_Y_KEY, 0),
                    controllerInput.getIntOr(LINKED_CONTROLLER_Z_KEY, 0));
            String sourceBlock = controllerInput.getStringOr(LINKED_CONTROLLER_SOURCE_BLOCK_KEY,
                    DEFAULT_APPEARANCE_SOURCE.blockId().toString());
            String overrideTexture = controllerInput.getStringOr(LINKED_CONTROLLER_OVERRIDE_TEXTURE_KEY, "");
            linkedControllers.put(controllerPos, new MachineAppearanceSpec.TextureSource(
                    sourceBlock.isBlank() ? DEFAULT_APPEARANCE_SOURCE.blockId() : ResourceLocation.parse(sourceBlock),
                    overrideTexture.isBlank() ? null : ResourceLocation.parse(overrideTexture)));
        }
        appearanceSource = resolveLinkedAppearance(linkedControllers);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(MachineModelDataKeys.PORT_BASE_TEXTURE, appearanceSource.overrideTexture())
                .with(MachineModelDataKeys.PORT_TEXTURE_SOURCE, appearanceSource)
                .with(MachineModelDataKeys.PORT_LINKED, !linkedControllers.isEmpty())
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

    protected void maintainControllerLink() {
        if (level == null || level.isClientSide() || linkedControllers.isEmpty()) return;
        if (Math.floorMod(controllerLinkCheckCounter++ + worldPosition.asLong(),
                ServerConfig.linkAppearanceCheckIntervalTicks()) != 0) return;
        boolean changed = linkedControllers.entrySet().removeIf(entry -> {
            BlockPos controllerPos = entry.getKey();
            if (!level.hasChunkAt(controllerPos)) return false;
            return !(level.getBlockState(controllerPos).getBlock() instanceof MachineControllerBlock)
                    || !(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)
                    || !controller.runtimeSnapshot().structure().formed()
                    || !(controller.runtimeSnapshot().linkedPortPositions().contains(worldPosition)
                    || controller.hasActiveNetworkInterface(worldPosition));
        });
        if (changed) {
            refreshLinkedAppearance();
        }
    }

    protected MachineAppearanceSpec.TextureSource resolveLinkedAppearance(
            TreeMap<BlockPos, MachineAppearanceSpec.TextureSource> linkedControllers) {
        return linkedControllers.size() == 1
                ? linkedControllers.firstEntry().getValue()
                : DEFAULT_APPEARANCE_SOURCE;
    }

    private void refreshLinkedAppearance() {
        MachineAppearanceSpec.TextureSource resolved = linkedControllers.isEmpty()
                ? DEFAULT_APPEARANCE_SOURCE
                : resolveLinkedAppearance(linkedControllers);
        boolean appearanceChanged = !resolved.equals(appearanceSource);
        setAppearanceSource(resolved);
        if (!appearanceChanged && level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        }
        setChanged();
    }
}
