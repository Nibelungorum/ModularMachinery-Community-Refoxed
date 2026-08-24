package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.autoio.AutoIOConfig;
import cn.howxu.mmcr.internal.autoio.AutoIOTransferHandler;
import cn.howxu.mmcr.internal.autoio.AutoIOTransferHandlers;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.TreeMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

public abstract class IOPortBlockEntity extends LinkedAppearanceBlockEntity implements MachineComponentTile, CapabilityHost {
    private static final String AUTO_IO_KEY = "auto_io";
    private static final int AUTO_IO_MIN_DELAY = 5;
    private static final int AUTO_IO_MAX_DELAY = 60;
    private final AutoIOConfig autoIOConfig = new AutoIOConfig();
    private boolean autoIOCacheDirty = true;
    private int autoIOSuccessCounter;
    private int autoIODelay = AUTO_IO_MAX_DELAY;
    private int autoIOTicksUntilTransfer;
    private EnumSet<Direction> autoIOCandidateSides = EnumSet.noneOf(Direction.class);
    private CapabilitySnapshot capabilitySnapshot;

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

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind().capabilityFactories().stream()
                    .map(factory -> factory.create(this))
                    .toList());
        }
        return capabilitySnapshot;
    }

    public AutoIOConfig autoIOConfig() {
        return autoIOConfig;
    }

    public abstract AutoIOCapabilityType autoIOCapabilityType();

    public int autoIoTransferLimit() {
        return 64;
    }

    public int autoIOCandidateCount() {
        return autoIOCandidateSides.size();
    }

    public int autoIODelay() {
        return autoIODelay;
    }

    public boolean hasAutoIOWork() {
        return hasAutoIOTransferWork();
    }

    protected boolean hasAutoIOTransferWork() {
        return true;
    }

    public record AdjacentSide(Direction side, BlockState state, ItemStack icon, Component name) {
    }

    public AdjacentSide adjacentSide(Direction side) {
        BlockState adjacentState = level == null || side == null
                ? Blocks.AIR.defaultBlockState()
                : level.getBlockState(worldPosition.relative(side));
        ItemStack icon = adjacentState.getBlock().asItem() == Items.AIR
                ? ItemStack.EMPTY
                : adjacentState.getBlock().asItem().getDefaultInstance();
        return new AdjacentSide(side, adjacentState, icon, adjacentState.getBlock().getName());
    }

    public void setAutoIOEnabled(boolean enabled) {
        if (autoIOConfig.enabled() == enabled) return;
        autoIOConfig.setEnabled(enabled);
        markAutoIOConfigChanged();
    }

    public boolean isAutoIOSideExposed(Direction side) {
        return side == null || autoIOConfig.isSideEnabled(side);
    }

    public void toggleAutoIOEnabled() {
        setAutoIOEnabled(!autoIOConfig.enabled());
    }

    public void setAutoIOSide(Direction side, boolean enabled) {
        if (side == null || autoIOConfig.isSideEnabled(side) == enabled) return;
        autoIOConfig.setSide(side, enabled);
        markAutoIOConfigChanged();
    }

    public void setAllAutoIOSides(boolean enabled) {
        if (autoIOConfig.enabledSides().size() == (enabled ? Direction.values().length : 0)) return;
        autoIOConfig.setAllSides(enabled);
        markAutoIOConfigChanged();
    }

    public void toggleAutoIOSide(Direction side) {
        if (side == null) return;
        setAutoIOSide(side, !autoIOConfig.isSideEnabled(side));
    }

    private void markAutoIOConfigChanged() {
        markAutoIOCacheDirty();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        runAutoIOCycle();
    }

    protected void runAutoIOCycle() {
        if (autoIOConfig == null || level == null || level.isClientSide() || !autoIOConfig.enabled() || autoIOConfig.enabledSides().isEmpty()) return;
        AutoIOTransferHandler handler = autoIOTransferHandler();
        if (handler == null) return;
        boolean rebuiltCandidates = consumeAutoIOCacheDirty();
        if (rebuiltCandidates) rebuildAutoIOCandidates(handler);
        if (autoIOCandidateSides.isEmpty()) {
            if (rebuiltCandidates) {
                autoIOTicksUntilTransfer = AUTO_IO_MIN_DELAY - 1;
                return;
            }
            if (autoIOTicksUntilTransfer > 0) {
                autoIOTicksUntilTransfer--;
                return;
            }
            rebuildAutoIOCandidates(handler);
            if (autoIOCandidateSides.isEmpty()) return;
        }
        if (!hasAutoIOTransferWork()) return;
        if (autoIOTicksUntilTransfer > 0) {
            autoIOTicksUntilTransfer--;
            return;
        }

        boolean moved = false;
        for (Direction side : autoIOCandidateSides) {
            moved |= handler.transfer(this, side);
        }
        if (moved) incrementAutoIOSuccess();
        else decrementAutoIOSuccess();
        autoIOTicksUntilTransfer = autoIODelay - 1;
    }

    private void rebuildAutoIOCandidates(AutoIOTransferHandler handler) {
        autoIOCandidateSides.clear();
        for (Direction side : autoIOConfig.enabledSides()) {
            if (handler.hasAdjacentTarget(this, side)) autoIOCandidateSides.add(side);
        }
    }

    protected AutoIOTransferHandler autoIOTransferHandler() {
        return AutoIOTransferHandlers.handlerFor(this).orElse(null);
    }

    public boolean ejectContents() {
        if (level == null || level.isClientSide() || ioType() != IOType.INPUT || isUsedByActiveRecipe()) return false;
        AutoIOTransferHandler handler = autoIOTransferHandler();
        if (handler == null || !handler.hasTransferableContents(this)) return false;
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        for (int index = sides.size() - 1; index > 0; index--) {
            int swapIndex = level.getRandom().nextInt(index + 1);
            Direction side = sides.get(index);
            sides.set(index, sides.get(swapIndex));
            sides.set(swapIndex, side);
        }
        boolean moved = false;
        for (Direction side : sides) {
            moved |= handler.eject(this, side);
            if (!handler.hasTransferableContents(this)) break;
        }
        return moved;
    }

    protected boolean isUsedByActiveRecipe() {
        if (level == null) return false;
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller
                    && controller.isPortUsedByActiveRecipe(worldPosition)) return true;
        }
        return false;
    }

    private void incrementAutoIOSuccess() {
        int max = (AUTO_IO_MAX_DELAY - AUTO_IO_MIN_DELAY) / 5;
        if (autoIOSuccessCounter < max) autoIOSuccessCounter++;
        autoIODelay = Math.max(AUTO_IO_MIN_DELAY, AUTO_IO_MAX_DELAY - autoIOSuccessCounter * 5);
    }

    private void decrementAutoIOSuccess() {
        if (autoIOSuccessCounter > 0) autoIOSuccessCounter--;
        autoIODelay = Math.max(AUTO_IO_MIN_DELAY, AUTO_IO_MAX_DELAY - autoIOSuccessCounter * 5);
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
