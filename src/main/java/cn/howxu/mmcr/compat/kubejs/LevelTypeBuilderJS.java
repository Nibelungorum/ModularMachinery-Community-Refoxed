package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Startup-script builder for machine level types.
 *
 * @author howxu <dev@howxu.cn>
 */
public class LevelTypeBuilderJS extends BuilderBase<LevelType> {
    public transient String displayName;

    public LevelTypeBuilderJS(Identifier id) {
        super(id);
    }

    public LevelTypeBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public LevelTypeBuilderJS displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public LevelType createObject() {
        return new LevelType(id, Component.literal(displayName == null ? id.toString() : displayName));
    }

    public void registerObject() {
        MachineLevelRegistry.registerType(createObject());
    }

    public LevelTypeBuilderJS register() {
        registerObject();
        return this;
    }
}
