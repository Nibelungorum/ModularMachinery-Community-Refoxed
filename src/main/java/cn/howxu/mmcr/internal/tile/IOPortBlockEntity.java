package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.autoio.AutoIOConfig;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.TreeMap;

public abstract class IOPortBlockEntity extends LinkedAppearanceBlockEntity implements MachineComponentTile {
    private static final String AUTO_IO_KEY = "auto_io";
    private final AutoIOConfig autoIOConfig = new AutoIOConfig();
    private boolean autoIOCacheDirty = true;

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

    public AutoIOConfig autoIOConfig() {
        return autoIOConfig;
    }

    public abstract AutoIOCapabilityType autoIOCapabilityType();

    public void toggleAutoIOEnabled() {
        autoIOConfig.setEnabled(!autoIOConfig.enabled());
        markAutoIOCacheDirty();
        setChanged();
    }

    public void toggleAutoIOSide(Direction side) {
        if (side == null) return;
        autoIOConfig.toggleSide(side);
        markAutoIOCacheDirty();
        setChanged();
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
    }

    protected void tick() {
        kind().tick(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        autoIOConfig.save(output.child(AUTO_IO_KEY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        autoIOConfig.loadInto(input.childOrEmpty(AUTO_IO_KEY));
        markAutoIOCacheDirty();
    }

    @Override
    protected Identifier resolveLinkedAppearance(TreeMap<BlockPos, Identifier> linkedControllers) {
        return linkedControllers.get(linkedControllers.firstKey());
    }
}
