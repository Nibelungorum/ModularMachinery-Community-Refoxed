package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.library.ingredients.IngredientInfo;
import mezz.jei.library.ingredients.IngredientManager;
import mezz.jei.library.ingredients.RegisteredIngredients;
import mezz.jei.library.ingredients.SlotDisplayInterpreterRegistry;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Real JEI ingredient creation and validation with an isolated item helper.
 *
 * @author howxu <dev@howxu.cn>
 */
final class JeiTestIngredients {
    private JeiTestIngredients() {
    }

    static IngredientManager manager() {
        @SuppressWarnings("unchecked")
        IIngredientHelper<ItemStack> helper = (IIngredientHelper<ItemStack>) Proxy.newProxyInstance(
                IIngredientHelper.class.getClassLoader(), new Class<?>[]{IIngredientHelper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIngredientType" -> VanillaTypes.ITEM_STACK;
                    case "isValidIngredient" -> !((ItemStack) args[0]).isEmpty();
                    case "normalizeIngredient" -> ((ItemStack) args[0]).copyWithCount(1);
                    case "copyIngredient" -> ((ItemStack) args[0]).copy();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var types = new LinkedHashMap<IIngredientType<?>, IngredientInfo<?>>();
        // These tests do not render ingredients or resolve slot displays.
        types.put(VanillaTypes.ITEM_STACK, new IngredientInfo<>(VanillaTypes.ITEM_STACK,
                List.of(), helper, null, ItemStack.CODEC, null));
        return new IngredientManager(new RegisteredIngredients(types),
                new SlotDisplayInterpreterRegistry(List.of(), List.of()));
    }
}
