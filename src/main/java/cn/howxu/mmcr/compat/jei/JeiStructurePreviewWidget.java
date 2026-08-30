package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.client.preview.StructurePreviewRenderer;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import cn.howxu.mmcr.client.preview.StructurePreviewCompilation;
import cn.howxu.mmcr.client.preview.StructurePreviewCompilationCache;
import cn.howxu.mmcr.client.preview.StructurePreviewWidget;
import cn.howxu.mmcr.mixin.client.preview.GuiGraphicsExtractorAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * JEI adapter for one independently-owned structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiStructurePreviewWidget implements IRecipeWidget, IJeiInputHandler, AutoCloseable {
    private static final float CONTROL_SCALE = 0.9F;
    private static final int CONTROL_SIZE = Math.round(15 * CONTROL_SCALE);
    private static final int CONTROL_STEP = CONTROL_SIZE + 4;
    private static final int UI_X_OFFSET = -3;
    private static final int CONTROL_Y_OFFSET = -13;
    private static final float LAYER_TEXT_SCALE = 0.9F;
    private static final int LAYER_TEXT_Y_OFFSET = -24;
    private static final int CANDIDATE_SIZE = 16;
    private static final int CANDIDATE_STEP = 18;
    private static final int LAYOUT_WIDTH = 168;
    private Preview preview;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private StructurePreviewSchema schema;
    private final Machine machine;
    private final StructurePreviewCompilation compilation;
    private final List<MachineStructureStage> stages;
    private List<StructurePreviewSchema> stageSchemas;
    private PreviewFactory previewFactory;
    private int selectedStage;
    private final LongSupplier clock = System::currentTimeMillis;
    private long compileAnimationStart = -1L;
    private boolean previewDragActive;
    private boolean closed;

    public JeiStructurePreviewWidget(Machine machine, int x, int y, int width, int height) {
        this(machine, StructurePreviewCompilationCache.instance().acquire(machine), x, y, width, height);
    }

    public JeiStructurePreviewWidget(Machine machine, StructurePreviewSchema schema,
            int x, int y, int width, int height) {
        this.preview = createPreview(schema);
        this.schema = schema;
        this.machine = machine;
        this.compilation = null;
        this.stages = machine.structureStages();
        this.stageSchemas = List.of();
        this.previewFactory = this::createPreview;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private JeiStructurePreviewWidget(Machine machine, StructurePreviewCompilation compilation, int x, int y, int width, int height) {
        this.preview = null;
        this.schema = null;
        this.machine = machine;
        this.compilation = compilation;
        this.stages = machine.structureStages();
        this.stageSchemas = List.of();
        this.previewFactory = this::createPreview;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private static Preview adapt(StructurePreviewWidget widget) {
        return new Preview() {
            @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return widget.mouseClicked(mouseX, mouseY, button); }
            @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return widget.mouseReleased(mouseX, mouseY, button); }
            @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return widget.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
            @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { return widget.mouseScrolled(mouseX, mouseY, scrollDelta); }
            @Override public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int originX, int originY) {
                widget.render(graphics, x, y, width, height, 0, originX, originY);
            }
            @Override public void previous() { widget.selectPreviousLayer(); }
            @Override public void next() { widget.selectNextLayer(); }
            @Override public void all() { widget.showAllLayers(); }
            @Override public void reset() { widget.reset(); }
            @Override public int selectedLayer() { return widget.selectedLayer(); }
            @Override public Object hoverHit() { return widget.hoverHit(); }
            @Override public Object selectedHit() { return widget.selectedHit(); }
        };
    }

    private Preview createPreview(StructurePreviewSchema previewSchema) {
        return adapt(new StructurePreviewWidget(new StructurePreviewRenderer(previewSchema)));
    }

    private JeiStructurePreviewWidget(Preview preview, StructurePreviewSchema schema, int x, int y, int width, int height) {
        this.preview = preview;
        this.schema = schema;
        this.machine = null;
        this.compilation = null;
        this.stages = List.of();
        this.stageSchemas = List.of();
        this.previewFactory = null;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    static JeiStructurePreviewWidget forTesting(Preview preview, int x, int y, int width, int height) {
        return forTesting(preview, null, x, y, width, height);
    }

    static JeiStructurePreviewWidget forTesting(Preview preview, StructurePreviewSchema schema, int x, int y, int width, int height) {
        return new JeiStructurePreviewWidget(preview, schema, x, y, width, height);
    }

    static JeiStructurePreviewWidget forTesting(StructurePreviewSchema schema, PreviewFactory factory, int x, int y, int width, int height) {
        return new JeiStructurePreviewWidget(factory.create(schema), schema, x, y, width, height);
    }

    static JeiStructurePreviewWidget forTesting(List<StructurePreviewSchema> schemas, PreviewFactory factory, int x, int y, int width, int height) {
        JeiStructurePreviewWidget widget = new JeiStructurePreviewWidget(factory.create(schemas.getFirst()), schemas.getFirst(), x, y, width, height);
        widget.stageSchemas = List.copyOf(schemas);
        widget.previewFactory = factory;
        return widget;
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(x, y); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(x, y, LAYOUT_WIDTH, height + 54); }

    @Override
    public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        ensurePreviewStarted();
        if (preview == null) {
            if (compileAnimationStart < 0L) compileAnimationStart = clock.getAsLong();
            long elapsed = Math.max(0L, clock.getAsLong() - compileAnimationStart);
            graphics.text(Minecraft.getInstance().font, Component.literal(compileStatus(elapsed)),
                    0, height / 2, 0xFFFFFFFF, false);
            return;
        }
        GuiGraphicsExtractorAccessor extractor = (GuiGraphicsExtractorAccessor) graphics;
        ScreenPosition origin = absoluteGuiOrigin(extractor.mmcr$getMouseX(), extractor.mmcr$getMouseY(), mouseX, mouseY);
        preview.render(graphics, 0, 0, width, height, origin.x(), origin.y());
        graphics.nextStratum();
        String[] labels = hasMultipleStages() ? new String[]{"+", "-", "A", "R", "M"} : new String[]{"+", "-", "A", "R"};
        for (int index = 0; index < labels.length; index++) {
            int controlX = UI_X_OFFSET + index * CONTROL_STEP;
            int controlY = height + CONTROL_Y_OFFSET;
            graphics.fill(controlX, controlY, controlX + CONTROL_SIZE, controlY + CONTROL_SIZE, 0xFF808080);
            graphics.pose().pushMatrix();
            graphics.pose().translate(controlX + CONTROL_SIZE / 2.0F, controlY + CONTROL_SIZE / 2.0F);
            graphics.pose().scale(CONTROL_SCALE, CONTROL_SCALE);
            int labelWidth = Minecraft.getInstance().font.width(labels[index]);
            graphics.text(Minecraft.getInstance().font, Component.literal(labels[index]),
                    -labelWidth / 2, -Minecraft.getInstance().font.lineHeight / 2, 0xFFFFFFFF, false);
            graphics.pose().popMatrix();
        }
        int selectedLayer = preview.selectedLayer();
        List<Integer> layers = schema == null ? List.of() : schema.layers();
        Component layerText = selectedLayer < 0
                ? Component.translatable("jei.mmcr.structure_preview.all_layers")
                : Component.translatable("jei.mmcr.structure_preview.layer", selectedLayer, layers.indexOf(selectedLayer) + 1, layers.size());
        graphics.pose().pushMatrix();
        graphics.pose().scale(LAYER_TEXT_SCALE, LAYER_TEXT_SCALE);
        int layerTextY = (int) ((height + LAYER_TEXT_Y_OFFSET) / LAYER_TEXT_SCALE);
        int layerTextX = (int) (UI_X_OFFSET / LAYER_TEXT_SCALE);
        graphics.text(Minecraft.getInstance().font, layerText, layerTextX, layerTextY, 0xFFFFFFFF, false);
        if (stages.size() > 1) {
            int levelTextX = Minecraft.getInstance().font.width(layerText) + 4;
            graphics.text(Minecraft.getInstance().font, Component.literal("Level=" + stages.get(selectedStage).number()),
                    (int) ((UI_X_OFFSET + levelTextX) / LAYER_TEXT_SCALE), layerTextY, 0xFFFFFFFF, false);
        }
        graphics.pose().popMatrix();
        renderCandidates(graphics);
    }

    private void renderCandidates(GuiGraphicsExtractor graphics) {
        if (schema == null || !(preview.selectedHit() instanceof BlockHitResult hit)) return;
        List<ItemStack> candidates = displayedStacks(hit.getBlockPos());
        if (candidates.isEmpty()) return;
        int visibleCount = Math.min(maxVisibleCandidates(), candidates.size());
        int offset = (int) Math.floorDiv(clock.getAsLong(), 1000) % candidates.size();
        for (int index = 0; index < visibleCount; index++) {
            graphics.item(candidates.get((index + offset) % candidates.size()), 0, index * CANDIDATE_STEP, 0);
        }
    }

    private List<ItemStack> displayedStacks(BlockPos position) {
        return displayedCandidates(position).stream().map(StructurePreviewSchema.Candidate::stack).toList();
    }

    private List<StructurePreviewSchema.Candidate> displayedCandidates(BlockPos position) {
        List<StructurePreviewSchema.Candidate> candidates = schema.previewCandidatesAt(position);
        if (!candidates.isEmpty()) return candidates;
        BlockState state = schema.stateAt(position);
        if (state == null || state.getBlock().asItem() == Items.AIR) return List.of();
        return List.of(new StructurePreviewSchema.Candidate(new ItemStack(state.getBlock()), false));
    }

    private void ensurePreviewStarted() {
        if (compilation == null) return;
        compilation.start();
        StructurePreviewSchema completed = compilation.schema();
        if (completed != null && preview == null) {
            schema = completed;
            preview = createPreview(completed);
        }
    }

    static String compileStatus(long elapsedMillis) {
        int dotCount = (int) (Math.max(0L, elapsedMillis) / 1_000L % 3L) + 1;
        return "Render compile" + ".".repeat(dotCount);
    }

    private boolean hasMultipleStages() {
        return stages.size() > 1 || stageSchemas.size() > 1;
    }

    private void selectNextStage() {
        selectedStage = (selectedStage + 1) % (stages.isEmpty() ? stageSchemas.size() : stages.size());
        preview.close();
        schema = stages.isEmpty()
                ? stageSchemas.get(selectedStage)
                : new StructurePreviewSchemaFactory().create(stages.get(selectedStage), machine.registryName());
        preview = previewFactory.create(schema);
    }

    static ScreenPosition absoluteGuiOrigin(int absoluteMouseX, int absoluteMouseY, double localMouseX, double localMouseY) {
        return new ScreenPosition(absoluteMouseX - (int) localMouseX, absoluteMouseY - (int) localMouseY);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (preview != null) preview.close();
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE) return false;
        int button = input.getKey().getValue();
        if (button != 0) return false;
        if (preview == null) return false;
        if (!input.isSimulate() && previewDragActive) {
            previewDragActive = false;
            boolean inside = insidePreview(mouseX, mouseY);
            boolean handled = preview.mouseReleased(previewMouseX(mouseX), previewMouseY(mouseY), button);
            return inside || handled;
        }
        int control = controlAt(mouseX, mouseY);
        if (control >= 0) {
            if (input.isSimulate()) return true;
            switch (control) {
                case 0 -> preview.previous();
                case 1 -> preview.next();
                case 2 -> preview.all();
                case 3 -> preview.reset();
                case 4 -> selectNextStage();
                default -> { }
            }
            previewDragActive = false;
            return true;
        }
        if (candidateAt(mouseX, mouseY) >= 0) {
            previewDragActive = false;
            return true;
        }
        if (input.isSimulate()) {
            return insidePreview(mouseX, mouseY);
        }
        if (!insidePreview(mouseX, mouseY)) return false;
        boolean handled = preview.mouseClicked(previewMouseX(mouseX), previewMouseY(mouseY), button);
        previewDragActive = handled;
        return handled;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        return previewDragActive && controlAt(mouseX, mouseY) < 0 && candidateAt(mouseX, mouseY) < 0
                && insidePreview(mouseX, mouseY)
                && mouseKey.getType() == InputConstants.Type.MOUSE
                && preview.mouseDragged(previewMouseX(mouseX), previewMouseY(mouseY), mouseKey.getValue(), dragX, dragY);
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        return controlAt(mouseX, mouseY) < 0 && candidateAt(mouseX, mouseY) < 0 && insidePreview(mouseX, mouseY)
                && preview.mouseScrolled(previewMouseX(mouseX), previewMouseY(mouseY), scrollDeltaY);
    }

    private int candidateAt(double mouseX, double mouseY) {
        if (schema == null || !(preview.selectedHit() instanceof BlockHitResult hit)) return -1;
        int count = Math.min(maxVisibleCandidates(), displayedCandidates(hit.getBlockPos()).size());
        if (mouseX < 0 || mouseX >= CANDIDATE_SIZE || mouseY < 0) return -1;
        int index = (int) Math.floor(mouseY / CANDIDATE_STEP);
        return index < count && mouseY < index * CANDIDATE_STEP + CANDIDATE_SIZE ? index : -1;
    }

    private static int maxVisibleCandidates() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 12;
            case 2 -> 10;
            case 3 -> 8;
            default -> 4;
        };
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
    }
    private double previewMouseX(double mouseX) { return mouseX; }
    private double previewMouseY(double mouseY) { return mouseY; }
    private int controlAt(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseY < height + CONTROL_Y_OFFSET || mouseY >= height + CONTROL_Y_OFFSET + CONTROL_SIZE) return -1;
        int relativeX = (int) Math.floor(mouseX - UI_X_OFFSET);
        if (relativeX < 0) return -1;
        int column = relativeX / CONTROL_STEP;
        int controls = hasMultipleStages() ? 5 : 4;
        return column >= 0 && column < controls
                && mouseX < UI_X_OFFSET + column * CONTROL_STEP + CONTROL_SIZE ? column : -1;
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        int control = controlAt(mouseX, mouseY);
        if (control >= 0) {
            String[] keys = {"previous_layer", "next_layer", "all_layers", "reset", "next_level"};
            tooltip.add(Component.translatable("jei.mmcr.structure_preview." + keys[control]));
            return;
        }
        int candidateIndex = candidateAt(mouseX, mouseY);
        if (candidateIndex >= 0 && preview.selectedHit() instanceof BlockHitResult hit) {
            List<StructurePreviewSchema.Candidate> candidates = displayedCandidates(hit.getBlockPos());
            int offset = (int) Math.floorDiv(clock.getAsLong(), 1000) % candidates.size();
            StructurePreviewSchema.Candidate candidate = candidates.get((candidateIndex + offset) % candidates.size());
            ItemStack stack = candidate.stack();
            tooltip.add(stack.getHoverName());
            if (candidate.modifier()) tooltip.add(Component.translatable("jei.mmcr.structure_preview.modifier"));
            tooltip.setIngredient(new ItemStackIngredient(stack));
            return;
        }
    }

    private record ItemStackIngredient(ItemStack stack) implements ITypedIngredient<ItemStack> {
        @Override public mezz.jei.api.ingredients.IIngredientType<ItemStack> getType() { return VanillaTypes.ITEM_STACK; }
        @Override public ItemStack getIngredient() { return stack; }
    }

    interface Preview {
        boolean mouseClicked(double mouseX, double mouseY, int button);
        default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
        default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
        default boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { return false; }
        default void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int originX, int originY) { }
        default void close() { }
        default void previous() { }
        default void next() { }
        default void all() { }
        default void reset() { }
        default int selectedLayer() { return -1; }
        default Object hoverHit() { return null; }
        default Object selectedHit() { return null; }
    }

    interface PreviewFactory {
        Preview create(StructurePreviewSchema schema);
    }
}
