package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.BotaniaBridge;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.client.gui.HUDHandler;

import java.util.List;

/** Recipe-local 122x16 row with an optional native 102x5 mana bar.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaJeiIngredientRenderer implements IIngredientRenderer<ManaJeiIngredient> {
    @Override public int getWidth() { return 122; }
    @Override public int getHeight() { return 16; }

    @Override
    public void render(GuiGraphics gui, ManaJeiIngredient ingredient) {
        if (!BotaniaBridge.get().available()) {
            var font = Minecraft.getInstance().font;
            var text = font.getSplitter().headByWidth(ingredient.tooltip(), getWidth(), Style.EMPTY);
            gui.drawString(font, text.getString(), 0, 4, 0xFF404040, false);
            return;
        }
        LoadedRenderer.render(gui, ingredient);
    }

    @Override @Deprecated(forRemoval = true)
    public List<Component> getTooltip(ManaJeiIngredient ingredient, TooltipFlag flag) {
        return List.of(ingredient.tooltip());
    }

    @Override
    public List<Component> getTooltip(ManaJeiIngredient ingredient, Item.TooltipContext context,
                                      @Nullable Player player, TooltipFlag flag) {
        return List.of(ingredient.tooltip());
    }

    /** Only linked when Botania is available.
     * @author howxu <dev@howxu.cn>
     */
    private static final class LoadedRenderer {
        private static void render(GuiGraphics gui, ManaJeiIngredient ingredient) {
            gui.pose().pushPose();
            try {
                gui.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("botania:creative_pool"))), 0, 0);
                HUDHandler.renderManaBar(gui, 20, 5, 0x0095FF, 1F,
                        (int) Math.min(ingredient.amount(), BotaniaManaIds.CAPACITY), BotaniaManaIds.CAPACITY);
            } finally {
                gui.pose().popPose();
            }
        }
    }
}
