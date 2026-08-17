package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Shared JEI category for multiblock structure previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureCategory implements IRecipeCategory<MachineStructureDisplay> {
    private static final int PREVIEW_X = 2;
    private static final int PREVIEW_Y = 4;

    private final IDrawable icon;

    public MachineStructureCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ModBlocks.BASIC_CASING.get());
    }

    @Override public IRecipeType<MachineStructureDisplay> getRecipeType() { return JeiMachineRecipeTypes.STRUCTURE; }
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
        builder.addText(display.machine().displayName(), PREVIEW_X, 4);
        JeiStructurePreviewWidget preview = new JeiStructurePreviewWidget(display.machine(), PREVIEW_X, PREVIEW_Y,
                previewWidth(), previewHeight());
        JeiPreviewLifecycle.registerActive(preview);
        builder.addWidget(preview);
        builder.addInputHandler(preview);
    }

    private static int previewWidth() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 164;
            case 2 -> 164;
            case 3 -> 164;
            default -> 164;
        };
    }

    private static int previewHeight() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 269;
            case 2 -> 248;
            case 3 -> 190;
            default -> 120;
        };
    }
}
