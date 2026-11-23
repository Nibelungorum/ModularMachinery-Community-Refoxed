package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.MouseUserInput;
import mezz.jei.library.focus.FocusGroup;
import mezz.jei.library.gui.ingredients.CycleTicker;
import mezz.jei.library.gui.recipes.layout.builder.RecipeSlotBuilder;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies manual paging and JEI slot selection in the structure material row.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureMaterialWidgetTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @ParameterizedTest
    @CsvSource({"1,4", "2,4", "3,4", "4,4", "3,0"})
    void jeiBorderDoesNotStretchPreviewTexelsAwayFromMaterialSlots(int scale, int padding) throws IOException {
        var texture = ImageIO.read(getClass().getResource("/assets/mmcr/textures/gui/jei/preview/" + scale + "x.png"));
        var content = new ScreenRectangle(20, 30, StructurePreviewLayout.WIDTH, StructurePreviewLayout.heightForScale(scale));
        int x = content.left() - padding, y = content.top() - padding;
        int width = content.width() + 2 * padding, height = content.height() + 2 * padding;
        var painted = JeiRecipeBackground.PREVIEW.textureArea(scale, x, y, width, height);
        assertThat(painted.width()).isEqualTo(texture.getWidth());
        assertThat(painted.height()).isEqualTo(texture.getHeight());
        var viewport = StructurePreviewLayout.viewport(content.height());
        var widget = new StructureMaterialWidget(List::of, StructurePreviewLayout.materialOrigin(viewport));
        assertThat(new ScreenPosition(painted.left() + 3, painted.bottom() - 23))
                .isEqualTo(new ScreenPosition(content.left() + widget.getPosition().x(), content.top() + widget.getPosition().y()));
        assertThat(JeiRecipeBackground.INSTANCE.textureArea(scale, x, y, width, height))
                .isEqualTo(new ScreenRectangle(x, y, width, height));
    }

    @Test
    void pagesOnLeftReleaseAndKeepsOnlyTheCurrentMaterialsUnderTheMouse() {
        var slots = slots(10);
        var origin = new ScreenPosition(3, 197);
        var widget = new StructureMaterialWidget(() -> slots, origin);
        assertThat(widget.getSlotUnderMouse(0, 0)).isEmpty();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(slots.getFirst(), origin));
        assertThat(widget.getSlotUnderMouse(16, 1)).isEmpty();
        assertThat(widget.getSlotUnderMouse(17, 1)).contains(new RecipeSlotUnderMouse(slots.get(1), origin));
        assertThat(widget.getSlotUnderMouse(151.9, 16.9)).contains(new RecipeSlotUnderMouse(slots.get(8), origin));
        assertThat(widget.getSlotUnderMouse(151.9, 17)).isEmpty();
        assertThat(widget.getSlotUnderMouse(152, 1)).isEmpty();
        assertThat(widget.getArea().containsPoint(origin.x() + 160, origin.y() + 16)).isTrue();

        assertThat(widget.handleInput(153, 1, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(160.9, 16.9, mouse(0, InputPhase.PRESS))).isTrue();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(slots.getFirst(), origin));
        assertThat(widget.handleInput(160.9, 16.9, mouse(0, InputPhase.RELEASE))).isTrue();

        slots.forEach(IRecipeSlotDrawable::clearDisplayOverrides);
        widget.tick();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(slots.getLast(), origin));
        assertThat(widget.getSlotUnderMouse(17, 1)).isEmpty();
        assertThat(widget.getSlotUnderMouse(136, 1)).isEmpty();
        var anotherPreview = new StructureMaterialWidget(() -> slots, origin);
        assertThat(anotherPreview.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(slots.getFirst(), origin));
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isFalse();
        assertThat(widget.handleInput(153, 1, mouse(0, InputPhase.PRESS))).isTrue();
        assertThat(widget.handleInput(160, 8.9, mouse(0, InputPhase.RELEASE))).isTrue();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(slots.getFirst(), origin));
    }

    @Test
    void changingStageResetsThePageAndDiscardsAnUnreleasedPageTurn() {
        var first = slots(19);
        var second = slots(10);
        var selected = new AtomicReference<>(first);
        var origin = new ScreenPosition(3, 197);
        var widget = new StructureMaterialWidget(selected::get, origin);
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.PRESS))).isTrue();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isTrue();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(first.get(9), origin));
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.PRESS))).isTrue();

        selected.set(second);
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isFalse();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(second.getFirst(), origin));
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.PRESS))).isTrue();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isTrue();
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(second.getLast(), origin));
        assertThat(widget.getSlotUnderMouse(17, 1)).isEmpty();

        selected.set(first);
        assertThat(widget.getSlotUnderMouse(0, 1)).contains(new RecipeSlotUnderMouse(first.getFirst(), origin));
    }

    @Test
    void gapsOtherButtonsAndReleasingOverTheOppositeArrowDoNotTurnThePage() {
        var slots = slots(19);
        var widget = new StructureMaterialWidget(() -> slots, new ScreenPosition(3, 197));
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.PRESS))).isTrue();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isTrue();

        assertThat(widget.handleInput(153, 0.9, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(152.9, 4, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(161, 4, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(160, 17, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(153, 9, mouse(1, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isFalse();
        assertThat(widget.handleInput(153, 8.9, mouse(0, InputPhase.PRESS))).isTrue();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.RELEASE))).isFalse();
        assertThat(widget.getSlotUnderMouse(0, 1)).map(RecipeSlotUnderMouse::slot).contains(slots.get(9));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 9})
    void neitherArrowAcceptsInputWithoutASecondPage(int count) {
        var slots = slots(count);
        var widget = new StructureMaterialWidget(() -> slots, new ScreenPosition(3, 197));
        assertThat(widget.handleInput(153, 1, mouse(0, InputPhase.PRESS))).isFalse();
        assertThat(widget.handleInput(153, 9, mouse(0, InputPhase.PRESS))).isFalse();
    }

    private static List<IRecipeSlotDrawable> slots(int count) {
        var manager = JeiTestIngredients.manager();
        var cycle = CycleTicker.createWithRandomOffset();
        List<IRecipeSlotDrawable> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var builder = new RecipeSlotBuilder(manager, ContextMap.EMPTY, i, RecipeIngredientRole.RENDER_ONLY);
            builder.add(Items.STONE);
            slots.add(builder.build(FocusGroup.EMPTY, cycle).second());
        }
        return slots;
    }

    private static MouseUserInput mouse(int button, InputPhase phase) {
        return new MouseUserInput(new MouseButtonEvent(0, 0, new MouseButtonInfo(button, 0)), false, phase);
    }
}
