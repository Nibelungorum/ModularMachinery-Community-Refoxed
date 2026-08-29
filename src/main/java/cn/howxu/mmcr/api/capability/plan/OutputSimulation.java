package cn.howxu.mmcr.api.capability.plan;

/**
 * Result of simulating an output request without applying I/O.
 *
 * @param requested requested output amount
 * @param accepted output amount that can fit
 * @param fit fit classification for the request
 * @author howxu <dev@howxu.cn>
 */
public record OutputSimulation(long requested, long accepted, OutputFit fit) {
    public OutputSimulation {
        if (requested < 0L || accepted < 0L || accepted > requested || fit == null) {
            throw new IllegalArgumentException("invalid output simulation");
        }
    }
}
