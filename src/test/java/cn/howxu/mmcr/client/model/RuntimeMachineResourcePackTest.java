package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies runtime resources are exposed for standalone dynamic machine blocks.
 * @author howxu <dev@howxu.cn>
 */
class RuntimeMachineResourcePackTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void standalone_upgrade_buses_expose_runtime_blockstate_and_item_models() {
        var resources = RuntimeMachineResourcePack.resources();
        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            String id = "upgrade_bus_" + size.id();
            assertThat(resources).containsKeys(
                    MMCR.id("blockstates/" + id + ".json"),
                    MMCR.id("items/" + id + ".json"));
        }
    }
}
