package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Startup-script builder for machine levels.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineLevelBuilderJS extends BuilderBase<MachineLevel> {
    public transient ResourceLocation typeId;
    public transient int priority;
    public transient BlockState state;
    public transient ModifierDefinition modifier = ModifierDefinition.EMPTY;

    public MachineLevelBuilderJS(ResourceLocation id) {
        super(id);
    }

    public MachineLevelBuilderJS(String id) {
        this(ResourceLocation.parse(id));
    }

    public MachineLevelBuilderJS type(String typeId) {
        this.typeId = ResourceLocation.parse(typeId);
        return this;
    }

    public MachineLevelBuilderJS priority(int priority) {
        this.priority = priority;
        return this;
    }

    public MachineLevelBuilderJS state(Object state) {
        this.state = switch (state) {
            case String blockId -> BuiltInRegistries.BLOCK.getValue(ResourceLocation.parse(blockId)).defaultBlockState();
            case BlockState blockState -> blockState;
            default -> throw new IllegalArgumentException("Machine level state must be a block id or BlockState: " + state);
        };
        return this;
    }

    public MachineLevelBuilderJS modifier(ModifierDefinition modifier) {
        this.modifier = Objects.requireNonNull(modifier, "modifier");
        return this;
    }

    @Override
    public MachineLevel createObject() {
        if (typeId == null) throw new IllegalStateException("type() not called");
        if (state == null) throw new IllegalStateException("state() not called");
        return new MachineLevel(id, typeId, priority, new BlockPredicate.OfBlockState(state), ItemStack.EMPTY, modifier);
    }

    public void registerObject() {
        MMCRMachineStructuresEvent.current().registerLevel(createObject());
    }

    public MachineLevelBuilderJS register() {
        registerObject();
        return this;
    }
}
