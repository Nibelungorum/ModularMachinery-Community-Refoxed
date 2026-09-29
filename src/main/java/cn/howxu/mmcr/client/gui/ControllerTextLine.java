package cn.howxu.mmcr.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * One logical controller screen line and its internal render color.
 *
 * @param text the line text
 * @param color the render color
 * @param icon optional native resource icon rendered before the text
 * @param tooltip tooltip shown for the line or its icon
 * @param leftIndent left inset for the complete line
 * @author howxu <dev@howxu.cn>
 */
public record ControllerTextLine(Component text, int color, Icon icon, List<Component> tooltip, int leftIndent) {
    public static final int DEFAULT_COLOR = 0xFF403E53;

    public ControllerTextLine(Component text, int color) {
        this(text, color, null, List.of(), 0);
    }

    public ControllerTextLine(Component text, int color, Icon icon, List<Component> tooltip) {
        this(text, color, icon, tooltip, 0);
    }

    public ControllerTextLine {
        tooltip = List.copyOf(tooltip == null ? List.of() : tooltip);
        if (leftIndent < 0) throw new IllegalArgumentException("leftIndent must be non-negative");
    }

    public int textXOffset() {
        return leftIndent + (icon == null ? 0 : icon.width() + 2);
    }

    public sealed interface Icon permits ItemIcon, FluidIcon, ChemicalIcon {
        int width();
    }

    public record ItemIcon(ItemStack stack) implements Icon {
        public ItemIcon {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        @Override
        public int width() {
            return 9;
        }
    }

    public record FluidIcon(FluidStack stack) implements Icon {
        public FluidIcon {
            stack = stack == null ? FluidStack.EMPTY : stack.copy();
        }

        @Override
        public int width() {
            return 9;
        }
    }

    public record ChemicalIcon(ResourceLocation chemicalId, long amount) implements Icon {
        public ChemicalIcon {
            if (chemicalId == null || amount <= 0L) throw new IllegalArgumentException("Invalid chemical icon");
        }

        @Override
        public int width() {
            return 9;
        }
    }
}
