package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class IOPortBlockEntity extends LinkedAppearanceBlockEntity implements MachineComponentTile {
    private static final int CONTROLLER_LINK_CHECK_INTERVAL_TICKS = 40;

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

    @Deprecated
    public void bindControllerAppearance(BlockPos controllerPos, Identifier texture) {
        if (controllerPos != null) {
            linkControllerAppearance(controllerPos, texture);
        }
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

    private void maintainControllerLink() {
        if (level == null || level.isClientSide() || linkedControllerPositions().isEmpty()) return;
        if (Math.floorMod(controllerLinkCheckCounter++ + worldPosition.asLong(), CONTROLLER_LINK_CHECK_INTERVAL_TICKS) != 0) return;
        for (BlockPos controllerPos : linkedControllerPositions()) {
            boolean invalid = !(level.getBlockState(controllerPos).getBlock() instanceof MachineControllerBlock)
                    || !(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)
                    || !controller.isFormed()
                    || !controller.hasLinkedPort(worldPosition);
            if (invalid) {
                unlinkControllerAppearance(controllerPos);
            }
        }
    }

    @Override
    protected Identifier appearanceTexture() {
        var appearances = linkedControllerAppearances();
        return appearances.isEmpty()
                ? DEFAULT_APPEARANCE_BASE_TEXTURE
                : appearances.get(appearances.keySet().stream().min(BlockPos::compareTo).orElseThrow());
    }
}
