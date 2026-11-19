package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.api.stacks.GenericStack;
import appeng.client.api.AEKeyRendering;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.UpgradeableMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Renders stocking configuration as resource markers, independently of the network amount below them.
 * @author howxu <dev@howxu.cn>
 */
public abstract class InterfaceMarkerScreen<M extends UpgradeableMenu<?>> extends UpgradeableScreen<M> {
    private final Set<Slot> markerSlots;

    protected InterfaceMarkerScreen(M menu, Inventory inventory, Component title, ScreenStyle style, List<Slot> configSlots) {
        super(menu, inventory, title, style);
        markerSlots = menu.getTarget() instanceof StockingInterfaceBlockEntity ? Set.copyOf(configSlots) : Set.of();
    }

    @Override
    protected void renderSlotContents(GuiGraphicsExtractor graphics, ItemStack stack, Slot slot, @Nullable String countText) {
        if (!stack.isEmpty() && markerSlots.contains(slot)) {
            var wrapped = GenericStack.unwrapItemStack(stack);
            if (wrapped != null) {
                // AE2 draws the wrapped amount with the item model, before vanilla draws decorations.
                AEKeyRendering.drawInGui(minecraft, graphics, slot.x, slot.y, wrapped.what());
                return;
            }
            countText = "";
        }
        super.renderSlotContents(graphics, stack, slot, countText);
    }
}
