package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGenTest {

    @Test
    void registerModelsDoesNotRegisterControllerOrPortModels() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
        List<ModelGen.GeneratedModel> generatedModels = ModelGen.collectRegisteredModels();
        List<String> controllerIds = MachineDefinitions.allRegistrations().stream()
                .map(registration -> MachineControllerSpec.defaultsFor(registration.id()).id().getPath())
                .toList();
        List<String> portIds = PortKinds.all().stream()
                .map(kind -> kind.id())
                .toList();

        assertThat(generatedModels).filteredOn(model -> model.kind() == ModelGen.GeneratedModel.Kind.BLOCKSTATE)
                .extracting(ModelGen.GeneratedModel::name)
                .doesNotContainAnyElementsOf(controllerIds)
                .doesNotContainAnyElementsOf(portIds);
        assertThat(generatedModels).filteredOn(model -> model.kind() == ModelGen.GeneratedModel.Kind.ITEM)
                .extracting(ModelGen.GeneratedModel::name)
                .doesNotContainAnyElementsOf(controllerIds)
                .doesNotContainAnyElementsOf(portIds);
        assertThat(ModelGen.collectKnownBlockNames())
                .doesNotContainAnyElementsOf(controllerIds)
                .doesNotContainAnyElementsOf(portIds);
        assertThat(ModelGen.collectKnownItemNames())
                .doesNotContainAnyElementsOf(controllerIds)
                .doesNotContainAnyElementsOf(portIds);
    }
}
