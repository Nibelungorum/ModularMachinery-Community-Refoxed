package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.DisplayStack;
import cn.howxu.mmcr.api.machine.definition.MachineLevel;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Startup-script builder for machine levels.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineLevelBuilderJS extends BuilderBase<MachineLevel> {
    public transient ResourceLocation typeId;
    public transient int priority;
    public transient String state;
    public transient ModifierDefinition modifier = ModifierDefinition.EMPTY;

    public MachineLevelBuilderJS(ResourceLocation id) {
        super(id);
    }

    public MachineLevelBuilderJS(String id) {
        this(ResourceLocation.parse(id));
    }

    public MachineLevelBuilderJS type(String typeId) {
        return type(ResourceLocation.parse(typeId));
    }

    public MachineLevelBuilderJS type(ResourceLocation typeId) {
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        return this;
    }

    public MachineLevelBuilderJS priority(int priority) {
        this.priority = priority;
        return this;
    }

    public MachineLevelBuilderJS state(String blockStateId) {
        this.state = Objects.requireNonNull(blockStateId, "blockStateId");
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
        return new MachineLevel(id, typeId, priority, BlockPredicate.state(state),
                DisplayStack.of(ItemStack.EMPTY), modifier);
    }

    public void registerObject() {
        StructureRegistration.current().registerLevel(createObject());
    }

    public MachineLevelBuilderJS register() {
        registerObject();
        return this;
    }
}
