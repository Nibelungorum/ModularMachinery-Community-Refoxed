package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.StructurePreviewPanel;
import cn.howxu.mmcr.mixin.compat.jei.RecipeLayoutAccessor;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Shared JEI category for multiblock structure previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureCategory implements IRecipeCategory<MachineStructureDisplay> {
    private static final float MACHINE_NAME_SCALE = 1.1F;

    private final IDrawable icon;
    private final IGuiHelper guiHelper;
    private final IIngredientManager ingredientManager;

    public MachineStructureCategory(IGuiHelper guiHelper, IIngredientManager ingredientManager) {
        this.guiHelper = guiHelper;
        icon = guiHelper.createDrawableItemLike(ModBlocks.BASIC_CASING.get());
        this.ingredientManager = ingredientManager;
    }

    @Override public RecipeType<MachineStructureDisplay> getRecipeType() { return JeiMachineRecipeTypes.STRUCTURE; }
    @Override public Component getTitle() { return Component.translatable("jei.mmcr.multiblock_structure"); }
    @Override public int getWidth() { return StructurePreviewLayout.WIDTH; }
    @Override
    public int getHeight() {
        return StructurePreviewLayout.heightForScale((int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        for (var summary : display.materialsByStage().values()) {
            for (var entry : summary.entries()) {
                IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY).addItemStack(entry.stack());
                MachineRecipeCategory.setItemOverlay(slot, "", MachineRecipeCategory.itemQuantityText(entry.count()));
                slot.addRichTooltipCallback((view, tooltip) -> entry.tooltipLines().forEach(tooltip::add));
            }
        }

        // JEI also calls setRecipe for indexing, before there is a selected preview stage.
        for (ItemStack stack : display.ingredients()) {
            builder.addInputSlot(-1000, -1000).addItemStack(stack);
        }
        builder.addOutputSlot(-1000, -1000).addItemStack(structureOutput(display));
        var sideButton = StructurePreviewLayout.sideButtonOrigin(StructurePreviewLayout.viewport(getHeight()));
        builder.moveRecipeTransferButton(sideButton.x(), sideButton.y());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        ScreenRectangle viewport = StructurePreviewLayout.viewport(getHeight());
        var panel = new StructurePreviewPanel(display.machine());
        JeiStructurePreviewWidget preview = new JeiStructurePreviewWidget(panel, viewport.left(), viewport.top(),
                viewport.width(), viewport.height(), ingredientManager);
        JeiPreviewLifecycle.registerActive(preview);
        builder.addWidget(preview);
        builder.addInputHandler(preview);
        var layoutSlots = builder.getRecipeSlots();
        var stageSlots = new StructureStageSlots(display, layoutSlots, guiHelper, panel::stageNumber);
        if (panel.hasMultipleStages() && builder instanceof RecipeLayoutAccessor layout) {
            layout.mmcr$setRecipeSlotsView(stageSlots);
        }
        var materials = new StructureMaterialWidget(stageSlots::materials, StructurePreviewLayout.materialOrigin(viewport));
        builder.addSlottedWidget(materials, layoutSlots.getSlots(RecipeIngredientRole.RENDER_ONLY));
        builder.addInputHandler(materials);
    }

    @Override
    public void draw(MachineStructureDisplay display, IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component machineName = display.machine().displayName();
        int textWidth = font.width(machineName);
        if (textWidth <= 0) return;

        ScreenRectangle title = StructurePreviewLayout.TITLE;
        float scale = Math.min(MACHINE_NAME_SCALE, Math.min(
                (float) title.width() / textWidth, (float) title.height() / font.lineHeight));
        graphics.pose().pushPose();
        graphics.pose().translate(title.left() + (title.width() - textWidth * scale) / 2.0F,
                title.top() + (title.height() - font.lineHeight * scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, machineName, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    @Override
    public ResourceLocation getRegistryName(MachineStructureDisplay display) {
        ResourceLocation machineId = display.machine().registryName();
        return MMCR.id("structure/" + machineId.getNamespace() + "/" + machineId.getPath());
    }

    static ItemStack structureOutput(MachineStructureDisplay display) {
        ItemStack output = new ItemStack(ModItems.BLUEPRINT.get());
        output.set(DataComponents.CUSTOM_NAME, display.machine().displayName());
        return output;
    }
}
