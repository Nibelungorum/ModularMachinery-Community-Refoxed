package cn.howxu.mmcr.api.machine;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeFailureActions {
    RESET,
    STILL,
    DECREASE;

    public static RecipeFailureActions getDefaultAction() {
        return STILL;
    }
}
