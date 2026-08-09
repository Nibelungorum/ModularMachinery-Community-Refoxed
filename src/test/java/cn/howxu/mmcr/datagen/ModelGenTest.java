package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void factory_controller_uses_dynamic_resources_and_thread_disperser_resources_exist() {
        Path root = Path.of("src/main/resources/assets/mmcr");

        assertThat(ModelGen.collectKnownBlockNames()).doesNotContain("factory_controller");
        assertThat(ModelGen.collectKnownItemNames()).doesNotContain("factory_controller");
        assertThat(ModelGen.collectKnownBlockNames()).doesNotContain("factory_scheduler");
        assertThat(ModelGen.collectKnownItemNames()).doesNotContain("factory_scheduler");
        assertThat(Files.exists(root.resolve("blockstates/factory_controller.json"))).isFalse();
        assertThat(Files.exists(root.resolve("models/block/factory_controller.json"))).isFalse();
        assertThat(Files.exists(root.resolve("models/item/factory_controller.json"))).isFalse();
        assertThat(Files.exists(root.resolve("blockstates/factory_scheduler.json"))).isFalse();
        assertThat(Files.exists(root.resolve("models/block/factory_scheduler.json"))).isFalse();
        assertThat(Files.exists(root.resolve("models/item/factory_scheduler.json"))).isFalse();
        assertThat(Files.exists(root.resolve("models/item/thread_disperser.json"))).isTrue();
        assertThat(Files.exists(root.resolve("textures/block/overlay_factory_controller.png"))).isTrue();
        assertThat(Files.exists(root.resolve("textures/item/thread_disperser.png"))).isTrue();
        assertThat(Files.exists(root.resolve("textures/gui/inventory_tiny.png"))).isTrue();
    }
}
