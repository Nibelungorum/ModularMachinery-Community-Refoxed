package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared JEI category for multiblock structure previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureCategory implements IRecipeCategory<MachineStructureDisplay> {
    private static final float MACHINE_NAME_SCALE = 1.1F;

    private final IDrawable icon;
    private final IIngredientManager ingredientManager;
    public MachineStructureCategory(IGuiHelper guiHelper, IIngredientManager ingredientManager) {
        this.ingredientManager = ingredientManager;
        icon = guiHelper.createDrawableItemLike(ModBlocks.BASIC_CASING.get());
    }

    @Override public IRecipeType<MachineStructureDisplay> getRecipeType() { return JeiMachineRecipeTypes.STRUCTURE; }
    @Override public Component getTitle() { return Component.translatable("jei.mmcr.multiblock_structure"); }
    @Override public int getWidth() { return StructurePreviewLayout.WIDTH; }
    @Override
    public int getHeight() {
        return StructurePreviewLayout.heightForScale(Minecraft.getInstance().getWindow().getGuiScale());
    }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        StructureMaterialSummary summary = display.materials();
        for (var entry : summary.entries()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY).add(entry.stack());
            MachineRecipeCategory.setItemOverlay(slot, "", MachineRecipeCategory.itemQuantityText(entry.count()));
            slot.addRichTooltipCallback((view, tooltip) -> entry.tooltipLines().forEach(tooltip::add));
        }

        for (ItemStack stack : summary.transferStacks()) {
            builder.addInputSlot(-1000, -1000).add(stack);
        }
        builder.addOutputSlot(-1000, -1000).add(structureOutput(display));
        var sideButton = StructurePreviewLayout.sideButtonOrigin(StructurePreviewLayout.viewport(getHeight()));
        builder.moveRecipeTransferButton(sideButton.x(), sideButton.y());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        ScreenRectangle viewport = StructurePreviewLayout.viewport(getHeight());
        JeiStructurePreviewWidget preview = new JeiStructurePreviewWidget(display.machine(), viewport.left(), viewport.top(),
                viewport.width(), viewport.height(), ingredientManager);
        JeiPreviewLifecycle.registerActive(preview);
        builder.addWidget(preview);
        builder.addInputHandler(preview);
        List<IRecipeSlotDrawable> slots = builder.getRecipeSlots().getSlots(RecipeIngredientRole.RENDER_ONLY);
        var materials = new StructureMaterialWidget(slots, StructurePreviewLayout.materialOrigin(viewport));
        builder.addSlottedWidget(materials, slots);
        builder.addInputHandler(materials);
    }

    @Override
    public void draw(MachineStructureDisplay display, IRecipeSlotsView recipeSlotsView,
            GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component machineName = display.machine().displayName();
        int textWidth = font.width(machineName);
        if (textWidth <= 0) return;

        ScreenRectangle title = StructurePreviewLayout.TITLE;
        float scale = Math.min(MACHINE_NAME_SCALE, Math.min(
                (float) title.width() / textWidth, (float) title.height() / font.lineHeight));
        graphics.pose().pushMatrix();
        graphics.pose().translate(title.left() + (title.width() - textWidth * scale) / 2.0F,
                title.top() + (title.height() - font.lineHeight * scale) / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, machineName, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    @Override
    public Identifier getIdentifier(MachineStructureDisplay display) {
        Identifier machineId = display.machine().registryName();
        return MMCR.id("structure/" + machineId.getNamespace() + "/" + machineId.getPath());
    }

    static ItemStack structureOutput(MachineStructureDisplay display) {
        ItemStack output = new ItemStack(ModItems.BLUEPRINT.get());
        output.set(DataComponents.CUSTOM_NAME, display.machine().displayName());
        return output;
    }
}
