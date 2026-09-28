package cn.howxu.mmcr.compat.athena;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AthenaModelBridgeBootstrapTest {
    @Test
    void selects_noop_bridge_when_athena_is_absent() {
        assertThat(AthenaModelBridgeBootstrap.selectForTesting(false))
                .isSameAs(UnavailableAthenaModelBridge.INSTANCE);
    }
}
