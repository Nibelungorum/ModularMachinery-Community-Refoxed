package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
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

import java.util.Map;

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
    void reactor_controller_item_uses_machine_appearance_base_texture() {
        var item = ModItems.ITEMS.get("reactor_controller").get();

        var description = DynamicOverlayItemModel.describeItem(item);

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.CONTROLLER);
        assertThat(description.machineId()).isEqualTo(MMCR.id("reactor"));
        assertThat(description.baseTexture()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("block/blue_ice"));
    }

    @Test
    void controller_item_resolves_textures_from_current_snapshots_after_definition_is_cached() {
        var block = ((BlockItem) ModItems.ITEMS.get("blast_furnace_controller").get()).getBlock();
        var machineId = MMCR.id("blast_furnace");
        var appearanceSnapshot = MachineAppearanceCache.snapshot();
        var controllerSnapshot = ControllerSpecCache.snapshot();
        RuntimeMachineModelRegistry.definition(block);

        try {
            MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                    MMCR.id("block/basic_casing"), MMCR.id("block/updated_controller_base"), MMCR.id("block/basic_casing"))));
            ControllerSpecCache.replaceSnapshot(Map.of(machineId, new MachineControllerSpec(
                    machineId, MMCR.id("block/updated_controller_overlay"), MMCR.id("block/basic_casing"),
                    MMCR.id("block/basic_casing"), MMCR.id("block/basic_casing"), false)));

            var itemDescription = DynamicOverlayItemModel.describeItem(ModItems.ITEMS.get("blast_furnace_controller").get());
            var blockDescription = DynamicOverlayItemModel.describeBlock(block);

            assertThat(itemDescription.baseTexture()).isEqualTo(MMCR.id("block/updated_controller_base"));
            assertThat(itemDescription.overlayTexture()).isEqualTo(MMCR.id("block/updated_controller_overlay"));
            assertThat(blockDescription.baseTexture()).isEqualTo(MMCR.id("block/updated_controller_base"));
            assertThat(blockDescription.overlayTexture()).isEqualTo(MMCR.id("block/updated_controller_overlay"));
        } finally {
            MachineAppearanceCache.replaceSnapshot(appearanceSnapshot);
            ControllerSpecCache.replaceSnapshot(controllerSnapshot);
        }
    }

    @Test
    void port_item_resolves_from_block_item() {
        var block = cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get();

        var description = DynamicOverlayItemModel.describeItem(block.asItem());

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(description.portKind()).isEqualTo(PortKinds.ITEM_INPUT);
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(description.baseTexture()).isNotNull();
        assertThat(description.overlayTexture()).isEqualTo(MMCR.id("block/overlay_inputbus_normal"));
        assertThat(description.overlayFaces()).containsExactlyInAnyOrder(Direction.values());
    }

    @Test
    void parallel_controller_item_uses_port_style_dynamic_overlay() {
        var block = cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(ParallelTier.X16.idSuffix()).get();

        var description = DynamicOverlayItemModel.describeItem(block.asItem());

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(description.baseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(description.overlayTexture()).isEqualTo(MMCR.id("block/overlay_parallel_controller_reinforced"));
        assertThat(description.overlayFaces()).containsExactlyInAnyOrder(Direction.values());
    }

    @Test
    void factory_controller_item_uses_port_style_dynamic_overlay() {
        var block = cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get();

        var description = DynamicOverlayItemModel.describeItem(block.asItem());

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(description.baseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(description.overlayTexture()).isEqualTo(MMCR.id("block/overlay_factory_controller"));
        assertThat(description.overlayFaces()).containsExactlyInAnyOrder(Direction.values());
    }

    @Test
    void ordinary_item_is_not_dynamic() {
        var description = DynamicOverlayItemModel.describeItem(Items.STICK);

        assertThat(description.kind()).isNull();
    }
}
