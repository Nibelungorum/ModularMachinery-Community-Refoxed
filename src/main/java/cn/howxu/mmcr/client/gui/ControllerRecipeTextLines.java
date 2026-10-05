package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.compat.ars_nouveau.SourceOutput;
import cn.howxu.mmcr.compat.botania.ManaOutput;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.util.SaturatingLong;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds controller text lines for a recipe's outputs.
 *
 * @author howxu <dev@howxu.cn>
 */
final class ControllerRecipeTextLines {
    static final int MAX_OUTPUT_LINES = 64;
    private static final int OUTPUT_INDENT = 4;

    private ControllerRecipeTextLines() {
    }

    static List<ControllerTextLine> create(ControllerRecipePresentation presentation) {
        if (presentation == null) return List.of();
        List<ControllerTextLine> lines = new ArrayList<>();
        if (presentation.energyInputPerTick() > 0L) {
            lines.add(summary("gui.mmcr.controller.recipe.energy_input",
                    "gui.mmcr.controller.recipe.energy_input_exact", ReadableNumber.format(presentation.energyInputPerTick()),
                    ReadableNumber.formatExact(presentation.energyInputPerTick())));
        }
        if (presentation.energyOutputPerTick() > 0L) {
            lines.add(summary("gui.mmcr.controller.recipe.energy_output",
                    "gui.mmcr.controller.recipe.energy_output_exact", ReadableNumber.format(presentation.energyOutputPerTick()),
                    ReadableNumber.formatExact(presentation.energyOutputPerTick())));
        }
        if (presentation.heatOutputPerTick() > 0D) {
            MekanismBridge.HeatDisplayData heat = MekanismBridge.get()
                    .heatDisplayData(presentation.heatOutputPerTick());
            String compact = formatHeat(heat.value());
            String exact = exactHeat(heat.value());
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe.heat_output",
                    compact, heat.unit()), MachineControllerScreen.STATUS_LABEL_COLOR, null,
                    List.of(Component.translatable("gui.mmcr.controller.recipe.heat_output_exact",
                            exact, heat.unit()))));
        }
        List<ControllerTextLine> outputs = outputs(presentation.outputs());
        if (!outputs.isEmpty()) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.title"),
                    MachineControllerScreen.STATUS_LABEL_COLOR));
            lines.addAll(outputs);
        }
        return List.copyOf(lines);
    }

    static List<ControllerTextLine> outputs(List<MachineOutputAmount> outputs) {
        List<ControllerTextLine> lines = new ArrayList<>();
        for (MachineOutputAmount output : outputs) {
            MachineOutput ownedOutput = output.output();
            if (!isRenderable(ownedOutput, output.amount())) continue;
            lines.add(line(ownedOutput, output.amount()));
        }
        if (lines.size() > MAX_OUTPUT_LINES) {
            lines.subList(MAX_OUTPUT_LINES - 1, lines.size()).clear();
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.more"),
                    MachineControllerScreen.STATUS_LABEL_COLOR, null, List.of(), OUTPUT_INDENT));
        }
        return List.copyOf(lines);
    }

    static Optional<ControllerTextLine.Icon> firstRenderableOutputIcon(
            ControllerRecipePresentation presentation) {
        if (presentation == null) return Optional.empty();
        for (MachineOutputAmount output : presentation.outputs()) {
            MachineOutput ownedOutput = output.output();
            if (isRenderable(ownedOutput, output.amount())) return Optional.of(icon(ownedOutput, output.amount()));
        }
        return Optional.empty();
    }

    static List<ControllerTextLine> forRecipe(MachineRecipe recipe, long parallelism) {
        if (recipe == null || parallelism < 1L) return List.of();
        return outputs(recipe.machineOutputs().stream()
                .map(output -> new MachineOutputAmount(output,
                        SaturatingLong.multiply(MachineOutput.scaledAmount(output), parallelism)))
                .toList());
    }

    private static boolean isRenderable(MachineOutput output, long amount) {
        if (amount <= 0L) return false;
        if (output instanceof SourceOutput) return true;
        if (output instanceof ManaOutput) return true;
        if (output instanceof MachineOutput.ItemOutput item) return !item.stack().isEmpty();
        if (output instanceof MachineOutput.FluidOutput fluid) return !fluid.stack().isEmpty();
        if (output instanceof LoadedChemicalOutput chemical) {
            return MekanismBridge.get().chemicalRenderData(chemical.id()) != null;
        }
        return false;
    }

    private static ControllerTextLine line(MachineOutput output, long amount) {
        ControllerTextLine.Icon icon = icon(output, amount);
        if (output instanceof ManaOutput) {
            return new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.mana",
                    ReadableNumber.format(amount)), MachineControllerScreen.STATUS_LABEL_COLOR,
                    icon, List.of(Component.translatable("gui.mmcr.mana.exact", ReadableNumber.formatExact(amount))),
                    OUTPUT_INDENT);
        }
        if (output instanceof SourceOutput) {
            return new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.source",
                    ReadableNumber.format(amount)), MachineControllerScreen.STATUS_LABEL_COLOR,
                    icon, List.of(Component.translatable("gui.mmcr.source.exact", ReadableNumber.formatExact(amount))),
                    OUTPUT_INDENT);
        }
        if (output instanceof MachineOutput.ItemOutput item) {
            ItemStack stack = item.resolvedStack();
            MutableComponent name = Component.empty().append(stack.getHoverName())
                    .withStyle(stack.getRarity().getStyleModifier());
            if (stack.has(DataComponents.CUSTOM_NAME)) name.withStyle(ChatFormatting.ITALIC);
            String count = amount > 1L
                    ? ReadableNumber.formatForSlot(amount, 0, "") + " " : "";
            return new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.item", count,
                    name), MachineControllerScreen.STATUS_LABEL_COLOR,
                    icon,
                    List.of(name, Component.literal(ReadableNumber.formatExact(amount))),
                    OUTPUT_INDENT);
        }
        if (output instanceof MachineOutput.FluidOutput fluid) {
            String displayAmount = fluidAmount(amount);
            return new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.fluid", displayAmount,
                    fluidName(fluid.stack())), MachineControllerScreen.STATUS_LABEL_COLOR,
                    icon,
                    List.of(fluidName(fluid.stack()), exactFluidAmount(amount)),
                    OUTPUT_INDENT);
        }
        if (output instanceof LoadedChemicalOutput chemical) {
            MekanismBridge.ChemicalRenderData data = MekanismBridge.get().chemicalRenderData(chemical.id());
            if (data == null) return new ControllerTextLine(Component.empty(), MachineControllerScreen.STATUS_LABEL_COLOR);
            return new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_output.chemical",
                    fluidAmount(amount), data.displayName()), MachineControllerScreen.STATUS_LABEL_COLOR,
                    icon,
                    List.of(data.displayName(), exactFluidAmount(amount)),
                    OUTPUT_INDENT);
        }
        throw new IllegalArgumentException("Unsupported controller recipe output: " + output.outputType().id());
    }

    private static ControllerTextLine.Icon icon(MachineOutput output, long amount) {
        if (output instanceof ManaOutput) {
            return new ControllerTextLine.ItemIcon(new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("botania:creative_pool"))));
        }
        if (output instanceof SourceOutput) {
            return new ControllerTextLine.SourceIcon();
        }
        if (output instanceof MachineOutput.ItemOutput item) {
            ItemStack stack = item.resolvedStack();
            stack.setCount(1);
            return new ControllerTextLine.ItemIcon(stack);
        }
        if (output instanceof MachineOutput.FluidOutput fluid) {
            FluidStack stack = fluid.stack().copy();
            stack.setAmount(1);
            return new ControllerTextLine.FluidIcon(stack);
        }
        if (output instanceof LoadedChemicalOutput chemical) {
            return new ControllerTextLine.ChemicalIcon(chemical.id(), amount);
        }
        throw new IllegalArgumentException("Unsupported controller recipe output: " + output.outputType().id());
    }

    private static ControllerTextLine summary(String textKey, String tooltipKey, String value, String exact) {
        return new ControllerTextLine(Component.translatable(textKey, value), MachineControllerScreen.STATUS_LABEL_COLOR,
                null, List.of(Component.translatable(tooltipKey, exact)));
    }

    private static String fluidAmount(long amount) {
        return amount <= 10L
                ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
    }

    private static Component exactFluidAmount(long amount) {
        return Component.translatable("gui.mmcr.controller.recipe_output.amount_mb",
                ReadableNumber.formatExact(amount));
    }

    private static String formatHeat(double amount) {
        return ReadableNumber.format(BigDecimal.valueOf(amount));
    }

    private static String exactHeat(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private static Component fluidName(FluidStack stack) {
        try {
            return stack.getHoverName();
        } catch (RuntimeException ignored) {
            return Component.literal(stack.getFluid().toString());
        }
    }
}
