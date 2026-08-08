package cn.howxu.mmcr.api.recipe;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum ParallelTier {
    X4(4),
    X16(16),
    X64(64),
    X256(256),
    X512(512);

    private final int maxParallelism;

    ParallelTier(int maxParallelism) {
        this.maxParallelism = maxParallelism;
    }

    public String idSuffix() {
        return "parallel_controller_" + maxParallelism;
    }

    public String translationKey() {
        return "block.mmcr." + idSuffix();
    }

    public int maxParallelism() {
        return maxParallelism;
    }
}
