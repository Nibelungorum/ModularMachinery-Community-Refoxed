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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JeiStructurePreviewWidgetTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Items.WATER_BUCKET.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void widgetTranslatesLocalPreviewInputAndForwardsReleaseOnlyAfterAnInsidePress() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

        assertThat(widget.getPosition()).isEqualTo(new ScreenPosition(4, 64));
        assertThat(widget.handleInput(1, 1, leftPress(true))).isTrue();
        assertThat(preview.presses).isZero();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(preview.presses).isEqualTo(1);
        assertThat(preview.lastPressX).isEqualTo(1);
        assertThat(preview.lastPressY).isEqualTo(1);
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(preview.releases).isEqualTo(1);
        assertThat(widget.handleInput(161, 1, leftPress(true))).isFalse();
    }

    @Test
    void controlsConsumeClicksBeforeForwardingThemToTheCamera() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

        assertThat(widget.handleInput(1, 94, leftPress(true))).isTrue();
        assertThat(preview.previous).isZero();
        assertThat(widget.handleInput(1, 94, leftPress(false))).isTrue();

        assertThat(preview.previous).isEqualTo(1);
        assertThat(preview.presses).isZero();
    }

    @Test
    void actualPreviewPressForwardsLocalDragAndReleaseEndsTheSession() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

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
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

        assertThat(widget.handleInput(1, 1, leftPress(true))).isTrue();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(161, 1, leftPress(false))).isFalse();

        assertThat(widget.handleInput(161, 1, leftPress(true))).isFalse();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(161, 1, leftPress(false))).isFalse();
        assertThat(preview.presses).isZero();
        assertThat(preview.releases).isZero();
    }

    @Test
    void outsideActualPressDoesNotArmPreviewDrag() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

        assertThat(widget.handleInput(161, 1, leftPress(false))).isFalse();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(preview.drags).isZero();
    }

    @Test
    void previewReleaseOverControlsClearsSessionWithoutRunningTheControl() {
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

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
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 4, 64, 160, 92);

        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleInput(161, 1, leftPress(false))).isFalse();
        assertThat(widget.handleMouseDragged(2, 2, InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();
        assertThat(widget.handleInput(1, 1, leftPress(false))).isTrue();

        assertThat(preview.presses).isEqualTo(2);
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void tooltipUsesHoveredBlockBeforeSelectionAndIncludesFluidBucketName() {
        BlockHitResult hovered = hitAt(0, 0, 0);
        BlockHitResult selected = hitAt(1, 0, 0);
        RecordingPreviewWidget preview = new RecordingPreviewWidget();
        preview.hover = hovered;
        preview.selected = selected;
        RecordingTooltip tooltip = new RecordingTooltip();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, schema(Blocks.IRON_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState()), 4, 64, 160, 92);

        widget.getTooltip(tooltip, 6, 6);
        assertThat(tooltip.text()).contains(Blocks.IRON_BLOCK.getName());
        assertThat(tooltip.text()).doesNotContain(Blocks.GOLD_BLOCK.getName());

        preview.hover = null;
        tooltip.clear();
        widget.getTooltip(tooltip, 6, 6);
        assertThat(tooltip.text()).contains(Blocks.GOLD_BLOCK.getName());

        preview.selected = hitAt(0, 0, 0);
        JeiStructurePreviewWidget fluidWidget = JeiStructurePreviewWidget.forTesting(preview, schema(Blocks.WATER.defaultBlockState()), 4, 64, 160, 92);
        tooltip.clear();
        fluidWidget.getTooltip(tooltip, 6, 6);
        assertThat(tooltip.text()).contains(Items.WATER_BUCKET.getDefaultInstance().getHoverName());
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
        first.handleInput(1, 1, leftPress(false));

        assertThat(previews).hasSize(2);
        assertThat(previews.get(0)).isNotSameAs(previews.get(1));
        assertThat(previews.get(0).presses).isEqualTo(1);
        assertThat(previews.get(1).presses).isZero();
        assertThat(second).isNotSameAs(first);
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

    private static StructurePreviewSchema schema(net.minecraft.world.level.block.state.BlockState... states) {
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new LinkedHashMap<>();
        for (int index = 0; index < states.length; index++) blocks.put(new BlockPos(index, 0, 0), states[index]);
        return new StructurePreviewSchema(MMCR.id("jei_widget_test"), blocks, Map.of());
    }

    private static BlockHitResult hitAt(int x, int y, int z) {
        return new BlockHitResult(new net.minecraft.world.phys.Vec3(x, y, z), Direction.UP, new BlockPos(x, y, z), false);
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
            public boolean is(net.minecraft.client.KeyMapping keyMapping) {
                return false;
            }

            @Override
            public boolean isSimulate() {
                return simulate;
            }

            @Override
            public net.minecraft.client.input.InputWithModifiers getInputWithModifiers() {
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
        @Override public void previous() { previous++; }
        @Override public Object hoverHit() { return hover; }
        @Override public Object selectedHit() { return selected; }
        @Override public void close() { closes++; }
    }

    private static final class RecordingTooltip implements ITooltipBuilder {
        private final List<FormattedText> text = new ArrayList<>();

        @Override public void add(FormattedText component) { text.add(component); }
        @Override public void addAll(Collection<? extends FormattedText> components) { text.addAll(components); }
        @Override public void add(TooltipComponent component) { }
        @Override public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) { }
        @Override public void setIngredient(ITypedIngredient<?> typedIngredient) { }
        @Override public void clearIngredient() { }
        @Override public List<Either<FormattedText, TooltipComponent>> getLines() { return List.of(); }
        private List<FormattedText> text() { return text; }
        @Override public void clear() { text.clear(); }
    }
}
