package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachinePortAppearanceTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void io_port_defaults_to_basic_casing_base_texture() {
        IOPortBlockEntity port = itemInputBus();

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void reset_restores_basic_casing_base_texture() {
        IOPortBlockEntity port = itemInputBus();
        port.setAppearanceBaseTexture(Identifier.parse("kubejs:block/steel_casing"));

        port.resetAppearanceBaseTexture();

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void io_port_saves_formed_base_texture_to_update_tag() {
        IOPortBlockEntity port = itemInputBus();
        Identifier texture = Identifier.parse("kubejs:block/steel_casing");

        port.bindControllerAppearance(new BlockPos(12, 4, 12), texture);

        var tag = port.getUpdateTag(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertThat(tag.getString("AppearanceBaseTexture")).contains(texture.toString());
        assertThat(tag.getBooleanOr("HasLinkedController", false)).isTrue();
        assertThat(tag.getIntOr("LinkedControllerX", 0)).isEqualTo(12);
        assertThat(tag.getIntOr("LinkedControllerY", 0)).isEqualTo(4);
        assertThat(tag.getIntOr("LinkedControllerZ", 0)).isEqualTo(12);
    }

    @Test
    void io_port_restores_linked_controller_from_update_tag() throws Exception {
        IOPortBlockEntity source = itemInputBus();
        Identifier texture = Identifier.parse("kubejs:block/steel_casing");
        BlockPos controllerPos = new BlockPos(12, 4, 12);
        source.bindControllerAppearance(controllerPos, texture);
        IOPortBlockEntity restored = itemInputBus();

        invokeHandleUpdateTag(restored, source.getUpdateTag(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));

        assertThat(restored.appearanceBaseTexture()).isEqualTo(texture);
        assertThat(restored.linkedControllerPos()).isEqualTo(controllerPos);
    }

    @Test
    void model_data_exposes_formed_base_texture() {
        IOPortBlockEntity port = itemInputBus();
        Identifier texture = Identifier.parse("kubejs:block/steel_casing");

        port.setAppearanceBaseTexture(texture);
        ModelData data = port.getModelData();

        assertThat(data.get(MachineModelDataKeys.PORT_BASE_TEXTURE)).isEqualTo(texture);
    }

    private static IOPortBlockEntity itemInputBus() {
        return (IOPortBlockEntity) ModBlockEntities.BES.get("item_input_bus").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
    }

    private static void invokeHandleUpdateTag(IOPortBlockEntity port, net.minecraft.nbt.CompoundTag tag) throws Exception {
        port.handleUpdateTag(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), tag));
    }
}
