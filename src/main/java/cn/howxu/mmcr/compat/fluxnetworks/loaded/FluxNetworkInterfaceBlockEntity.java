package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkData;
import sonar.fluxnetworks.common.connection.ServerFluxNetwork;
import sonar.fluxnetworks.common.device.TileFluxDevice;

/** A native network device with one exclusive MMCR controller association.
 * @author howxu <dev@howxu.cn>
 */
public abstract class FluxNetworkInterfaceBlockEntity extends TileFluxDevice
        implements MachineComponentTile, CapabilityHost, MachinePort {
    private final IOPortKind kind;
    private final MachineComponent component;
    protected BlockPos controllerPos;
    private MachineAppearanceSpec.TextureSource appearanceSource = MachineAppearanceSpec.defaults().formedPortTextureSource();
    private int appearanceCheck;
    private boolean appearanceRefreshPending = true;

    protected FluxNetworkInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(ModBlockEntities.BES.get(kind.id()).get(), pos, state);
        this.kind = kind;
        component = new MachineComponent(kind, CapabilityDirections.of(kind.ioType()));
    }

    @Override public IOPortKind kind() { return kind; }
    @Override public MachineComponent provideComponent() { return component; }
    @Override public ItemStack getDisplayStack() { return new ItemStack(ModBlocks.BLOCKS.get(kind.id()).get()); }
    public MachineAppearanceSpec.TextureSource appearanceSource() { return appearanceSource; }

    @Override
    public void onMachineFormed(BlockPos pos) {
        checkServerThread();
        if (pos.equals(controllerPos)) return;
        controllerPos = pos.immutable();
        // The forming controller publishes its new structure after this callback.
        appearanceRefreshPending = true;
        associationChanged();
    }

    @Override
    public void onMachineUnformed(BlockPos pos) {
        checkServerThread();
        if (!pos.equals(controllerPos)) return;
        controllerPos = null;
        appearanceSource = MachineAppearanceSpec.defaults().formedPortTextureSource();
        associationChanged();
    }

    private void associationChanged() {
        setChanged();
        if (level != null && !level.isClientSide() && !isRemoved()) sendBlockUpdate();
    }

    protected boolean acceptsNetworkEnergy() {
        checkServerThread();
        return level != null && !level.isClientSide() && !isRemoved() && linkedController() != null && getNetwork().isValid();
    }

    private MachineControllerBlockEntity linkedController() {
        if (controllerPos == null || level == null || !level.hasChunkAt(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller
                && controller.currentStructureSnapshot().formed()
                && controller.runtimeSnapshot().linkedPortPositions().contains(worldPosition) ? controller : null;
    }

    protected void checkServerThread() {
        if (level != null && !level.isClientSide() && level.getServer() != null && !level.getServer().isSameThread()) {
            throw new IllegalStateException("Flux operation outside server thread");
        }
    }

    protected void energyChanged() {
        checkServerThread();
        if (level == null || level.isClientSide() || isRemoved()) return;
        markEnergyChanged();
        MachineControllerBlockEntity controller = linkedController();
        if (controller != null) {
            controller.componentRuntime().markCapabilityPresentationChanged(worldPosition);
            controller.resourceAvailabilityNotifier().notifyAvailability(kind.ioType() == IOType.INPUT
                    ? ResourceAvailabilityNotifier.Reason.ENERGY_AVAILABLE : ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY,
                    BuiltinCapabilityDefinitions.ENERGY_TYPE);
        }
    }

    @Override
    public boolean connect(FluxNetwork network) {
        checkServerThread();
        FluxNetwork previous = getNetwork();
        boolean connected = super.connect(network);
        if (connected && previous != getNetwork()) energyChanged();
        return connected;
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        checkServerThread();
        int previousPriority = getTransferHandler().getPriority();
        super.applyImplicitComponents(input);
        if (input.get(FluxDataComponents.FLUX_CONFIG) != null && level != null && !level.isClientSide()) {
            // Native apply only changes the saved ID; a live device also needs its connection queues updated.
            if ((mFlags & FLAG_FIRST_TICKED) != 0) connect(FluxNetworkData.getNetwork(getNetworkID()));
            if (previousPriority != getTransferHandler().getPriority()
                    && getNetwork() instanceof ServerFluxNetwork network) {
                network.markSortConnections();
            }
            mFlags |= FLAG_SETTING_CHANGED;
            energyChanged();
        }
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_UPDATE) {
            CompoundTag metadata = new CompoundTag();
            if (controllerPos != null) metadata.putLong("controller", controllerPos.asLong());
            metadata.putString("source_block", appearanceSource.blockId().toString());
            if (appearanceSource.overrideTexture() != null) metadata.putString("texture", appearanceSource.overrideTexture().toString());
            tag.put("mmcr_port", metadata);
        }
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_UPDATE) {
            CompoundTag metadata = tag.getCompound("mmcr_port");
            controllerPos = metadata.contains("controller") ? BlockPos.of(metadata.getLong("controller")) : null;
            String block = metadata.getString("source_block");
            String texture = metadata.getString("texture");
            appearanceSource = block.isBlank() ? MachineAppearanceSpec.defaults().formedPortTextureSource()
                    : new MachineAppearanceSpec.TextureSource(ResourceLocation.parse(block),
                            texture.isBlank() ? null : ResourceLocation.parse(texture));
            appearanceRefreshPending = true;
            if (level != null && level.isClientSide()) requestModelDataUpdate();
        } else if (type == FluxConstants.NBT_TILE_SETTINGS) {
            energyChanged();
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(MachineModelDataKeys.PORT_BASE_TEXTURE, appearanceSource.overrideTexture())
                .with(MachineModelDataKeys.PORT_TEXTURE_SOURCE, appearanceSource)
                .with(MachineModelDataKeys.PORT_LINKED, controllerPos != null).build();
    }

    private void refreshControllerAppearance() {
        if (controllerPos != null && !level.hasChunkAt(controllerPos)) return;
        MachineAppearanceSpec.TextureSource next = MachineAppearanceSpec.defaults().formedPortTextureSource();
        if (controllerPos != null) {
            MachineControllerBlockEntity controller = linkedController();
            if (controller == null || controller.currentStructureSnapshot().machine() == null) {
                onMachineUnformed(controllerPos);
                return;
            }
            next = controller.currentStructureSnapshot().machine().appearance().formedPortTextureSource();
        }
        if (!next.equals(appearanceSource)) {
            appearanceSource = next;
            associationChanged();
        }
    }

    @Override
    protected void onServerTick() {
        super.onServerTick();
        boolean scheduled = Math.floorMod(appearanceCheck++ + worldPosition.asLong(),
                ServerConfig.linkAppearanceCheckIntervalTicks()) == 0L;
        if (appearanceRefreshPending || scheduled) {
            appearanceRefreshPending = false;
            refreshControllerAppearance();
        }
    }
}
