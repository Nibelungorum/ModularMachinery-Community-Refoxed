package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltip formatting for MMCR controller and IO interface items.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InterfaceTooltips {

    public static List<Component> tooltipLines(Block block) {
        if (block instanceof IOPortBlock port) {
            return port.kind().itemBusSize().map(InterfaceTooltips::itemTooltip)
                    .or(() -> port.kind().fluidHatchSize().map(InterfaceTooltips::fluidTooltip))
                    .or(() -> port.kind().energyHatchSize().map(InterfaceTooltips::energyTooltip))
                    .orElse(List.of());
        }
        if (block instanceof MachineControllerBlock controller) {
            var registration = MachineDefinitions.getRegistration(controller.machineId());
            if (registration == null) return List.of();
            return registration.controllerSpec().tooltip().stream()
                    .map(Component::translatable)
                    .map(Component.class::cast)
                    .toList();
        }
        if (block instanceof ParallelControllerBlock controller) {
            return List.of(parallelLine(controller.tier()));
        }
        if (block instanceof FactorySchedulerBlock) {
            return List.of(Component.translatable("tooltip.mmcr.factory_controller.multithreading"));
        }
        return List.of();
    }

    public static List<Component> itemTooltip(ItemBusSize size) {
        return List.of(capacityLine(size.slots(), Component.translatable("tooltip.mmcr.interface.unit.slots")));
    }

    public static List<Component> fluidTooltip(FluidHatchSize size) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(capacityLine(formatAmount(size.capacity(), "mb")));
        lines.add(rateLine(size.transfer() + "mb/t"));
        return lines;
    }

    public static List<Component> energyTooltip(EnergyHatchSize size) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(capacityLine(formatAmount(size.capacity(), "FE")));
        lines.add(rateLine(formatAmount(size.transfer(), "FE/t")));
        return lines;
    }

    private static Component capacityLine(Object value) {
        return labeledLine("tooltip.mmcr.interface.capacity_label", value);
    }

    private static Component capacityLine(Object value, Component unit) {
        return labeledLine("tooltip.mmcr.interface.capacity_label", ChatFormatting.RED,
                Component.literal(String.valueOf(value)).append(unit));
    }

    private static Component rateLine(Object value) {
        return labeledLine("tooltip.mmcr.interface.rate_label", ChatFormatting.GREEN, Component.literal(String.valueOf(value)));
    }

    private static MutableComponent labeledLine(String labelKey, Object value) {
        return labeledLine(labelKey, ChatFormatting.RED, Component.literal(String.valueOf(value)));
    }

    private static MutableComponent labeledLine(String labelKey, ChatFormatting labelColor, Component value) {
        return Component.empty()
                .append(Component.translatable(labelKey).withStyle(labelColor))
                .append(value);
    }

    private static Component parallelLine(ParallelTier tier) {
        return Component.translatable("tooltip.mmcr.interface.parallel", formatParallel(tier.maxParallelism()))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private static String formatParallel(int parallelism) {
        return parallelism == Integer.MAX_VALUE ? "2.1Gx" : parallelism + "x";
    }

    private static String formatAmount(int amount, String unit) {
        if (amount == Integer.MAX_VALUE) return "2.1G" + unit;
        if (amount >= 1_000_000 && amount % 1_000_000 == 0) return amount / 1_000_000 + "M" + unit;
        if (amount >= 1_000_000 && amount % 100_000 == 0) return (amount / 100_000) / 10.0D + "M" + unit;
        if (amount >= 1_000 && amount % 1_000 == 0) return amount / 1_000 + "k" + unit;
        return amount + unit;
    }

    private InterfaceTooltips() {}
}
