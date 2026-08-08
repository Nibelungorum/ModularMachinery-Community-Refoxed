package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
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
    void parallel_and_factory_controllers_use_dynamic_port_loader() {
        var parallel = ModBlocks.BLOCKS.get(ParallelTier.X16.idSuffix()).get();
        var factory = ModBlocks.BLOCKS.get("factory_controller").get();

        assertThat(RuntimeMachineModelRegistry.dynamicBlockState(parallel).variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));
        assertThat(RuntimeMachineModelRegistry.dynamicBlockState(factory).variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));
    }

    @Test
    void dynamic_json_points_to_custom_loader_type() {
        var block = (IOPortBlock) ModBlocks.BLOCKS.get("item_input_bus").get();

        String json = RuntimeMachineModelRegistry.blockStateJson(RuntimeMachineModelRegistry.portDefinition(block));

        assertThat(json).contains("\"type\": \"mmcr:dynamic_port_overlay\"");
    }

    @Test
    void vertical_controller_overlay_uv_uses_roll_facing() {
        Vector3f northWest = new Vector3f(0.0f, 1.0f, 0.0f);

        assertThat(DynamicOverlayModelLoader.uv(Direction.UP, Direction.NORTH, northWest)).containsExactly(1.0f, 1.0f);
        assertThat(DynamicOverlayModelLoader.uv(Direction.UP, Direction.EAST, northWest)).containsExactly(1.0f, 0.0f);
        assertThat(DynamicOverlayModelLoader.uv(Direction.UP, Direction.SOUTH, northWest)).containsExactly(0.0f, 0.0f);
        assertThat(DynamicOverlayModelLoader.uv(Direction.UP, Direction.WEST, northWest)).containsExactly(0.0f, 1.0f);
    }

}
