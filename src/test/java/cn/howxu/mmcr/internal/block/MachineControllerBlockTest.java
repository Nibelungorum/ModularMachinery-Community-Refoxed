package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void menu_title_uses_machine_localized_name() {
        assertThat(MachineControllerBlock.titleFor(MMCR.id("blast_furnace")).getString()).isEqualTo("高炉");
    }
}
