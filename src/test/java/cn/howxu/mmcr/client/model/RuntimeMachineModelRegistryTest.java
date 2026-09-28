package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies dynamic model registration for network interfaces.
 * @author howxu <dev@howxu.cn>
 */
class RuntimeMachineModelRegistryTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void network_interface_is_a_dynamic_port_style_block() {
        Block networkInterface = ModBlocks.NETWORK_INTERFACE.get();

        RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(networkInterface);

        assertThat(definition).isNotNull();
        assertThat(definition.blockStateDefinition().variants())
                .singleElement()
                .extracting(RuntimeMachineModelRegistry.RuntimeVariant::modelId)
                .isEqualTo(DynamicOverlayModelLoader.PORT_ID);
    }

    @Test
    void network_interface_item_uses_the_dynamic_port_base_and_overlay_chain() {
        DynamicOverlayItemModel.Description description = DynamicOverlayItemModel.describeItem(
                ModItems.ITEMS.get("network_interface").get());

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(description.baseTextureSource()).isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
        assertThat(description.overlayTextures()).containsExactly(MMCR.id("block/overlay_network_interface"));
    }

    @Test
    void network_interface_model_data_starts_with_the_basic_casing_texture() {
        NetworkInterfaceBlockEntity entity = (NetworkInterfaceBlockEntity) ModBlockEntities.NETWORK_INTERFACE.get()
                .create(BlockPos.ZERO, ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());

        ModelData data = entity.getModelData();

        assertThat(data.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE))
                .isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
    }
}
