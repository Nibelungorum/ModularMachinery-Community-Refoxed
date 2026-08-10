package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Startup-script builder for machine levels.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineLevelBuilderJS extends BuilderBase<MachineLevel> {
    public transient Identifier typeId;
    public transient int priority;
    public transient BlockState state;
    public transient LevelModifier modifier = LevelModifier.IDENTITY;

    public MachineLevelBuilderJS(Identifier id) {
        super(id);
    }

    public MachineLevelBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineLevelBuilderJS type(String typeId) {
        this.typeId = Identifier.parse(typeId);
        return this;
    }

    public MachineLevelBuilderJS priority(int priority) {
        this.priority = priority;
        return this;
    }

    public MachineLevelBuilderJS state(Object state) {
        this.state = switch (state) {
            case String blockId -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)).defaultBlockState();
            case BlockState blockState -> blockState;
            default -> throw new IllegalArgumentException("Machine level state must be a block id or BlockState: " + state);
        };
        return this;
    }

    public MachineLevelBuilderJS modifier(Map<String, Object> modifier) {
        LevelModifier defaults = LevelModifier.IDENTITY;
        this.modifier = new LevelModifier(
                doubleValue(modifier, "durationMultiplier", defaults.durationMultiplier()),
                doubleValue(modifier, "energyMultiplier", defaults.energyMultiplier()),
                doubleValue(modifier, "outputMultiplier", defaults.outputMultiplier()),
                intValue(modifier, "parallelismBonus", defaults.parallelismBonus()),
                intValue(modifier, "factoryThreadBonus", defaults.factoryThreadBonus()));
        return this;
    }

    @Override
    public MachineLevel createObject() {
        if (typeId == null) throw new IllegalStateException("type() not called");
        if (state == null) throw new IllegalStateException("state() not called");
        return new MachineLevel(id, typeId, priority, new BlockPredicate.OfBlockState(state),
                new ItemStack(state.getBlock()), modifier);
    }

    public void registerObject() {
        MachineLevelRegistry.registerLevel(createObject());
    }

    public MachineLevelBuilderJS register() {
        registerObject();
        return this;
    }

    private static double doubleValue(Map<String, Object> modifier, String key, double defaultValue) {
        Object value = modifier.get(key);
        return value == null ? defaultValue : ((Number) value).doubleValue();
    }

    private static int intValue(Map<String, Object> modifier, String key, int defaultValue) {
        Object value = modifier.get(key);
        return value == null ? defaultValue : ((Number) value).intValue();
    }
}
