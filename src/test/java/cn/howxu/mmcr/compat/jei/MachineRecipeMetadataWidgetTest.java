package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual tooltip reachability while scrolling oversized pages and returning from the last page.
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeMetadataWidgetTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        PneumaticRecipeTypes.register();
    }

    @Test
    void oversizedContentScrollsToTheLastRowAndClipsTooltipInput() {
        var display = display(6);
        var base = MachineRecipeLayout.forDisplay(display, 4);
        int startY = base.durationTextY();
        var layout = withPages(base, List.of(new MachineRecipeLayout.MetadataPage(startY, startY + 70)), 26);
        var widget = new MachineRecipeMetadataWidget(CombinedAirManaRecipeLayoutTest.category(), display, layout, List.of());
        var lines = new ArrayList<Component>();
        var tooltip = CombinedAirManaRecipeLayoutTest.tooltip(lines);

        for (int scroll = 0; scroll < 6; scroll++) assertThat(widget.mouseScrolled(20, 5, 0, -1)).isTrue();
        widget.getTooltip(tooltip, 20, 3);
        assertThat(lines).containsExactlyElementsOf(display.airInputs().getLast().tooltip(true));
        widget.getTooltip(tooltip, 20, 0);
        widget.getTooltip(tooltip, 20, 13);
        widget.getTooltip(tooltip, -1, 3);
        assertThat(lines).containsExactlyElementsOf(display.airInputs().getLast().tooltip(true));
        assertThat(widget.getSlotUnderMouse(20, 13)).isEmpty();
        assertThat(widget.mouseScrolled(20, 26, 0, -1)).isFalse();

        for (int scroll = 0; scroll < 6; scroll++) widget.mouseScrolled(20, 5, 0, 1);
        lines.clear();
        widget.getTooltip(tooltip, 20, 11);
        assertThat(lines).containsExactlyElementsOf(display.airInputs().getFirst().tooltip(true));
    }

    @Test
    void scrollingBeyondLastPageThenBackReachesThePreviousPage() {
        var display = display(3);
        var base = MachineRecipeLayout.forDisplay(display, 4);
        int startY = base.durationTextY();
        var layout = withPages(base, List.of(
                new MachineRecipeLayout.MetadataPage(startY, startY + 20),
                new MachineRecipeLayout.MetadataPage(startY + 20, startY + 40)), 34);
        var widget = new MachineRecipeMetadataWidget(CombinedAirManaRecipeLayoutTest.category(), display, layout, List.of());
        var lines = new ArrayList<Component>();
        var tooltip = CombinedAirManaRecipeLayoutTest.tooltip(lines);

        widget.mouseScrolled(20, 5, 0, -1);
        widget.mouseScrolled(20, 5, 0, -1);
        widget.getTooltip(tooltip, 20, 11);
        assertThat(lines).containsExactlyElementsOf(display.airInputs().getLast().tooltip(true));

        lines.clear();
        widget.mouseScrolled(20, 5, 0, 1);
        widget.getTooltip(tooltip, 20, 11);
        assertThat(lines).containsExactlyElementsOf(display.airInputs().getFirst().tooltip(true));
        assertThat(widget.mouseClicked(90, layout.metadataViewportHeight(), 0)).isFalse();
    }

    private static MachineRecipeDisplay display(int airRows) {
        List<MachineRequirement> requirements = new ArrayList<>();
        for (int index = 0; index < airRows; index++) requirements.add(AirRequirement.input(100 + index, 4F, List.of()));
        return MachineRecipeDisplay.from(new MachineRecipe(MMCR.id("details_scroll"), MMCR.id("test_cube"), 20,
                requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of()));
    }

    private static MachineRecipeLayout withPages(MachineRecipeLayout base,
                                                List<MachineRecipeLayout.MetadataPage> pages, int viewportHeight) {
        return new MachineRecipeLayout(base.width(), base.height(), base.inputs(), base.outputs(), base.arrow(),
                base.sourceTextLines(), base.manaRows(), pages, base.metadataViewportY(), viewportHeight,
                base.durationTextX(), base.hostRequirementTextY(), base.durationTextY(),
                base.transferButtonX(), base.transferButtonY());
    }
}
