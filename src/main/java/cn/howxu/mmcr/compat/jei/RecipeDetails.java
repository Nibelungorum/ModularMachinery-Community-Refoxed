package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/** Client-only JEI recipe details, independent of transfer slots and ingredient search indexing.
 * @author howxu <dev@howxu.cn>
 */
public interface RecipeDetails {
    boolean handles(MachineRequirement requirement);
    int order();
    void append(MachineRecipeDisplay display, List<Row> rows);
    default void index(IRecipeLayoutBuilder builder, MachineRecipeDisplay display) {}

    record Row(Component label, @Nullable ResourceLocation icon, List<Component> tooltip, @Nullable Renderer renderer) {
        public Row { tooltip = List.copyOf(tooltip); }
        public Row(Component label, @Nullable ResourceLocation icon, List<Component> tooltip) { this(label, icon, tooltip, null); }
    }
    @FunctionalInterface interface Renderer { void draw(GuiGraphics graphics, int x, int y, int width); }

    static List<RecipeDetails> providers() { return Providers.ALL; }
    static List<Row> rows(MachineRecipeDisplay display) {
        var rows = new ArrayList<Row>();
        providers().forEach(provider -> provider.append(display, rows));
        return List.copyOf(rows);
    }
    final class Providers {
        private Providers() {}
        private static final List<RecipeDetails> ALL = ServiceLoader.load(RecipeDetails.class, RecipeDetails.class.getClassLoader())
                .stream().map(ServiceLoader.Provider::get).sorted(Comparator.comparingInt(RecipeDetails::order)).toList();
    }
}
