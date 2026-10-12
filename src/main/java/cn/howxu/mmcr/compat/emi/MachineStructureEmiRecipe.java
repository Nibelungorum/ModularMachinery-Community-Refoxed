package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.compat.jei.MachineStructureDisplay;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.LongSupplier;

/** A browse-only default-stage structure with a real, machine-bound blueprint output.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureEmiRecipe implements EmiRecipe {
    private final EmiRecipeCategory category;
    private final MachineStructureDisplay display;
    private final Version version;
    private final List<EmiIngredient> inputs;
    private final EmiStack output;

    public MachineStructureEmiRecipe(EmiRecipeCategory category, MachineStructureDisplay display) {
        this(category, display, new Version(0, 0, 0, 0));
    }

    public MachineStructureEmiRecipe(EmiRecipeCategory category, MachineStructureDisplay display, Version version) {
        this.category = category;
        this.display = display;
        this.version = version;
        inputs = display.materials().entries().stream().map(entry -> (EmiIngredient) EmiStack.of(entry.stack(), entry.count())).toList();
        ItemStack blueprint = new ItemStack(ModItems.BLUEPRINT.get());
        blueprint.set(DataComponents.CUSTOM_NAME, display.machine().displayName());
        blueprint.set(ModDataComponents.BLUEPRINT_MACHINE.get(), display.machine().registryName());
        output = EmiStack.of(blueprint).comparison(Comparison.compareComponents());
    }

    public MachineStructureDisplay display() { return display; }
    public Version version() { return version; }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public ResourceLocation getId() { return MachineEmiRecipe.id("structure", display.machine().registryName()); }
    @Override public List<EmiIngredient> getInputs() { return inputs.stream().map(EmiIngredient::copy).toList(); }
    @Override public List<EmiStack> getOutputs() { return List.of(output.copy()); }
    @Override public int getDisplayWidth() { return 180; }
    @Override public int getDisplayHeight() { return 220; }
    @Override public boolean supportsRecipeTree() { return false; }
    @Override public boolean hideCraftable() { return true; }

    @Override public void addWidgets(WidgetHolder holder) {
        int width = holder.getWidth(), height = holder.getHeight();
        holder.addDrawable(0, 0, width, height, (graphics, mouseX, mouseY, delta) -> {
            RecipeDisplayDrawing.drawBackground(graphics, true, 0, 0, width, height);
            var font = Minecraft.getInstance().font;
            var name = display.machine().displayName();
            float scale = Math.min(1F, (float) (width - 28) / Math.max(1, font.width(name)));
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(4, 5, 0);
                graphics.pose().scale(scale, scale, 1F);
                graphics.drawString(font, name, 0, 0, 0xFFFFFFFF, false);
            } finally { graphics.pose().popPose(); }
        });
        holder.addSlot(output.copy(), width - 22, 2).recipeContext(this);
        LongSupplier clock = System::currentTimeMillis;
        EmiStructurePreviewWidget.add(holder, display, version, 4, 24, width - 8,
                Math.max(18, height - 50 - EmiStructurePreviewWidget.CONTROLS_HEIGHT), clock);
        EmiStructureMaterialWidget.add(holder, display, 4, height - 22, clock);
    }
}
