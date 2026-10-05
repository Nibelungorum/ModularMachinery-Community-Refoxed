package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.api.BotaniaAPIClient;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.gui.HUDHandler;
import vazkii.botania.common.item.BotaniaItems;

/** Native wand HUD with real inventory and a fixed item-transfer direction.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaWandHud implements WandHUD {
    private final ManaPortBlockEntity pool;

    public ManaWandHud(ManaPortBlockEntity pool) { this.pool = pool; }

    @Override
    public void renderHUD(GuiGraphics gui, Window window, Font font, float partialTick) {
        ItemStack poolStack = new ItemStack(pool.getBlockState().getBlock());
        String name = poolStack.getHoverName().getString();
        int centerX = window.getGuiScaledWidth() / 2;
        int centerY = window.getGuiScaledHeight() / 2;
        int width = Math.max(102, font.width(name)) + 4;
        RenderHelper.renderHUDBox(gui, centerX - width / 2, centerY + 8,
                centerX + width / 2, centerY + 48);
        BotaniaAPIClient.instance().drawSimpleManaHUD(gui, window, font, 0x0095FF,
                pool.storage().amount(), pool.storage().capacity(), name);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderHelper.drawTexturedModalRect(gui, HUDHandler.manaBar, centerX - 11, centerY + 30,
                    pool.isOutputtingPower() ? 22 : 0, 38, 22, 15);
            gui.renderItem(new ItemStack(BotaniaItems.MANA_TABLET), centerX - 31, centerY + 30);
            gui.renderItem(poolStack, centerX + 15, centerY + 30);
        } finally {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }
    }
}
