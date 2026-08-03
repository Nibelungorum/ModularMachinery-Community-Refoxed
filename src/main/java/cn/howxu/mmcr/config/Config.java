package cn.howxu.mmcr.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MACHINE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue ENERGY_CONSUMPTION_MULTIPLIER;

    static {
        var b = new ModConfigSpec.Builder();
        MACHINE_CHECK_INTERVAL_TICKS = b
                .comment("Ticks between controller structure-check passes")
                .defineInRange("machine_check_interval_ticks", 20, 1, 600);
        ENERGY_CONSUMPTION_MULTIPLIER = b
                .comment("Global multiplier on energy consumption")
                .defineInRange("energy_consumption_multiplier", 1.0, 0.0, 100.0);
        SPEC = b.build();
    }

    private Config() {
    }
}