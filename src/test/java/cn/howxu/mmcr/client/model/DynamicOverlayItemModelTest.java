package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayItemModelTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
    }

    @Test
    void controller_item_resolves_from_block_item() {
        var item = ModItems.ITEMS.get("blast_furnace_controller").get();

        var description = DynamicOverlayItemModel.describeItem(item);

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.CONTROLLER);
        assertThat(description.machineId()).isEqualTo(MMCR.id("blast_furnace"));
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_machine_controller"));
        assertThat(description.baseTexture()).isNotNull();
        assertThat(description.overlayTexture()).isNotNull();
        assertThat(description.overlayFaces()).containsExactly(Direction.NORTH);
    }

    @Test
    void port_item_resolves_from_block_item() {
        var block = cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get();

        var description = DynamicOverlayItemModel.describeBlock(block);

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(description.portKind()).isEqualTo(PortKinds.ITEM_INPUT);
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(description.baseTexture()).isNotNull();
        assertThat(description.overlayTexture()).isEqualTo(MMCR.id("block/overlay_inputbus_normal"));
        assertThat(description.overlayFaces()).containsExactlyInAnyOrder(Direction.values());
    }

    @Test
    void ordinary_item_is_not_dynamic() {
        var description = DynamicOverlayItemModel.describeItem(Items.STICK);

        assertThat(description.kind()).isNull();
    }
}
