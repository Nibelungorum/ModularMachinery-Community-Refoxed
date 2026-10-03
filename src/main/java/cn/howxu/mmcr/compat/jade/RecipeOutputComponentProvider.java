package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.compat.ars_nouveau.SourceOutput;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJadeElement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.util.ReadableNumber;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IElementHelper;

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
        boolean labelAdded = false;
        for (MachineOutputAmount output : outputs) {
            MachineOutput owned = output.output();
            long amount = output.amount();
            if (!isRenderable(owned, amount)) continue;
            if (!labelAdded) {
                tooltip.add(Component.translatable("jade.mmcr.machine_controller.recipe_output"));
                labelAdded = true;
            }
            if (owned instanceof MachineOutput.ItemOutput item) renderItem(tooltip, item, amount);
            else if (owned instanceof MachineOutput.FluidOutput fluid) renderFluid(tooltip, fluid, amount);
            else if (owned instanceof LoadedChemicalOutput chemical) renderChemical(tooltip, chemical, amount);
            else if (owned instanceof SourceOutput) renderSource(tooltip, amount);
        }
    }

    private static boolean isRenderable(MachineOutput output, long amount) {
        if (output instanceof SourceOutput) return amount > 0L;
        if (output instanceof MachineOutput.ItemOutput item) {
            return !item.stack().isEmpty() && amount > 0L;
        }
        if (output instanceof MachineOutput.FluidOutput fluid) {
            return !fluid.stack().isEmpty() && amount > 0L;
        }
        if (output instanceof LoadedChemicalOutput chemical) {
            if (amount <= 0L) return false;
            Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                    ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, chemical.id()));
            return holder != null && holder.isPresent();
        }
        return false;
    }

    private static void renderItem(ITooltip tooltip, MachineOutput.ItemOutput item, long amount) {
        ItemStack stack = item.resolvedStack();
        if (stack.isEmpty() || amount <= 0L) return;
        ItemStack iconStack = stack.copy();
        iconStack.setCount(1);
        IElementHelper elements = IElementHelper.get();
        tooltip.add(elements.smallItem(iconStack));
        tooltip.append(elements.spacer(2, 0));
        String count = amount > 1L
                ? ReadableNumber.formatForSlot(amount, 0, "") + " "
                : "";
        MutableComponent name = Component.empty().append(stack.getHoverName())
                .withStyle(stack.getRarity().getStyleModifier());
        if (stack.has(DataComponents.CUSTOM_NAME)) name.withStyle(ChatFormatting.ITALIC);
        Component text = Component.translatable("jade.mmcr.machine_controller.recipe_output.item",
                count,
                name);
        tooltip.append(text);
    }

    private static void renderSource(ITooltip tooltip, long amount) {
        tooltip.add(new SourceJadeElement().translate(new Vec2(0, -1)));
        tooltip.append(IElementHelper.get().spacer(2, 0));
        tooltip.append(Component.translatable("gui.mmcr.source.exact", ReadableNumber.formatExact(amount)));
    }

    private static void renderFluid(ITooltip tooltip, MachineOutput.FluidOutput fluid, long amount) {
        FluidStack stack = fluid.stack();
        if (stack.isEmpty() || amount <= 0L) return;
        JadeFluidObject obj = JadeFluidObject.of(stack.getFluid(), 1L);
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        IElementHelper elements = IElementHelper.get();
        var icon = elements.fluid(obj);
        icon.size(new Vec2(lineHeight + 1, lineHeight - 1)).translate(new Vec2(0, -1));
        tooltip.add(icon);
        tooltip.append(elements.spacer(2, 0));
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
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
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
            int lineHeight = Minecraft.getInstance().font.lineHeight;
            icon.size(new Vec2(lineHeight + 1, lineHeight - 1)).translate(new Vec2(0, -1));
            tooltip.add(icon);
        } catch (RuntimeException ignored) {
            // Unit tests have no client font, but Jade can still lay out the default-sized icon.
        }
        tooltip.append(IElementHelper.get().spacer(2, 0));
        tooltip.append(Component.translatable("jade.mmcr.machine_controller.recipe_output.fluid",
                formattedAmount, name));
    }
}
