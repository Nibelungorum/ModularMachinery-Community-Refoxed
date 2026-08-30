package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlocks;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

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
    private static final int MATERIAL_X = 5;
    private static final int MATERIAL_Y_4X = 129;
    private static final int MATERIAL_Y_3X = 199;
    private static final int MATERIAL_Y_2X = 259;
    private static final int MATERIAL_Y_1X = 279;
    private static final int MATERIAL_STEP = 18;
    private static final int MATERIAL_SLOT_COUNT = 9;
    private static final int TRANSFER_BUTTON_X = 171;
    private static final int TRANSFER_BUTTON_Y = 129;

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
        StructureMaterialSummary summary = display.materials();
        int materialY = materialY();
        for (int slotIndex = 0; slotIndex < MATERIAL_SLOT_COUNT; slotIndex++) {
            int entryIndex = slotIndex;
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY,
                            MATERIAL_X + slotIndex * MATERIAL_STEP, materialY)
                    .setStandardSlotBackground();
            if (entryIndex < summary.entries().size()) {
                slot.add(summary.entries().get(entryIndex).stack());
            }
            slot.addRichTooltipCallback((view, tooltip) -> {
                int page = StructureMaterialWidget.pageFor(System.currentTimeMillis(), summary.entries().size());
                int pageEntry = page * StructureMaterialWidget.SLOT_COUNT + entryIndex;
                if (pageEntry < summary.entries().size()) {
                    tooltip.add(Component.translatable("jei.mmcr.machine_recipe.item_count",
                            cn.howxu.mmcr.util.ReadableNumber.formatExact(summary.entries().get(pageEntry).count())));
                }
            });
        }

        for (ItemStack stack : summary.transferStacks()) {
            builder.addInputSlot(-1000, -1000).add(stack);
        }
        builder.addOutputSlot(-1000, -1000).add(structureOutput(display));
        builder.moveRecipeTransferButton(TRANSFER_BUTTON_X, TRANSFER_BUTTON_Y);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineStructureDisplay display, IFocusGroup focuses) {
        JeiStructurePreviewWidget preview = new JeiStructurePreviewWidget(display.machine(), display.defaultSchema(),
                PREVIEW_X, PREVIEW_Y, previewWidth(), previewHeight());
        JeiPreviewLifecycle.registerActive(preview);
        builder.addWidget(preview);
        builder.addInputHandler(preview);
        builder.addWidget(new StructureMaterialWidget(display.materials(),
                builder.getRecipeSlots().getSlots(RecipeIngredientRole.RENDER_ONLY),
                MATERIAL_X, materialY()));
    }

    @Override
    public void onDisplayedIngredientsUpdate(MachineStructureDisplay display,
            List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
        List<IRecipeSlotDrawable> materialSlots = recipeSlots.stream()
                .filter(slot -> slot.getRole() == RecipeIngredientRole.RENDER_ONLY)
                .toList();
        StructureMaterialWidget.refreshPage(display.materials(), materialSlots, System.currentTimeMillis());
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

    @Override
    public Identifier getIdentifier(MachineStructureDisplay display) {
        Identifier machineId = display.machine().registryName();
        return MMCR.id("structure/" + machineId.getNamespace() + "/" + machineId.getPath());
    }

    static ItemStack structureOutput(MachineStructureDisplay display) {
        ItemStack output = new ItemStack(Items.ENCHANTED_BOOK);
        output.set(DataComponents.CUSTOM_NAME, display.machine().displayName());
        return output;
    }

    private static int materialY() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> MATERIAL_Y_1X;
            case 2 -> MATERIAL_Y_2X;
            case 3 -> MATERIAL_Y_3X;
            default -> MATERIAL_Y_4X;
        };
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
