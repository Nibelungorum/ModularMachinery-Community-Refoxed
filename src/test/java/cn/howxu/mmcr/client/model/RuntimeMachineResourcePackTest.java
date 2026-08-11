package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeMachineResourcePackTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
    }

    @Test
    void resource_pack_contains_dynamic_definitions_only_for_runtime_blocks() {
        assertThat(RuntimeMachineResourcePack.resources().keySet())
                .anyMatch(id -> id.equals(MMCR.id("blockstates/blast_furnace_controller.json")))
                .anyMatch(id -> id.equals(MMCR.id("items/blast_furnace_controller.json")))
                .anyMatch(id -> id.equals(MMCR.id("items/item_input_bus.json")))
                .noneMatch(id -> id.equals(MMCR.id("blockstates/basic_casing.json")));
    }

    @Test
    void generated_item_definitions_share_one_dynamic_item_model() {
        var resources = RuntimeMachineResourcePack.resources();

        assertThat(resources.get(MMCR.id("items/blast_furnace_controller.json")))
                .isEqualTo(RuntimeMachineModelRegistry.itemDefinitionJson());
        assertThat(resources.get(MMCR.id("items/item_input_bus.json")))
                .isEqualTo(RuntimeMachineModelRegistry.itemDefinitionJson())
                .contains("mmcr:dynamic_machine_item");
    }

    @Test
    void resources_match_registered_model_definitions() {
        var resources = RuntimeMachineResourcePack.resources();

        assertResourcesMatchDefinition(resources, "blast_furnace_controller", ModBlocks.CONTROLLER.get());
        assertResourcesMatchDefinition(resources, "item_input_bus", ModBlocks.BLOCKS.get("item_input_bus").get());
        assertResourcesMatchDefinition(resources, ParallelTier.PLUS.idSuffix(),
                ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get());
        assertResourcesMatchDefinition(resources, "factory_controller", ModBlocks.BLOCKS.get("factory_controller").get());
    }

    @Test
    void get_resource_returns_dynamic_blockstate_json() throws Exception {
        try (var pack = new RuntimeMachineResourcePack(new net.minecraft.server.packs.PackLocationInfo(
                "test", net.minecraft.network.chat.Component.literal("test"),
                net.minecraft.server.packs.repository.PackSource.BUILT_IN, java.util.Optional.empty()))) {
            var supplier = pack.getResource(PackType.CLIENT_RESOURCES, MMCR.id("blockstates/blast_furnace_controller.json"));

            assertThat(supplier).isNotNull();
            assertThat(new String(supplier.get().readAllBytes())).contains("mmcr:dynamic_controller_overlay");
        }
    }

    private static void assertResourcesMatchDefinition(java.util.Map<net.minecraft.resources.Identifier, String> resources,
                                                       String blockName, net.minecraft.world.level.block.Block block) {
        RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(block);

        assertThat(resources).containsKeys(
                MMCR.id("blockstates/" + blockName + ".json"),
                MMCR.id("items/" + blockName + ".json"));
        assertThat(resources.get(MMCR.id("blockstates/" + blockName + ".json")))
                .isEqualTo(RuntimeMachineModelRegistry.blockStateJson(definition.blockStateDefinition()));
        assertThat(resources.get(MMCR.id("items/" + blockName + ".json")))
                .isEqualTo(RuntimeMachineModelRegistry.itemDefinitionJson());
    }
}
