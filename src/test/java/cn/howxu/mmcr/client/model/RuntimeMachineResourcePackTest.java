package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
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
                .noneMatch(id -> id.equals(MMCR.id("blockstates/basic_casing.json")));
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
}
