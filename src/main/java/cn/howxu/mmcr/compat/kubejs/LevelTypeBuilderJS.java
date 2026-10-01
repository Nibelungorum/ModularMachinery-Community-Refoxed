package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Startup-script builder for machine level types.
 *
 * @author howxu <dev@howxu.cn>
 */
public class LevelTypeBuilderJS extends BuilderBase<LevelType> {
    public transient String displayNameKey;

    public LevelTypeBuilderJS(ResourceLocation id) {
        super(id);
    }

    public LevelTypeBuilderJS(String id) {
        this(ResourceLocation.parse(id));
    }

    public LevelTypeBuilderJS displayName(String displayName) {
        this.displayNameKey = displayName;
        return this;
    }

    public LevelTypeBuilderJS displayNameKey(String key) {
        return displayName(key);
    }

    @Override
    public LevelType createObject() {
        String key = displayNameKey == null ? id.toString() : displayNameKey;
        Component component = Component.translatable(key);
        return new LevelType(id, component);
    }

    public void registerObject() {
        StructureRegistration.current().registerLevelType(createObject());
    }

    public LevelTypeBuilderJS register() {
        registerObject();
        return this;
    }
}
