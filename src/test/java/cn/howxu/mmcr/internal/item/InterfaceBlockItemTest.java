package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.client.model.DynamicOverlayBakedModel;
import cn.howxu.mmcr.client.model.DynamicOverlayItemModel;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceBlockItemTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void item_interface_tooltip_shows_capacity_only() {
        List<Component> tooltip = InterfaceTooltips.itemTooltip(ItemBusSize.LUDICROUS);

        assertThat(tooltip).hasSize(1);
        assertThat(tooltip.getFirst().getString())
                .isEqualTo("tooltip.mmcr.interface.capacity_label32tooltip.mmcr.interface.unit.slots");
        assertThat(tooltip.getFirst().getStyle().getColor()).isNull();
        assertThat(tooltip.getFirst().getSiblings().get(0)).isEqualTo(Component.translatable("tooltip.mmcr.interface.capacity_label")
                .withStyle(ChatFormatting.RED));
        assertThat(tooltip.getFirst().getSiblings().get(1)).isEqualTo(Component.literal("32")
                .append(Component.translatable("tooltip.mmcr.interface.unit.slots")));
        assertThat(tooltip.getFirst().getSiblings().get(1).getStyle().getColor()).isNull();
    }

    @Test
    void energy_interface_tooltip_shows_capacity_and_rate() {
        List<Component> tooltip = InterfaceTooltips.energyTooltip(EnergyHatchSize.ULTIMATE);

        assertThat(tooltip).hasSize(2);
        assertThat(tooltip.get(0).getStyle().getColor()).isNull();
        assertThat(tooltip.get(0).getSiblings().get(0)).isEqualTo(Component.translatable("tooltip.mmcr.interface.capacity_label")
                .withStyle(ChatFormatting.RED));
        assertThat(tooltip.get(1).getStyle().getColor()).isNull();
        assertThat(tooltip.get(1).getSiblings().get(0)).isEqualTo(Component.translatable("tooltip.mmcr.interface.rate_label")
                .withStyle(ChatFormatting.GREEN));
        assertThat(tooltip.get(0).getSiblings().get(1).getStyle().getColor()).isNull();
        assertThat(tooltip.get(1).getSiblings().get(1).getStyle().getColor()).isNull();
    }

    @Test
    void long_interface_quantities_use_readable_si_formatting() {
        assertThat(InterfaceTooltips.energyTooltip(ExtendedEnergyHatchSize.ULTIMATE))
                .extracting(Component::getString)
                .containsExactly(
                        "tooltip.mmcr.interface.capacity_label9.22E FE",
                        "tooltip.mmcr.interface.rate_label9.22E FE/t");
        assertThat(InterfaceTooltips.fluidTooltip(Long.MAX_VALUE, Long.MAX_VALUE))
                .extracting(Component::getString)
                .containsExactly(
                        "tooltip.mmcr.interface.capacity_label9.22E mB",
                        "tooltip.mmcr.interface.rate_label9.22E mB/t");
    }

    @Test
    void parallel_controller_tooltip_shows_parallelism() {
        List<Component> tooltip = InterfaceTooltips.tooltipLines(ModBlocks.BLOCKS.get("parallel_controller_pro").get());

        assertThat(tooltip).containsExactly(Component.translatable("tooltip.mmcr.interface.parallel", "256x")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Test
    void ultimate_parallel_controller_tooltip_shows_exact_parallelism() {
        List<Component> tooltip = InterfaceTooltips.tooltipLines(ModBlocks.BLOCKS.get("parallel_controller_ultimate").get());

        assertThat(tooltip).containsExactly(Component.translatable("tooltip.mmcr.interface.parallel", "2147483647x")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Test
    void factory_controller_tooltip_shows_multithreading() {
        List<Component> tooltip = InterfaceTooltips.tooltipLines(ModBlocks.BLOCKS.get("factory_controller").get());

        assertThat(tooltip).containsExactly(Component.translatable("tooltip.mmcr.factory_controller.multithreading"));
    }

    @Test
    void generated_port_items_have_size_tooltips() {
        for (IOPortKind kind : PortKinds.all().stream()
                .filter(candidate -> candidate.extendedItemBusSize().isPresent()
                        || candidate.extendedFluidHatchSize().isPresent()
                        || candidate.extendedEnergyHatchSize().isPresent()
                        || candidate.combinedPortSize().isPresent()
                        || candidate.extendedCombinedPortSize().isPresent())
                .toList()) {
            assertThat(InterfaceTooltips.tooltipLines(ModBlocks.BLOCKS.get(kind.id()).get()))
                    .as(kind.id())
                    .isNotEmpty();
        }
    }

    @Test
    void extended_and_combined_ports_use_linked_controller_model_data() {
        for (IOPortKind kind : PortKinds.all().stream()
                .filter(candidate -> candidate.extendedItemBusSize().isPresent()
                        || candidate.extendedFluidHatchSize().isPresent()
                        || candidate.extendedEnergyHatchSize().isPresent()
                        || candidate.combinedPortSize().isPresent()
                        || candidate.extendedCombinedPortSize().isPresent())
                .toList()) {
            String id = kind.id();
            IOPortBlockEntity port = (IOPortBlockEntity) ModBlockEntities.BES.get(id).get().create(
                    BlockPos.ZERO, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
            var linkedTexture = net.minecraft.resources.Identifier.parse("kubejs:block/steel_casing");
            port.setAppearanceBaseTexture(linkedTexture);

            DynamicOverlayItemModel.Description description = DynamicOverlayItemModel.describeItem(
                    ModItems.ITEMS.get(id).get());
            DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(
                    null, port.getModelData().get(MachineModelDataKeys.PORT_BASE_TEXTURE), description.overlayTexture());

            assertThat(textures.base()).as(id).isEqualTo(linkedTexture);
        }
    }

}
