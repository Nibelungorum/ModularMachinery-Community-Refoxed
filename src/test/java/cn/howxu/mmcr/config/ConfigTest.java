package cn.howxu.mmcr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {

    @Test
    void terminalDemolishLimitDefaultsTo2048() {
        assertEquals(2048, Config.TERMINAL_MAX_DEMOLISH_BLOCKS.getDefault());
    }
}
