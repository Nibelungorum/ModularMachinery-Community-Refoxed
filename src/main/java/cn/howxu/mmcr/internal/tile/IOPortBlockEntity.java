package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
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

import java.util.Set;
import java.util.TreeMap;

public abstract class IOPortBlockEntity extends BlockEntity implements MachineComponentTile {
    private static final Identifier DEFAULT_APPEARANCE_BASE_TEXTURE = MMCR.id("block/basic_casing");
    private static final String APPEARANCE_BASE_TEXTURE_KEY = "AppearanceBaseTexture";
    private static final String LINKED_CONTROLLERS_KEY = "LinkedControllers";
    private static final String LINKED_CONTROLLER_X_KEY = "X";
    private static final String LINKED_CONTROLLER_Y_KEY = "Y";
    private static final String LINKED_CONTROLLER_Z_KEY = "Z";
    private static final String LINKED_CONTROLLER_TEXTURE_KEY = "Texture";
    private static final int CONTROLLER_LINK_CHECK_INTERVAL_TICKS = 40;

    private Identifier appearanceBaseTexture = DEFAULT_APPEARANCE_BASE_TEXTURE;
    private final TreeMap<BlockPos, Identifier> linkedControllers = new TreeMap<>(BlockPos::compareTo);
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
        return linkedControllers.isEmpty() ? null : linkedControllers.firstKey();
    }

    public Set<BlockPos> linkedControllerPositions() {
        return Set.copyOf(linkedControllers.keySet());
    }

    public void linkControllerAppearance(BlockPos controllerPos, Identifier texture) {
        linkedControllers.put(controllerPos.immutable(), texture == null ? DEFAULT_APPEARANCE_BASE_TEXTURE : texture);
        refreshLinkedAppearance();
    }

    public void unlinkControllerAppearance(BlockPos controllerPos) {
        if (controllerPos == null || linkedControllers.remove(controllerPos) == null) return;
        refreshLinkedAppearance();
    }

    @Deprecated
    public void bindControllerAppearance(BlockPos controllerPos, Identifier texture) {
        if (controllerPos != null) {
            linkControllerAppearance(controllerPos, texture);
        }
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
        linkedControllers.clear();
        refreshLinkedAppearance();
    }

    @Override
    public MachineComponent provideComponent() {
        return new MachineComponent(kind(), ioType());
    }

    @Override
    public ComponentClaimPolicy claimPolicy() {
        return ComponentClaimPolicy.SHARED_SERIALIZED;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(APPEARANCE_BASE_TEXTURE_KEY, appearanceBaseTexture.toString());
        var controllers = output.childrenList(LINKED_CONTROLLERS_KEY);
        for (var entry : linkedControllers.entrySet()) {
            var controller = entry.getKey();
            var controllerOutput = controllers.addChild();
            controllerOutput.putInt(LINKED_CONTROLLER_X_KEY, controller.getX());
            controllerOutput.putInt(LINKED_CONTROLLER_Y_KEY, controller.getY());
            controllerOutput.putInt(LINKED_CONTROLLER_Z_KEY, controller.getZ());
            controllerOutput.putString(LINKED_CONTROLLER_TEXTURE_KEY, entry.getValue().toString());
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
            String texture = controllerInput.getStringOr(LINKED_CONTROLLER_TEXTURE_KEY, DEFAULT_APPEARANCE_BASE_TEXTURE.toString());
            linkedControllers.put(controllerPos, texture.isBlank() ? DEFAULT_APPEARANCE_BASE_TEXTURE : Identifier.parse(texture));
        }
        refreshLinkedAppearance();
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
        if (level == null || level.isClientSide() || linkedControllers.isEmpty()) return;
        if (Math.floorMod(controllerLinkCheckCounter++ + worldPosition.asLong(), CONTROLLER_LINK_CHECK_INTERVAL_TICKS) != 0) return;
        boolean changed = linkedControllers.entrySet().removeIf(entry -> {
            BlockPos controllerPos = entry.getKey();
            return !(level.getBlockState(controllerPos).getBlock() instanceof MachineControllerBlock)
                    || !(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)
                    || !controller.isFormed()
                    || !controller.hasLinkedPort(worldPosition);
        });
        if (changed) {
            refreshLinkedAppearance();
        }
    }

    private void refreshLinkedAppearance() {
        setAppearanceBaseTexture(linkedControllers.isEmpty()
                ? DEFAULT_APPEARANCE_BASE_TEXTURE
                : linkedControllers.get(linkedControllers.firstKey()));
        setChanged();
    }
}
