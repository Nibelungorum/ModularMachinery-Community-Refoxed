package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
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
        assertThat(tooltip.get(0).getSiblings().get(1)).isEqualTo(Component.literal("2,097,152 FE"));
        assertThat(tooltip.get(0).getSiblings().get(1).getStyle().getColor()).isNull();
        assertThat(tooltip.get(1).getStyle().getColor()).isNull();
        assertThat(tooltip.get(1).getSiblings().get(0)).isEqualTo(Component.translatable("tooltip.mmcr.interface.rate_label")
                .withStyle(ChatFormatting.GREEN));
        assertThat(tooltip.get(1).getSiblings().get(1)).isEqualTo(Component.literal("131,072 FE/t"));
        assertThat(tooltip.get(1).getSiblings().get(1).getStyle().getColor()).isNull();
    }

    @Test
    void fluid_interface_tooltip_shows_capacity_and_rate() {
        List<Component> tooltip = InterfaceTooltips.fluidTooltip(FluidHatchSize.VACUUM);

        assertThat(tooltip).hasSize(2);
        assertThat(tooltip.get(0).getSiblings().get(0)).isEqualTo(Component.translatable("tooltip.mmcr.interface.capacity_label")
                .withStyle(ChatFormatting.RED));
        assertThat(tooltip.get(0).getSiblings().get(1)).isEqualTo(Component.literal("32,000 mB"));
        assertThat(tooltip.get(1).getSiblings().get(0)).isEqualTo(Component.translatable("tooltip.mmcr.interface.rate_label")
                .withStyle(ChatFormatting.GREEN));
        assertThat(tooltip.get(1).getSiblings().get(1)).isEqualTo(Component.literal("32,000 mB/t"));
    }

    @Test
    void fluid_interface_tooltip_formats_all_sizes_with_compact_units() {
        assertFluidTooltip(FluidHatchSize.TINY, "100 mB", "100 mB/t");
        assertFluidTooltip(FluidHatchSize.SMALL, "400 mB", "400 mB/t");
        assertFluidTooltip(FluidHatchSize.NORMAL, "1,000 mB", "1,000 mB/t");
        assertFluidTooltip(FluidHatchSize.REINFORCED, "2,000 mB", "2,000 mB/t");
        assertFluidTooltip(FluidHatchSize.BIG, "4,500 mB", "4,500 mB/t");
        assertFluidTooltip(FluidHatchSize.HUGE, "8,000 mB", "8,000 mB/t");
        assertFluidTooltip(FluidHatchSize.LUDICROUS, "16,000 mB", "16,000 mB/t");
        assertFluidTooltip(FluidHatchSize.VACUUM, "32,000 mB", "32,000 mB/t");
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

    private static void assertFluidTooltip(FluidHatchSize size, String capacity, String rate) {
        List<Component> tooltip = InterfaceTooltips.fluidTooltip(size);

        assertThat(tooltip.get(0).getSiblings().get(1)).isEqualTo(Component.literal(capacity));
        assertThat(tooltip.get(1).getSiblings().get(1)).isEqualTo(Component.literal(rate));
    }
}
