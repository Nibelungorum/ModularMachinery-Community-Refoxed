package cn.howxu.mmcr.api.recipe;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum ParallelTier {
    X4(4, "parallel_controller_4"),
    X16(16, "parallel_controller_16"),
    X64(64, "parallel_controller_64"),
    X256(256, "parallel_controller_256"),
    X512(512, "parallel_controller_512"),
    MAX(Integer.MAX_VALUE, "parallel_controller_max");

    private final int maxParallelism;
    private final String idSuffix;

    ParallelTier(int maxParallelism, String idSuffix) {
        this.maxParallelism = maxParallelism;
        this.idSuffix = idSuffix;
    }

    public String idSuffix() {
        return idSuffix;
    }

    public String translationKey() {
        return "block.mmcr." + idSuffix();
    }

    public int maxParallelism() {
        return maxParallelism;
    }
}
