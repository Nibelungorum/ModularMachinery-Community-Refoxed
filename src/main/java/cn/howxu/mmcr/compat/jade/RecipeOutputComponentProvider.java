package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.util.ReadableNumber;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.overlay.DisplayHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renders the controller's current recipe outputs at the tail of the Jade tooltip.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeOutputComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = MMCR.id("recipe_output");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL - 9;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        List<MachineOutputAmount> outputs = RecipeOutputCodec.read(accessor.getServerData());
        List<MachineOutputAmount> renderable = new ArrayList<>(outputs.size());
        for (MachineOutputAmount output : outputs) {
            if (isRenderable(output)) renderable.add(output);
        }
        if (renderable.isEmpty()) return;
        tooltip.add(Component.translatable("jade.mmcr.machine_controller.recipe_output"));
        for (MachineOutputAmount output : renderable) {
            if (output.output() instanceof MachineOutput.ItemOutput item) renderItem(tooltip, item, output.amount());
            else if (output.output() instanceof MachineOutput.FluidOutput fluid) renderFluid(tooltip, fluid, output.amount());
            else if (output.output() instanceof LoadedChemicalOutput chemical) renderChemical(tooltip, chemical, output.amount());
        }
    }

    private static boolean isRenderable(MachineOutputAmount output) {
        if (output.output() instanceof MachineOutput.ItemOutput item) {
            return !item.stack().isEmpty() && output.amount() > 0L;
        }
        if (output.output() instanceof MachineOutput.FluidOutput fluid) {
            return !fluid.stack().isEmpty() && output.amount() > 0L;
        }
        if (output.output() instanceof LoadedChemicalOutput chemical) {
            if (output.amount() <= 0L) return false;
            Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.get(
                    ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, chemical.id()));
            return holder != null && holder.isPresent();
        }
        return false;
    }

    private static void renderItem(ITooltip tooltip, MachineOutput.ItemOutput item, long amount) {
        ItemStack stack = item.stack();
        if (stack.isEmpty() || amount <= 0L) return;
        ItemStack iconStack = stack.copy();
        iconStack.setCount(1);
        tooltip.add(JadeUI.smallItem(iconStack));
        tooltip.append(JadeUI.spacer(2, 0));
        String count = amount > 1L
                ? ReadableNumber.formatForSlot(amount, 0, "") + " "
                : "";
        Component text = Component.translatable("jade.mmcr.machine_controller.recipe_output.item",
                count,
                stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        tooltip.append(text);
    }

    private static void renderFluid(ITooltip tooltip, MachineOutput.FluidOutput fluid, long amount) {
        FluidStack stack = fluid.stack();
        if (stack.isEmpty() || amount <= 0L) return;
        JadeFluidObject obj = JadeFluidObject.of(stack.getFluid(), 1L);
        int lineHeight = DisplayHelper.font().lineHeight;
        var icon = JadeUI.fluid(obj);
        icon.setFreeSpace(lineHeight + 1, lineHeight - 1);
        tooltip.add(icon.offset(0, -1));
        tooltip.append(JadeUI.spacer(2, 0));
        String formattedAmount = amount <= 10L
                ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
        Component name = ComponentUtils.wrapInSquareBrackets(stack.getHoverName())
                .withStyle(ChatFormatting.WHITE);
        tooltip.append(Component.translatable("jade.mmcr.machine_controller.recipe_output.fluid",
                formattedAmount, name));
    }

    // for mekanism
    private static void renderChemical(ITooltip tooltip, LoadedChemicalOutput chemical, long amount) {
        if (amount <= 0L) return;
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.get(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, chemical.id()));
        if (holder == null || holder.isEmpty()) return;
        Chemical value = holder.get().value();
        String formattedAmount = amount <= 10L
                ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
        Component name = ComponentUtils.wrapInSquareBrackets(value.getTextComponent())
                .withStyle(ChatFormatting.WHITE);
        // here get an icon
        var icon = new JadeChemicalElement(value.getIcon(), value.getTint(), 10,8);
        try {
            int lineHeight = DisplayHelper.font().lineHeight;
            icon.setFreeSpace(lineHeight + 1, lineHeight - 1);
            tooltip.add(icon.offset(0, -1));
        } catch (RuntimeException ignored) {
            // Unit tests have no client font, but Jade can still lay out the default-sized icon.
        }
        tooltip.append(JadeUI.spacer(2, 0));
        tooltip.append(Component.translatable("jade.mmcr.machine_controller.recipe_output.fluid",
                formattedAmount, name));
    }
}
