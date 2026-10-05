package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.ReadableNumber;
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
            var kind = port.kind();
            if (FluxNetworksIds.INPUT.equals(kind.id()) || FluxNetworksIds.OUTPUT.equals(kind.id())) {
                return List.of(Component.translatable(FluxNetworksIds.INPUT.equals(kind.id())
                                ? "tooltip.mmcr.fluxnetworks.input" : "tooltip.mmcr.fluxnetworks.output"),
                        Component.translatable("tooltip.mmcr.fluxnetworks.configure"));
            }
            if (PneumaticIds.INPUT.equals(kind.id()) || PneumaticIds.OUTPUT.equals(kind.id())) {
                return List.of(Component.translatable(PneumaticIds.INPUT.equals(kind.id())
                                ? "tooltip.mmcr.pneumaticcraft.input" : "tooltip.mmcr.pneumaticcraft.output"),
                        Component.translatable("tooltip.mmcr.pneumaticcraft.volume", 10_000),
                        Component.translatable("tooltip.mmcr.pneumaticcraft.safe_pressure", 20),
                        Component.translatable("tooltip.mmcr.pneumaticcraft.equalization"));
            }
            if (kind instanceof PortKinds.ChemicalKind chemical) return chemicalTooltip(chemical);
            if (kind instanceof PortKinds.HeatKind heat) return heatTooltip(heat);
            return kind.itemBusSize().map(InterfaceTooltips::itemTooltip)
                    .or(() -> kind.extendedItemBusSize().map(InterfaceTooltips::itemTooltip))
                    .or(() -> kind.fluidHatchSize().map(InterfaceTooltips::fluidTooltip))
                    .or(() -> kind.extendedFluidHatchSize().map(InterfaceTooltips::fluidTooltip))
                    .or(() -> kind.energyHatchSize().map(InterfaceTooltips::energyTooltip))
                    .or(() -> kind.extendedEnergyHatchSize().map(InterfaceTooltips::energyTooltip))
                    .or(() -> kind.combinedPortSize().map(size -> combinedTooltip(size.itemTypes(), size.fluidTypes())))
                    .or(() -> kind.extendedCombinedPortSize().map(size -> combinedTooltip(size.itemTypes(), size.fluidTypes())))
                    .orElse(List.of());
        }
        if (block instanceof MachineControllerBlock controller) {
            var registration = MachineDefinitions.effectiveSnapshot().get(controller.machineId());
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
        return itemTooltip(size.slots());
    }

    public static List<Component> itemTooltip(ExtendedItemBusSize size) {
        return itemTooltip(size.slots());
    }

    private static List<Component> itemTooltip(int slots) {
        return List.of(capacityLine(slots, Component.translatable("tooltip.mmcr.interface.unit.slots")));
    }

    public static List<Component> fluidTooltip(FluidHatchSize size) {
        return fluidTooltip(size.capacity(), size.capacity());
    }

    public static List<Component> fluidTooltip(ExtendedFluidHatchSize size) {
        return List.of(capacityLine(size.slots(), Component.translatable("tooltip.mmcr.interface.unit.tanks")));
    }

    static List<Component> fluidTooltip(long capacity, long transfer) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(capacityLine(formatAmount(capacity, "mB")));
        lines.add(rateLine(formatAmount(transfer, "mB/t")));
        return lines;
    }

    public static List<Component> energyTooltip(EnergyHatchSize size) {
        return energyTooltip(size.capacity(), size.transfer());
    }

    public static List<Component> energyTooltip(ExtendedEnergyHatchSize size) {
        return energyTooltip(size.capacity(), size.transfer());
    }

    static List<Component> energyTooltip(long capacity, long transfer) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(capacityLine(formatAmount(capacity, "FE")));
        lines.add(rateLine(formatAmount(transfer, "FE/t")));
        return lines;
    }

    private static List<Component> chemicalTooltip(PortKinds.ChemicalKind kind) {
        return List.of(capacityLine(formatAmount(kind.capacity(), "mB")));
    }

    private static List<Component> heatTooltip(PortKinds.HeatKind kind) {
        return List.of(capacityLine(formatAmount(kind.capacity(), "J/K")));
    }

    private static List<Component> combinedTooltip(int itemTypes, int fluidTypes) {
        return List.of(
                capacityLine(itemTypes, Component.translatable("tooltip.mmcr.interface.unit.slots")),
                capacityLine(fluidTypes, Component.translatable("tooltip.mmcr.interface.unit.tanks")));
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

    private static String formatParallel(long parallelism) {
        return parallelism + "x";
    }

    private static String formatAmount(long amount, String unit) {
        return ReadableNumber.format(amount) + " " + unit;
    }

    private static String formatAmount(double amount, String unit) {
        return java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() + " " + unit;
    }

    private InterfaceTooltips() {}
}
