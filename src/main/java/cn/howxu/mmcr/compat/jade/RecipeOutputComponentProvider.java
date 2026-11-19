package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
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

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Renders the controller's current recipe outputs at the tail of the Jade tooltip.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeOutputComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    public static final Identifier UID = RecipeOutputDataProvider.UID;

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL - 9;
    }

    /** A missing row suppresses an invalid value of a supported output type. */
    @FunctionalInterface
    public interface Renderer<O extends MachineOutput> {
        @Nullable Consumer<ITooltip> row(O output, long amount);
    }

    private static final Map<OutputType<?>, Renderer<?>> RENDERERS = new IdentityHashMap<>();

    static {
        register(MachineOutput.ItemOutput.TYPE, (item, amount) ->
                item.stack().isEmpty() ? null : tip -> renderItem(tip, item, amount));
        register(MachineOutput.FluidOutput.TYPE, (fluid, amount) ->
                fluid.stack().isEmpty() ? null : tip -> renderFluid(tip, fluid, amount));
    }

    public static <O extends MachineOutput> void register(OutputType<O> type, Renderer<O> renderer) {
        Renderer<?> previous = RENDERERS.putIfAbsent(type, renderer);
        if (previous != null && previous != renderer) throw new IllegalStateException("Duplicate Jade renderer for " + type.id());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        List<MachineOutputAmount> outputs = RecipeOutputCodec.read(accessor.getServerData(),
                accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE));
        boolean labelAdded = false;
        for (MachineOutputAmount output : outputs) {
            if (output.amount() <= 0L) continue;
            var row = row(output.output(), output.amount());
            if (row == null) continue;
            if (!labelAdded) {
                tooltip.add(Component.translatable("jade.mmcr.machine_controller.recipe_output"));
                labelAdded = true;
            }
            row.accept(tooltip);
        }
    }

    @SuppressWarnings("unchecked") // register pairs each renderer with its canonical OutputType<O>.
    private static @Nullable Consumer<ITooltip> row(MachineOutput output, long amount) {
        var type = output.outputType();
        var renderer = (Renderer<MachineOutput>) RENDERERS.get(type);
        if (renderer != null) return renderer.row(output, amount);
        return tooltip -> tooltip.add(genericText(type, amount));
    }

    public static Component genericText(OutputType<?> type, long amount) {
        Component name = Component.translatableWithFallback(type.presentation().translationKey(), type.id().toString());
        return Component.translatable("jade.mmcr.machine_controller.recipe_output.generic", amount, name);
    }

    private static void renderItem(ITooltip tooltip, MachineOutput.ItemOutput item, long amount) {
        ItemStack stack = item.resolvedStack();
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
                stack.getStyledHoverName());
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

}
