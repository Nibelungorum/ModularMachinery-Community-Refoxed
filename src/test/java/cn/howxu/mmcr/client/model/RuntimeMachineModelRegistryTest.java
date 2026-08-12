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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void dynamic_blocks_have_explicit_definitions() {
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.CONTROLLER.get()).modelKind())
                .isEqualTo(DynamicOverlayBakedModel.Kind.CONTROLLER);
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.BLOCKS.get("item_input_bus").get()).modelKind())
                .isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get()).modelKind())
                .isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get()).itemDescription().overlayTexture())
                .isEqualTo(MMCR.id("block/overlay_parallel_controller_plus"));
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.BLOCKS.get("factory_controller").get()).blockName())
                .isEqualTo("factory_controller");
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.BLOCKS.get("factory_controller").get()).itemDescription().overlayTexture())
                .isEqualTo(MMCR.id("block/overlay_factory_controller"));
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.SMART_INTERFACE.get()).modelKind())
                .isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.SMART_INTERFACE.get()).itemDescription().overlayTexture())
                .isEqualTo(MMCR.id("block/overlay_smartinterface_number"));
    }

    @Test
    void definitions_are_stable_after_deferred_holders_bind() {
        var block = ModBlocks.CONTROLLER.get();

        var first = RuntimeMachineModelRegistry.definition(block);
        var second = RuntimeMachineModelRegistry.definition(block);
        var firstDefinitions = RuntimeMachineModelRegistry.definitions().toList();
        var secondDefinitions = RuntimeMachineModelRegistry.definitions().toList();

        assertThat(first).isSameAs(second);
        assertThat(firstDefinitions).containsExactlyElementsOf(secondDefinitions);
        assertThat(firstDefinitions.getFirst()).isSameAs(secondDefinitions.getFirst());
    }

    @Test
    void cached_block_state_variants_cannot_be_mutated() {
        var definition = RuntimeMachineModelRegistry.definition(ModBlocks.CONTROLLER.get());
        String resourceJson = RuntimeMachineModelRegistry.blockStateJson(definition.blockStateDefinition());

        assertThatThrownBy(() -> definition.blockStateDefinition().variants().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(RuntimeMachineModelRegistry.blockStateJson(definition.blockStateDefinition()))
                .isEqualTo(resourceJson);
    }

    @Test
    void dynamic_block_state_requires_an_explicit_definition() {
        var block = ModBlocks.CONTROLLER.get();

        assertThat(RuntimeMachineModelRegistry.dynamicBlockState(block))
                .isSameAs(RuntimeMachineModelRegistry.definition(block).blockStateDefinition());
        assertThatThrownBy(() -> RuntimeMachineModelRegistry.dynamicBlockState(ModBlocks.BASIC_CASING.get()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported dynamic machine block");
    }

    @Test
    void item_description_does_not_share_mutable_overlay_faces() {
        var description = RuntimeMachineModelRegistry.definition(ModBlocks.CONTROLLER.get()).itemDescription();

        description.overlayFaces().clear();

        assertThat(RuntimeMachineModelRegistry.definition(ModBlocks.CONTROLLER.get()).itemDescription().overlayFaces())
                .containsExactly(Direction.NORTH);
    }

    @Test
    void definition_does_not_share_its_input_item_description() {
        var overlayFaces = java.util.EnumSet.of(Direction.NORTH);
        var description = new DynamicOverlayItemModel.Description(
                DynamicOverlayBakedModel.Kind.CONTROLLER, MMCR.id("test"), null,
                MMCR.id("block/dynamic_machine_controller"), MMCR.id("block/test_base"),
                MMCR.id("block/test_overlay"), overlayFaces);
        var definition = new RuntimeBlockModelDefinition(
                ModBlocks.CONTROLLER.get(), "test", DynamicOverlayBakedModel.Kind.CONTROLLER,
                RuntimeMachineModelRegistry.controllerDefinition((MachineControllerBlock) ModBlocks.CONTROLLER.get()), description);

        overlayFaces.clear();

        assertThat(definition.itemDescription().overlayFaces()).containsExactly(Direction.NORTH);
    }

    @Test
    void parallel_and_factory_controllers_use_dynamic_port_loader() {
        var parallel = ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get();
        var factory = ModBlocks.BLOCKS.get("factory_controller").get();
        var smartInterface = ModBlocks.SMART_INTERFACE.get();

        assertThat(RuntimeMachineModelRegistry.dynamicBlockState(parallel).variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));
        assertThat(RuntimeMachineModelRegistry.dynamicBlockState(factory).variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));
        assertThat(RuntimeMachineModelRegistry.dynamicBlockState(smartInterface).variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));
    }

    @Test
    void runtime_resources_include_dynamic_factory_controller() {
        assertThat(RuntimeMachineModelRegistry.dynamicBlockEntries()).containsKey("factory_controller");
        assertThat(RuntimeMachineModelRegistry.dynamicBlockEntries()).doesNotContainKey("factory_scheduler");
        assertThat(RuntimeMachineResourcePack.resources())
                .containsKey(MMCR.id("blockstates/factory_controller.json"))
                .containsKey(MMCR.id("items/factory_controller.json"))
                .doesNotContainKey(MMCR.id("blockstates/factory_scheduler.json"))
                .doesNotContainKey(MMCR.id("items/factory_scheduler.json"));
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
