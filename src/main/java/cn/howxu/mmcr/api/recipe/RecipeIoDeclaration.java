package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

/** Typed source accepted by recipe and direct execution IO entry points.
 * @author howxu <dev@howxu.cn>
 */
public interface RecipeIoDeclaration {
    IOType io();
}
