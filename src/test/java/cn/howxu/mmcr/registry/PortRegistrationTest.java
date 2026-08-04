package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortRegistrationTest {

    private static final List<String> IDS = List.of(
            "item_input_bus", "item_output_bus",
            "fluid_input_hatch", "fluid_output_hatch",
            "energy_input_hatch", "energy_output_hatch");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void all_basic_ports_are_registered_without_io_state() {
        for (String id : IDS) {
            Block block = ModBlocks.BLOCKS.get(id).get();
            assertThat(block).isNotNull();
            assertThat(ModItems.ITEMS).containsKey(id);
            assertThat(ModBlockEntities.BES).containsKey(id);
            assertThat(block.defaultBlockState().getProperties())
                    .noneMatch(property -> property.getName().equals("io_type"));
        }
        assertThat(ModBlocks.BLOCKS).doesNotContainKeys(
                "io_port_item_basic", "io_port_fluid_basic", "io_port_energy_basic");
    }
}