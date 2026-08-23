package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.PreviewFrameViewport;
import cn.howxu.mmcr.client.preview.PreviewViewport;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import static org.assertj.core.api.Assertions.assertThat;

class JeiStructurePreviewWidgetTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Items.WATER_BUCKET.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void compileStatusCyclesOneToThreeDotsEverySecond() {
        assertThat(JeiStructurePreviewWidget.compileStatus(0)).isEqualTo("Render compile.");
        assertThat(JeiStructurePreviewWidget.compileStatus(1_000)).isEqualTo("Render compile..");
        assertThat(JeiStructurePreviewWidget.compileStatus(2_000)).isEqualTo("Render compile...");
        assertThat(JeiStructurePreviewWidget.compileStatus(3_000)).isEqualTo("Render compile.");
    }

    @Test
    void widgetTranslatesLocalPreviewInputAndForwardsReleaseOnlyAfterAnInsidePress() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.getPosition()).isEqualTo(new ScreenPosition(0, 0));
        assertThat(widget.handleInput(1, 1, leftPress(true))).isTrue();
        assertThat(preview.presses).isZero();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(preview.presses).isEqualTo(1);
        assertThat(preview.lastPressX).isEqualTo(1);
        assertThat(preview.lastPressY).isEqualTo(1);
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(preview.releases).isEqualTo(1);
        assertThat(widget.handleInput(169, 1, leftPress(true))).isFalse();
    }

    @Test
    void widgetDeclaresItsConfiguredJeiPositionAndInputArea() {
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(new RecordingPreviewWidget(), 4, 20, 160, 92);

        assertThat(widget.getPosition()).isEqualTo(new ScreenPosition(4, 20));
        assertThat(widget.getArea().position()).isEqualTo(new ScreenPosition(4, 20));
    }

    @Test
    void controlsConsumeClicksBeforeForwardingThemToTheCamera() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(1, 106, leftPress(true))).isTrue();
        assertThat(preview.previous).isZero();
        assertThat(widget.handleInput(1, 106, leftPress(false))).isTrue();

        assertThat(preview.previous).isEqualTo(1);
        assertThat(preview.presses).isZero();

        assertThat(widget.handleInput(17, 106, leftPress(false))).isFalse();
    }

    @Test
    void actualPreviewPressForwardsLocalDragAndReleaseEndsTheSession() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleMouseDragged(16, 6, InputConstants.Type.MOUSE.getOrCreate(2), 3, 4)).isTrue();
        assertThat(preview.drags).isEqualTo(1);
        assertThat(preview.lastDragX).isEqualTo(16);
        assertThat(preview.lastDragY).isEqualTo(6);
        assertThat(preview.lastDragButton).isEqualTo(2);
        assertThat(preview.lastDeltaX).isEqualTo(3);
        assertThat(preview.lastDeltaY).isEqualTo(4);

        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleMouseDragged(16, 6, InputConstants.Type.MOUSE.getOrCreate(2), 3, 4)).isFalse();
    }

    @Test
    void simulatedPreviewProbesDoNotArmDragOrReleaseHandling() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(1, 1, leftPress(true))).isTrue();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(169, 1, leftPress(false))).isFalse();

        assertThat(widget.handleInput(169, 1, leftPress(true))).isFalse();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(169, 1, leftPress(false))).isFalse();
        assertThat(preview.presses).isZero();
        assertThat(preview.releases).isZero();
    }

    @Test
    void outsideActualPressDoesNotArmPreviewDrag() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(169, 1, leftPress(false))).isFalse();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(preview.drags).isZero();
    }

    @Test
    void camera_input_is_limited_to_the_rendered_preview_rectangle() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 20, 160, 204);

        assertThat(widget.handleInput(-1, 0, leftPress(false))).isFalse();
        assertThat(widget.handleInput(0, 0, leftPress(false))).isTrue();
        assertThat(preview.lastPressX).isZero();
        assertThat(preview.lastPressY).isZero();
        assertThat(widget.handleMouseDragged(159, 203, InputConstants.Type.MOUSE.getOrCreate(0), 3, 4)).isTrue();
        assertThat(preview.lastDragX).isEqualTo(159);
        assertThat(preview.lastDragY).isEqualTo(203);
        assertThat(widget.handleMouseScrolled(-1, 0, 0, 1)).isFalse();
        assertThat(widget.handleMouseScrolled(0, 0, 0, 1)).isTrue();
        assertThat(widget.handleMouseDragged(163, 219, InputConstants.Type.MOUSE.getOrCreate(0), 3, 4)).isFalse();
        assertThat(widget.handleInput(163, 219, leftPress(false))).isFalse();

        assertThat(preview.presses).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(1);
        assertThat(preview.releases).isZero();
        assertThat(preview.scrolls).isEqualTo(1);
    }

    @Test
    void controls_take_priority_over_expanded_camera_input_area() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 204);

        assertThat(widget.handleInput(1, 218, leftPress(false))).isTrue();

        assertThat(preview.previous).isEqualTo(1);
        assertThat(preview.presses).isZero();
    }

    @Test
    void inputAreaIncludesControlsBelowTheTallestPreview() {
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(new RecordingPreviewWidget(), 2, 4, 164, 265);

        assertThat(widget.getArea().height()).isGreaterThanOrEqualTo(319);
    }

    @Test
    void previewReleaseOverControlsClearsSessionWithoutRunningTheControl() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleInput(1, 94, leftPress(false))).isFalse();
        assertThat(preview.previous).isZero();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();

        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(preview.presses).isEqualTo(2);
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void previewReleaseOutsideClearsSessionBeforeTheNextPreviewClick() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleInput(169, 1, leftPress(false))).isFalse();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();

        assertThat(preview.presses).isEqualTo(2);
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void structurePreviewExposesControlAndSelectedCandidateTooltips() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        preview.selected = hitAt(0, 0, 0);
        RecordingTooltip tooltip = new RecordingTooltip();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview,
                schemaWithCandidate(Blocks.IRON_BLOCK.defaultBlockState()), 0, 0, 160, 92);

        tooltip.clear();
        widget.getTooltip(tooltip, 6, 125);
        assertThat(tooltip.text().getFirst().getString())
                .isEqualTo(new ItemStack(Items.IRON_INGOT).getHoverName().getString());
        assertThat(tooltip.ingredient().getItem()).isEqualTo(Items.IRON_INGOT);
    }

    @Test
    void structurePreviewMarksModifierCandidatesInTheirTooltip() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        preview.selected = hitAt(0, 0, 0);
        RecordingTooltip tooltip = new RecordingTooltip();
        BlockPos position = BlockPos.ZERO;
        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("modifier_tooltip"),
                Map.of(position, Blocks.GOLD_BLOCK.defaultBlockState()), Map.of(), Map.of(position,
                List.of(new StructurePreviewSchema.Candidate(new ItemStack(Blocks.GOLD_BLOCK), true))), true);
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, schema, 0, 0, 160, 92);

        widget.getTooltip(tooltip, 6, 125);

        assertThat(tooltip.text()).extracting(FormattedText::getString).containsExactly(
                new ItemStack(Blocks.GOLD_BLOCK).getHoverName().getString(), "jei.mmcr.structure_preview.modifier");
    }

    @Test
    void structurePreviewShowsTheFixedBlockWhenItHasNoAlternatives() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        preview.selected = hitAt(0, 0, 0);
        RecordingTooltip tooltip = new RecordingTooltip();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview,
                schema(Blocks.IRON_BLOCK.defaultBlockState()), 0, 0, 160, 92);

        widget.getTooltip(tooltip, 6, 125);

        assertThat(tooltip.text().getFirst().getString())
                .isEqualTo(new ItemStack(Blocks.IRON_BLOCK).getHoverName().getString());
    }

    @Test
    void structurePreviewShowsCandidatesForHoveredBlockWhenNothingIsSelected() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        preview.hover = hitAt(0, 0, 0);
        RecordingTooltip tooltip = new RecordingTooltip();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview,
                schemaWithCandidate(Blocks.IRON_BLOCK.defaultBlockState()), 0, 0, 160, 92);

        widget.getTooltip(tooltip, 6, 125);

        assertThat(tooltip.text().getFirst().getString())
                .isEqualTo(new ItemStack(Items.IRON_INGOT).getHoverName().getString());
    }

    @Test
    void displayedCandidatesAreResolvableRenderableItemStacks() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        preview.hover = hitAt(0, 0, 0);
        StructurePreviewSchema schema = schemaWithCandidate(Blocks.IRON_BLOCK.defaultBlockState());
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, schema, 0, 0, 160, 92);

        java.util.List<StructurePreviewSchema.Candidate> candidates = schema.previewCandidatesAt(BlockPos.ZERO);

        assertThat(candidates).as("expected at least one candidate for IRON_BLOCK block").isNotEmpty();
        candidates.forEach(candidate -> {
            assertThat(candidate.stack().isEmpty())
                    .as("candidate stack must not be empty so the JEI carousel can render it")
                    .isFalse();
            assertThat(candidate.stack().typeHolder().unwrapKey())
                    .as("candidate stack must resolve to a registry holder so JEI can render and label it")
                    .isPresent();
        });
    }

    @Test
    void factoryBuiltCandidatesAreResolvableRenderableItemStacks() {
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        DynamicMachine machine = new DynamicMachine(MMCR.id("widget_factory_candidates"), "machine.widget.factory.candidates", pattern);
        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(machine);

        java.util.List<StructurePreviewSchema.Candidate> candidates = schema.previewCandidatesAt(BlockPos.ZERO);

        assertThat(candidates).as("factory must produce candidates for IRON_BLOCK block").isNotEmpty();
        candidates.forEach(candidate -> {
            assertThat(candidate.stack().isEmpty())
                    .as("factory candidate stack must not be empty so the JEI carousel can render it")
                    .isFalse();
            assertThat(candidate.stack().typeHolder().unwrapKey())
                    .as("factory candidate stack must resolve to a registry holder so JEI can render and label it")
                    .isPresent();
        });
    }

    @Test
    void factoryCreatesIndependentPreviewStateForEachRecipeWidget() {
        List<RecordingPreviewWidget> previews = new ArrayList<>();
        JeiStructurePreviewWidget.PreviewFactory factory = ignored -> {
            RecordingPreviewWidget preview = new RecordingPreviewWidget();
            previews.add(preview);
            return preview;
        };

        JeiStructurePreviewWidget first = JeiStructurePreviewWidget.forTesting(schema(Blocks.IRON_BLOCK.defaultBlockState()), factory, 4, 64, 160, 92);
        JeiStructurePreviewWidget second = JeiStructurePreviewWidget.forTesting(schema(Blocks.IRON_BLOCK.defaultBlockState()), factory, 4, 64, 160, 92);
        first.handleInput(4, 64, leftPress(false));

        assertThat(previews).hasSize(2);
        assertThat(previews.get(0)).isNotSameAs(previews.get(1));
        assertThat(previews.get(0).presses).isEqualTo(1);
        assertThat(previews.get(1).presses).isZero();
        assertThat(second).isNotSameAs(first);
    }

    @Test
    void multiStagePreviewCyclesTheLevelControlAndClosesThePreviousPreview() {
        List<RecordingPreviewWidget> previews = new ArrayList<>();
        List<StructurePreviewSchema> schemas = List.of(
                schema(Blocks.IRON_BLOCK.defaultBlockState()),
                schema(Blocks.GOLD_BLOCK.defaultBlockState()));
        List<StructurePreviewSchema> previewSchemas = new ArrayList<>();
        JeiStructurePreviewWidget.PreviewFactory factory = previewSchema -> {
            previewSchemas.add(previewSchema);
            RecordingPreviewWidget preview = new RecordingPreviewWidget();
            previews.add(preview);
            return preview;
        };
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(schemas, factory, 0, 0, 160, 92);
        RecordingTooltip tooltip = new RecordingTooltip();

        assertThat(previewSchemas).containsExactly(schemas.get(0));
        assertThat(widget.handleInput(77, 106, leftPress(true))).isTrue();
        assertThat(previews).hasSize(1);
        assertThat(previews.getFirst().closes).isZero();
        assertThat(previewSchemas).containsExactly(schemas.get(0));

        assertThat(widget.handleInput(77, 106, leftPress(false))).isTrue();
        assertThat(previews).hasSize(2);
        assertThat(previews.getFirst().closes).isEqualTo(1);
        assertThat(previewSchemas).containsExactly(schemas.get(0), schemas.get(1));

        assertThat(widget.handleInput(77, 106, leftPress(false))).isTrue();
        assertThat(previews).hasSize(3);
        assertThat(previews.get(1).closes).isEqualTo(1);
        assertThat(previewSchemas).containsExactly(schemas.get(0), schemas.get(1), schemas.get(0));
    }

    @Test
    void singleStagePreviewDoesNotConsumeTheLevelControl() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(List.of(schema(Blocks.IRON_BLOCK.defaultBlockState())),
                ignored -> preview, 0, 0, 160, 92);

        assertThat(widget.handleInput(77, 106, leftPress(false))).isFalse();
    }

    @Test
    void renderUsesJeiLayoutOriginForTheAbsolutePreviewViewport() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

        ScreenPosition origin = JeiStructurePreviewWidget.absoluteGuiOrigin(304, 238, 40, 18);

        assertThat(origin).isEqualTo(new ScreenPosition(264, 220));
    }

    @Test
    void absoluteJeiViewportKeepsExtractorMouseInsideDepthMapping() {
        PreviewFrameViewport frame = new PreviewFrameViewport(new PreviewViewport(268, 284, 160, 92),
                new PreviewViewport.FramebufferViewport(536, 192, 320, 184), 320, 184, 2);

        assertThat(frame.containsAbsoluteGui(304, 302)).isTrue();
        assertThat(frame.depthTexturePixel(304, 302)).isEqualTo(new PreviewFrameViewport.Pixel(72, 146));
    }

    @Test
    void layoutDisposalClosesEachPreviewOnlyOnce() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);
        JeiPreviewLifecycle lifecycle = new JeiPreviewLifecycle();

        lifecycle.register(widget);
        lifecycle.closeAll();
        lifecycle.closeAll();
        widget.close();

        assertThat(preview.closes).isEqualTo(1);
    }

    private static StructurePreviewSchema schema(BlockState... states) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int index = 0; index < states.length; index++) blocks.put(new BlockPos(index, 0, 0), states[index]);
        return new StructurePreviewSchema(MMCR.id("jei_widget_test"), blocks, Map.of());
    }

    private static StructurePreviewSchema schemaWithCandidate(BlockState state) {
        BlockPos position = BlockPos.ZERO;
        return new StructurePreviewSchema(MMCR.id("jei_widget_test"), Map.of(position, state), Map.of(),
                Map.of(position, List.of(new ItemStack(Items.IRON_INGOT))));
    }

    private static BlockHitResult hitAt(int x, int y, int z) {
        return new BlockHitResult(new Vec3(x, y, z), Direction.UP, new BlockPos(x, y, z), false);
    }

    private static IJeiUserInput leftPress(boolean simulate) {
        return new IJeiUserInput() {
            @Override
            public InputConstants.Key getKey() {
                return InputConstants.Type.MOUSE.getOrCreate(0);
            }

            @Override
            public int getModifiers() {
                return 0;
            }

            @Override
            public boolean is(mezz.jei.api.runtime.IJeiKeyMapping keyMapping) {
                return false;
            }

            @Override
            public boolean is(KeyMapping keyMapping) {
                return false;
            }

            @Override
            public boolean isSimulate() {
                return simulate;
            }

            @Override
            public InputWithModifiers getInputWithModifiers() {
                return null;
            }
        };
    }

    private static final class RecordingPreviewWidget implements JeiStructurePreviewWidget.Preview {
        private int presses;
        private int drags;
        private int releases;
        private int previous;
        private double lastPressX;
        private double lastPressY;
        private double lastDragX;
        private double lastDragY;
        private int lastDragButton;
        private double lastDeltaX;
        private double lastDeltaY;
        private Object hover;
        private Object selected;
        private int closes;
        private int scrolls;

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            presses++;
            lastPressX = mouseX;
            lastPressY = mouseY;
            return true;
        }

        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { releases++; return true; }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            drags++;
            lastDragX = mouseX;
            lastDragY = mouseY;
            lastDragButton = button;
            lastDeltaX = dragX;
            lastDeltaY = dragY;
            return true;
        }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { scrolls++; return true; }
        @Override public void previous() { previous++; }
        @Override public Object hoverHit() { return hover; }
        @Override public Object selectedHit() { return selected; }
        @Override public void close() { closes++; }
    }

    private static final class RecordingTooltip implements ITooltipBuilder {
        private final List<FormattedText> text = new ArrayList<>();
        private ITypedIngredient<?> ingredient;

        @Override public void add(FormattedText component) { text.add(component); }
        @Override public void addAll(Collection<? extends FormattedText> components) { text.addAll(components); }
        @Override public void add(TooltipComponent component) { }
        @Override public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) { }
        @Override public void setIngredient(ITypedIngredient<?> typedIngredient) { ingredient = typedIngredient; }
        @Override public void clearIngredient() { }
        @Override public List<Either<FormattedText, TooltipComponent>> getLines() { return List.of(); }
        private List<FormattedText> text() { return text; }
        private ItemStack ingredient() { return (ItemStack) ingredient.getIngredient(); }
        @Override public void clear() { text.clear(); }
    }
}
