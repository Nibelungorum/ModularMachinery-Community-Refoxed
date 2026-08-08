package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGenTest {

    @Test
    void controllerAndPortsAreAbsentFromGeneratedDynamicBlocks() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
        assertThat(ModelGen.generatedDynamicBlocks())
                .doesNotContain(MachineControllerSpec.defaultsFor(MMCR.id("blast_furnace")).id().getPath())
                .doesNotContainAnyElementsOf(PortKinds.all().stream().map(kind -> kind.id()).toList());
    }
}
