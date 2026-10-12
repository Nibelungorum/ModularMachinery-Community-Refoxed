package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.client.recipe.RecipeMetadataRow;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real layout and widget state, without constructing Minecraft/font/GPU resources.
 * @author howxu <dev@howxu.cn>
 */
class EmiRecipeViewportTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void hiddenSlotsExposeNeitherBoundsIngredientTooltipNorRecipeContext() {
        var recipe = MachineEmiRecipeTest.recipe("mmcr:slot_pages", List.of(
                new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 17)),
                List.of(new ItemStack(Items.DIAMOND, 4)));
        var widget = new MachineRecipeEmiWidget(recipe, 168, 80, row -> 10);
        var input = widget.slots().getFirst();
        var output = widget.slots().getLast();
        var oldBounds = input.getBounds();
        assertThat(input.getStack().getEmiStacks().getFirst().getKey()).isSameAs(Items.IRON_INGOT);
        assertThat(input.quantityText()).isEqualTo("17");
        assertThat(input.tooltipText()).contains(Component.translatable("jei.mmcr.machine_recipe.item_count", "17"));
        assertThat(output.getRecipe()).isSameAs(recipe);

        assertThat(widget.changePage(1)).isTrue();
        assertThat(input.getBounds()).isEqualTo(Bounds.EMPTY);
        assertThat(input.getStack().isEmpty()).isTrue();
        assertThat(input.tooltipText()).isEmpty();
        assertThat(input.getTooltip(oldBounds.x(), oldBounds.y())).isEmpty();
        assertThat(input.mouseClicked(oldBounds.x(), oldBounds.y(), 0)).isFalse();
        assertThat(input.keyPressed(0, 0, 0)).isFalse();
        assertThat(output.getRecipe()).isNull();

        widget.changePage(-1);
        assertThat(input.getBounds()).isEqualTo(oldBounds);
        assertThat(input.getStack().getAmount()).isEqualTo(17);
        assertThat(output.getRecipe()).isSameAs(recipe);
    }

    @Test void largeInputGridScrollsToFinalOutputAndOldLocationsStopMatching() {
        var inputs = IntStream.range(0, 80).mapToObj(i -> new MachineIngredient.ItemIngredient(
                Ingredient.of(Items.IRON_INGOT), i + 1)).toList();
        var recipe = MachineEmiRecipeTest.recipe("mmcr:grid_scroll", inputs, List.of(new ItemStack(Items.DIAMOND, 4)));
        var widget = new MachineRecipeEmiWidget(recipe, 168, 80, row -> 10);
        var first = widget.slots().getFirst();
        var last = widget.slots().getLast();
        assertThat(last.getStack().isEmpty()).isTrue();
        while (widget.scroll(1)) { }
        assertThat(first.getBounds()).isEqualTo(Bounds.EMPTY);
        assertThat(first.getStack().isEmpty()).isTrue();
        assertThat(last.getStack().getEmiStacks().getFirst().getKey()).isSameAs(Items.DIAMOND);
        assertThat(last.getRecipe()).isSameAs(recipe);
        assertThat(last.quantityText()).isEqualTo("4");
        assertThat(recipe.getInputs()).hasSize(80);
    }

    @Test void oversizedWrappedInformationCanReachItsLastLine() {
        var base = MachineEmiRecipeTest.recipe("mmcr:long_text", List.of(), List.of(new ItemStack(Items.DIAMOND)));
        var text = Component.literal("x".repeat(500));
        var recipe = new MachineEmiRecipe(base.getCategory(), base.data(), List.of(text));
        // Controlled width: ten characters per line, ten pixels per line.
        var widget = new MachineRecipeEmiWidget(recipe, 168, 80,
                row -> Math.max(1, (row.text().getString().length() + 9) / 10) * 10);
        while (widget.changePage(1)) { }
        while (widget.scroll(1)) { }
        var viewport = widget.viewport();
        assertThat(widget.rowAt(viewport.x() + 1, viewport.y() + viewport.height() - 1))
                .isEqualTo(recipe.details().size() - 1);
        assertThat(widget.rowAt(viewport.x() - 1, viewport.y())).isEqualTo(-1);
        assertThat(widget.tooltipRows(viewport.x() + 1, viewport.y() + viewport.height() - 1))
                .extracting(RecipeMetadataRow::text).containsExactly(text);
        assertThat(widget.mouseScrolled(-1, -1, 0, -1)).isFalse();
        assertThat(widget.changePage(1)).isFalse();
    }
}
