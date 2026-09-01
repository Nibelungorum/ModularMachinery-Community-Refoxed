package cn.howxu.mmcr.api.publicapi.recipe;

/** Public direction for recipe requirements and modifiers.
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeIo {
    INPUT,
    OUTPUT;

    /**
     * Returns whether this direction consumes a machine resource.
     *
     * @return {@code true} for input directions
     */
    public boolean isInput() {
        return this == INPUT;
    }
}
