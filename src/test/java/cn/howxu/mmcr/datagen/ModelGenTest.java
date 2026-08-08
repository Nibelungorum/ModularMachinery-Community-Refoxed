package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGenTest {

    @Test
    void registerModelsDoesNotRegisterControllerOrPortModels() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
        assertThat(ModelGen.collectRegisteredModels())
                .extracting(ModelGen.GeneratedModel::name)
                .doesNotContain("blast_furnace")
                .doesNotContain("energy_input_port");
    }
}
