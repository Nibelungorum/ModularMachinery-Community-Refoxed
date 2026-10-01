package cn.howxu.mmcr.publicapi.recipe.component;

import cn.howxu.mmcr.internal.api.facade.recipe.ComponentAdapters;
import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Component matching factories. @author howxu <dev@howxu.cn> */
public final class ComponentConditions {
    private ComponentConditions() {}
    public static ExactCondition exact(JsonElement value) { return ComponentAdapters.exact(value); }
    public static ExactCondition exact(Dynamic<?> value) { return ComponentAdapters.exact(value); }
    public static MapCondition map(Map<String, ComponentCondition> values) { return ComponentAdapters.map(values); }
    public static ListCondition list(List<ComponentCondition> values) { return ComponentAdapters.list(values); }
    public static RangeCondition range(double min, double max) { return ComponentAdapters.range(min, max); }
    public static TextCondition text(String value, TextMatchMode mode) { return ComponentAdapters.text(value, mode); }
    public static TextCondition text(Component value, TextMatchMode mode) { return ComponentAdapters.text(value, mode); }
}
