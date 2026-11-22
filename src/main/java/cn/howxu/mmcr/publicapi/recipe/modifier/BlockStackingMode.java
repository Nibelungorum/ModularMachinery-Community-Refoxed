package cn.howxu.mmcr.publicapi.recipe.modifier;

/** How repeated matching structure blocks combine; never changes Upgrade Bus item arithmetic.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public enum BlockStackingMode {
    /** Sum each block's change from a neutral factor of one: two ×0.9 blocks give ×0.8. */
    ADD,
    /** Compound per-block factors: two ×0.9 blocks give ×0.81. Fixed amounts still add in their own units. */
    MULTIPLY
}
