package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.ComponentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ProcessingComponent {

    private final MachineComponent component;
    private final BlockEntity container;
    private final BlockPos relativePos;
    @Nullable
    private final ComponentType type;
    private final List<String> tags;
    private final BlockPos pos;

    public ProcessingComponent(MachineComponent component, BlockEntity container, BlockPos pos, BlockPos relativePos, @Nullable String tag) {
        this(component, container, pos, relativePos, tag == null ? List.of() : List.of(tag));
    }

    public ProcessingComponent(MachineComponent component, BlockEntity container, BlockPos pos, BlockPos relativePos, List<String> tags) {
        this.component = component;
        this.container = container;
        this.pos = pos;
        this.relativePos = relativePos;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.type = null;
    }

    public ProcessingComponent(@Nullable ComponentType type, @Nullable String tag, BlockPos pos) {
        this(type, tag == null ? List.of() : List.of(tag), pos);
    }

    public ProcessingComponent(@Nullable ComponentType type, List<String> tags, BlockPos pos) {
        this.component = null;
        this.container = null;
        this.relativePos = BlockPos.ZERO;
        this.type = type;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
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
        return tag();
    }

    @Nullable
    public String tag() {
        return tags.isEmpty() ? null : tags.getFirst();
    }

    public List<String> tags() {
        return tags;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean matchesTag(@Nullable String requirementTag) {
        if (requirementTag == null || requirementTag.isBlank()) return true;
        if (tags.isEmpty()) return true;
        return tags.contains(requirementTag);
    }

    public BlockPos getPos() {
        return pos;
    }
}
