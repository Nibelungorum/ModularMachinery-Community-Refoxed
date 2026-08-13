package cn.howxu.mmcr.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final int DEFAULT_MACHINE_CHECK_INTERVAL_TICKS = 20;
    public static final ModConfigSpec.IntValue MACHINE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue TERMINAL_MAX_DEMOLISH_BLOCKS;
    public static final ModConfigSpec.DoubleValue ENERGY_CONSUMPTION_MULTIPLIER;

    static {
        var b = new ModConfigSpec.Builder();
        MACHINE_CHECK_INTERVAL_TICKS = b
                .comment("Ticks between controller structure-check passes")
                .defineInRange("machine_check_interval_ticks", DEFAULT_MACHINE_CHECK_INTERVAL_TICKS, 1, 600);
        TERMINAL_MAX_DEMOLISH_BLOCKS = b
                .comment("Maximum structure blocks removed by one terminal demolish action")
                .defineInRange("terminal_max_demolish_blocks", 256, 1, 4096);
        ENERGY_CONSUMPTION_MULTIPLIER = b
                .comment("Global multiplier on energy consumption")
                .defineInRange("energy_consumption_multiplier", 1.0, 0.0, 100.0);
        SPEC = b.build();
    }

    private Config() {
    }
}
