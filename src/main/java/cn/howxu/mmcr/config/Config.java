package cn.howxu.mmcr.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final int DEFAULT_MACHINE_CHECK_INTERVAL_TICKS = 40;
    public static final int DEFAULT_TERMINAL_MAX_DEMOLISH_BLOCKS = 131072;
    public static final int DEFAULT_BUILD_BLOCKS_PER_TICK = 256;
    public static final int DEFAULT_BUILD_TASK_TIMEOUT_TICKS = 20 * 60;
    public static final int DEFAULT_STRUCTURE_SCAN_BATCHES = 10;
    public static final int DEFAULT_STRUCTURE_SENTINEL_COUNT = 16;
    public static final double DEFAULT_PREVIEW_RENDER_RADIUS = 64.0;
    public static final ModConfigSpec.IntValue MACHINE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue TERMINAL_MAX_DEMOLISH_BLOCKS;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue BUILD_TASK_TIMEOUT_TICKS;
    public static final ModConfigSpec.DoubleValue PREVIEW_RENDER_RADIUS;
    public static final ModConfigSpec.IntValue STRUCTURE_SCAN_BATCHES;
    public static final ModConfigSpec.IntValue STRUCTURE_SENTINEL_COUNT;
    public static final ModConfigSpec.BooleanValue STRUCTURE_SENTINEL_ENABLED;
    public static final ModConfigSpec.DoubleValue ENERGY_CONSUMPTION_MULTIPLIER;

    static {
        var b = new ModConfigSpec.Builder();
        MACHINE_CHECK_INTERVAL_TICKS = b
                .comment("Ticks between controller structure-check passes")
                .defineInRange("machine_check_interval_ticks", DEFAULT_MACHINE_CHECK_INTERVAL_TICKS, 1, 600);
        TERMINAL_MAX_DEMOLISH_BLOCKS = b
                .comment("Maximum blocks removed by one terminal demolish operation")
                .defineInRange("terminal_max_demolish_blocks", DEFAULT_TERMINAL_MAX_DEMOLISH_BLOCKS, 1, 1_000_000);
        BUILD_BLOCKS_PER_TICK = b
                .comment("Maximum structure blocks placed by one controller per tick")
                .defineInRange("build_blocks_per_tick", DEFAULT_BUILD_BLOCKS_PER_TICK, 1, 1_000_000);
        BUILD_TASK_TIMEOUT_TICKS = b
                .comment("Maximum age of a pending structure build task")
                .defineInRange("build_task_timeout_ticks", DEFAULT_BUILD_TASK_TIMEOUT_TICKS, 1, 1_000_000);
        PREVIEW_RENDER_RADIUS = b
                .comment("Maximum distance at which multiblock preview blocks are rendered")
                .defineInRange("preview_render_radius", DEFAULT_PREVIEW_RENDER_RADIUS, 1.0, 512.0);
        STRUCTURE_SCAN_BATCHES = b
                .comment("Number of batches used to scan a structure across server ticks")
                .defineInRange("structure_scan_batches", DEFAULT_STRUCTURE_SCAN_BATCHES, 1, 32);
        STRUCTURE_SENTINEL_COUNT = b
                .comment("Number of deterministic structure entries checked before each scan batch")
                .defineInRange("structure_sentinel_count", DEFAULT_STRUCTURE_SENTINEL_COUNT, 0, 128);
        STRUCTURE_SENTINEL_ENABLED = b
                .comment("Whether deterministic structure sentinel checks are enabled")
                .define("structure_sentinel_enabled", true);
        ENERGY_CONSUMPTION_MULTIPLIER = b
                .comment("Global multiplier on energy consumption")
                .defineInRange("energy_consumption_multiplier", 1.0, 0.0, 100.0);
        SPEC = b.build();
    }

    private Config() {
    }
}
