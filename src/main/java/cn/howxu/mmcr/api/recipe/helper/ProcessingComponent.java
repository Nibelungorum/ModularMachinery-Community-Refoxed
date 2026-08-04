package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.api.recipe.ComponentType;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class ProcessingComponent {

    @Nullable
    private final ComponentType type;
    @Nullable
    private final String tag;
    private final BlockPos pos;

    public ProcessingComponent(@Nullable ComponentType type, @Nullable String tag, BlockPos pos) {
        this.type = type;
        this.tag = tag;
        this.pos = pos;
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
