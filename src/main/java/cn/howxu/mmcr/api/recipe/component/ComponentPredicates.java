package cn.howxu.mmcr.api.recipe.component;

import com.mojang.serialization.Dynamic;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentPredicates {

    private ComponentPredicates() {
    }

    public static boolean matches(ItemStack stack, DataComponentPredicateSet predicates) {
        return predicates.matches(stack);
    }

    static <T> boolean matches(DataComponentType<T> type, T value, ComponentPredicate predicate) {
        return type.codec().encodeStart(NbtOps.INSTANCE, value)
                .map(encoded -> predicate.matches(new Dynamic<>(NbtOps.INSTANCE, encoded)))
                .result().orElse(false);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate) {
        if (!(predicate instanceof ComponentPredicate.Exact exact)) return null;
        return type.codec().parse(NbtOps.INSTANCE, exact.value().convert(NbtOps.INSTANCE).getValue()).result().orElse(null);
    }
}
