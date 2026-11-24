package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

/** Decides only whether to stop waiting for more inputs; never grants a start permit.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class PatternAggregationPolicy {
    private PatternAggregationPolicy() { }

    public static boolean shouldStart(long payable, long laneLimit, long quietTicks, long eligibleTicks) {
        return payable > 0 && laneLimit > 0 && (payable >= laneLimit || quietTicks >= 5 || eligibleTicks >= 20);
    }
}
