package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeMachineModelRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
    }

    @Test
    void controller_definition_uses_dynamic_loader_for_every_variant() {
        var block = (MachineControllerBlock) ModBlocks.controllerFor(MMCR.id("blast_furnace")).get();

        var definition = RuntimeMachineModelRegistry.controllerDefinition(block);

        assertThat(definition.variants()).hasSize(96)
                .allMatch(variant -> variant.modelId().equals(DynamicOverlayModelLoader.CONTROLLER_ID));
    }

    @Test
    void port_definition_uses_dynamic_port_loader() {
        var block = (IOPortBlock) ModBlocks.BLOCKS.get("item_input_bus").get();

        var definition = RuntimeMachineModelRegistry.portDefinition(block);

        assertThat(definition.variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));
    }

    @Test
    void dynamic_json_points_to_custom_loader_type() {
        var block = (IOPortBlock) ModBlocks.BLOCKS.get("item_input_bus").get();

        String json = RuntimeMachineModelRegistry.blockStateJson(RuntimeMachineModelRegistry.portDefinition(block));

        assertThat(json).contains("\"type\": \"mmcr:dynamic_port_overlay\"");
    }

}
