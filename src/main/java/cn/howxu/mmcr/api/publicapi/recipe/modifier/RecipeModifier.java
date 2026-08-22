package cn.howxu.mmcr.api.publicapi.recipe.modifier;

/** Public operation names used by declarative recipe modifiers.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeModifier {
    private RecipeModifier() {}

    public enum IOType { INPUT, OUTPUT }

    public enum Operation { ADD, MULTIPLY, SUBTRACT, DIVIDE }
}
