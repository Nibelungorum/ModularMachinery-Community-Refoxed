package cn.howxu.mmcr.api.recipe.requirement;

/** Scheduling facts for a single, already modified payload; not a resource-type enumeration.
 * @author howxu <dev@howxu.cn>
 */
public enum RequirementExecutionPolicy {
    LEGACY, START_MATERIAL, PREFLIGHT_INPUT, TICK_IO, AFTER_PAYMENT_OUTPUT;

    public boolean recordsStartPayment() { return this == START_MATERIAL; }
    public boolean tickOutput() { return this == TICK_IO || this == AFTER_PAYMENT_OUTPUT; }
}
