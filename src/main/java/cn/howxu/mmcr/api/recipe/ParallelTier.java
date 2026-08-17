package cn.howxu.mmcr.api.recipe;

/**
 * 并行控制器等级。等级名按优雅分级排列,数量按 4 的幂次递增,
 * 最高级 (Ultimate) 使用 {@link Integer#MAX_VALUE} 代表 ~2.1G 倍并行。
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ParallelTier {
    NORMAL(4, "parallel_controller_normal"),
    PLUS(16, "parallel_controller_plus"),
    REINFORCED(64, "parallel_controller_reinforced"),
    PRO(256, "parallel_controller_pro"),
    ELITE(1024, "parallel_controller_elite"),
    FANTASY(4096, "parallel_controller_fantasy"),
    MAX(16384, "parallel_controller_max"),
    ULTIMATE(Integer.MAX_VALUE, "parallel_controller_ultimate");

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
