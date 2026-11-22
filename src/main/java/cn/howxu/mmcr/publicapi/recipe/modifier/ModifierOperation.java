package cn.howxu.mmcr.publicapi.recipe.modifier;
/** Arithmetic operation. @author howxu <dev@howxu.cn> */
public enum ModifierOperation {
    ADD, MULTIPLY, SUBTRACT, DIVIDE,
    /** Adds a fraction of the original base, before direct multipliers and target-specific rounding. */
    ADD_RATIO
}
