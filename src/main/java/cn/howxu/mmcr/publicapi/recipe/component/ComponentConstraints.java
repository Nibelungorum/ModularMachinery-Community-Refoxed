package cn.howxu.mmcr.publicapi.recipe.component;

import cn.howxu.mmcr.internal.api.facade.recipe.ComponentAdapters;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/** Library-produced component constraints. applyTo mutates the supplied stack.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ComponentConstraints {
    ComponentConstraints EMPTY = ComponentAdapters.empty();
    static ComponentConstraints ofTypes(Map<DataComponentType<?>, ComponentCondition> values) { return ComponentAdapters.ofTypes(values); }
    static ComponentConstraints ofIds(Map<ResourceLocation, ComponentCondition> values) { return ComponentAdapters.ofIds(values); }
    Map<DataComponentType<?>, ComponentCondition> values();
    boolean isEmpty();
    boolean hasNonExactValues();
    boolean matches(ItemStack stack);
    boolean matches(ItemStack stack, DynamicOps<?> ops);
    ItemStack displayStack(Item item, int count);
    ItemStack displayStack(Item item, int count, DynamicOps<?> ops);
    void applyTo(ItemStack stack);
    void applyTo(ItemStack stack, DynamicOps<?> ops);
    Optional<DataComponentPatch> exactPatch();
}
