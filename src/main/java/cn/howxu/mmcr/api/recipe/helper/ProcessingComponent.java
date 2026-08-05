package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.ComponentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class ProcessingComponent {

    private final MachineComponent component;
    private final BlockEntity container;
    private final BlockPos relativePos;
    @Nullable
    private final ComponentType type;
    @Nullable
    private final String tag;
    private final BlockPos pos;

    public ProcessingComponent(MachineComponent component, BlockEntity container, BlockPos pos, BlockPos relativePos, @Nullable String tag) {
        this.component = component;
        this.container = container;
        this.pos = pos;
        this.relativePos = relativePos;
        this.tag = tag;
        this.type = null;
    }

    public ProcessingComponent(@Nullable ComponentType type, @Nullable String tag, BlockPos pos) {
        this.component = null;
        this.container = null;
        this.relativePos = BlockPos.ZERO;
        this.type = type;
        this.tag = tag;
        this.pos = pos;
    }

    public MachineComponent getComponent() {
        return component;
    }

    public BlockEntity getContainer() {
        return container;
    }

    public BlockPos getRelativePos() {
        return relativePos;
    }

    @Nullable
    public ComponentType getType() {
        return type;
    }

    @Nullable
    public String getTag() {
        return tag;
    }

    public BlockPos getPos() {
        return pos;
    }
}
