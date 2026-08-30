package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared JEI category for multiblock structure previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureCategory implements IRecipeCategory<MachineStructureDisplay> {
    private static final int MACHINE_NAME_X = 5;
    private static final int MACHINE_NAME_Y = 5;
    private static final int MACHINE_NAME_WIDTH = 160;
    private static final int MACHINE_NAME_HEIGHT = 16;
    private static final float MACHINE_NAME_SCALE = 1.1F;
    private static final int PREVIEW_X = 4;
    private static final int PREVIEW_Y = 23;

    private final IDrawable icon;
    public MachineStructureCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ModBlocks.BASIC_CASING.get());
    }

    @Override public mezz.jei.api.recipe.types.IRecipeType<MachineStructureDisplay> getRecipeType() { return JeiMachineRecipeTypes.STRUCTURE; }
    @Override public Component getTitle() { return Component.translatable("jei.mmcr.multiblock_structure"); }
    @Override
    public int getWidth() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 168;
            case 2 -> 168;
            case 3 -> 168;
            default -> 168;
        };
    }

    @Override
    public int getHeight() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 300;
            case 2 -> 280;
            case 3 -> 220;
            default -> 150;
        };
    }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        display.ingredients().forEach(stack -> builder.addInputSlot(-1000, -1000).add(stack));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        JeiStructurePreviewWidget preview = new JeiStructurePreviewWidget(display.machine(), PREVIEW_X, PREVIEW_Y,
                previewWidth(), previewHeight());
        JeiPreviewLifecycle.registerActive(preview);
        builder.addWidget(preview);
        builder.addInputHandler(preview);
    }

    @Override
    public void draw(MachineStructureDisplay display, IRecipeSlotsView recipeSlotsView,
            GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component machineName = display.machine().displayName();
        int textWidth = font.width(machineName);
        if (textWidth <= 0) return;

        float scale = Math.min(MACHINE_NAME_SCALE, Math.min(
                (float) MACHINE_NAME_WIDTH / textWidth,
                (float) MACHINE_NAME_HEIGHT / font.lineHeight));
        float scaledWidth = textWidth * scale;
        float scaledHeight = font.lineHeight * scale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(
                MACHINE_NAME_X + MACHINE_NAME_WIDTH / 2.0F - scaledWidth / 2.0F,
                MACHINE_NAME_Y + MACHINE_NAME_HEIGHT / 2.0F - scaledHeight);
        graphics.pose().scale(scale, scale);
        graphics.text(font, machineName, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    private static int previewWidth() {
        return 161;
    }

    private static int previewHeight() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 253;
            case 2 -> 233;
            case 3 -> 173;
            default -> 103;
        };
    }
}
