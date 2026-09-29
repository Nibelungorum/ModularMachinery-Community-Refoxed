package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary.Entry;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema.Candidate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * JEI-independent structure preview panel shared by preview hosts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewPanel implements AutoCloseable {
    public static final int VISIBLE_SLOT_COUNT = 8;

    private final Machine machine;
    private final List<MachineStructureStage> stages;
    private StructurePreviewCompilation compilation;
    private @Nullable StructurePreviewSchema schema;
    private @Nullable StructurePreviewWidget widget;
    private StructureMaterialSummary materials = StructureMaterialSummary.empty();
    private int stageIndex;
    private long compileAnimationStart = -1L;
    private boolean closed;

    public StructurePreviewPanel(Machine machine) {
        this(machine, 0);
    }

    public StructurePreviewPanel(Machine machine, int stageNumber) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.stages = List.copyOf(machine.structureStages());
        if (stages.isEmpty()) throw new IllegalArgumentException("machine structure stages empty");
        this.stageIndex = stageIndexFor(stageNumber, stages);
        this.compilation = acquireCompilation(stageIndex);
    }

    public void render(GuiGraphics graphics, int width, int height,
            float partialTick, int guiOriginX, int guiOriginY, int mouseX, int mouseY,
            int statusOriginX, int statusOriginY) {
        if (closed) return;
        ensurePreviewStarted();
        if (widget == null) {
            Component status;
            if (compilation.failure() != null) {
                status = Component.translatable("jei.mmcr.structure_preview.unavailable");
            } else {
                if (compileAnimationStart < 0L) compileAnimationStart = System.currentTimeMillis();
                long elapsed = Math.max(0L, System.currentTimeMillis() - compileAnimationStart);
                status = Component.literal(compileStatus(elapsed));
            }
            Minecraft minecraft = Minecraft.getInstance();
            graphics.enableScissor(statusOriginX, statusOriginY,
                    statusOriginX + width, statusOriginY + height);
            try {
                int x = statusOriginX + Math.max(0, (width - minecraft.font.width(status)) / 2);
                graphics.drawString(minecraft.font, status, x, statusOriginY + height / 2, 0xFFFFFFFF, false);
            } finally {
                graphics.disableScissor();
            }
            return;
        }

        widget.render(graphics, 0, 0, width, height, partialTick, guiOriginX, guiOriginY, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return widget != null && widget.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return widget != null && widget.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        return widget != null && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return widget != null && widget.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    public boolean isReady() {
        return widget != null;
    }

    public @Nullable StructurePreviewSchema schema() {
        return schema;
    }

    public int selectedLayer() {
        return widget == null ? -1 : widget.selectedLayer();
    }

    public @Nullable BlockPos selectedPosition() {
        return widget == null ? null : widget.selectedPosition();
    }

    public List<StructurePreviewSchema.Candidate> selectedCandidates() {
        if (schema == null || selectedPosition() == null) return List.of();
        BlockPos position = selectedPosition();
        List<StructurePreviewSchema.Candidate> candidates = schema.previewCandidatesAt(position);
        if (!candidates.isEmpty()) return candidates;
        BlockState state = schema.stateAt(position);
        if (state == null || state.getBlock().asItem() == Items.AIR) return List.of();
        return List.of(new StructurePreviewSchema.Candidate(new ItemStack(state.getBlock()), false));
    }

    public @Nullable Candidate candidateAt(int slot, long timeMillis) {
        return candidateAt(slot, timeMillis, VISIBLE_SLOT_COUNT);
    }

    public @Nullable Candidate candidateAt(int slot, long timeMillis, int visibleSlotCount) {
        List<StructurePreviewSchema.Candidate> candidates = selectedCandidates();
        int index = candidateIndex(slot, timeMillis, candidates.size(), visibleSlotCount);
        return index < 0 ? null : candidates.get(index);
    }

    public StructureMaterialSummary materials() {
        return materials;
    }

    public @Nullable Entry materialAt(int slot, long timeMillis) {
        return materialAt(slot, timeMillis, VISIBLE_SLOT_COUNT);
    }

    public @Nullable Entry materialAt(int slot, long timeMillis, int visibleSlotCount) {
        if (slot < 0 || slot >= visibleSlotCount || visibleSlotCount <= 0) return null;
        List<StructureMaterialSummary.Entry> entries = materials.entries();
        int first = materialPage(timeMillis, entries.size(), visibleSlotCount) * visibleSlotCount;
        int index = first + slot;
        return index < entries.size() ? entries.get(index) : null;
    }

    public boolean hasMultipleStages() {
        return stages.size() > 1;
    }

    public int stageNumber() {
        return stages.get(stageIndex).number();
    }

    public void selectPreviousLayer() {
        if (widget != null) widget.selectPreviousLayer();
    }

    public void selectNextLayer() {
        if (widget != null) widget.selectNextLayer();
    }

    public void showAllLayers() {
        if (widget != null) widget.showAllLayers();
    }

    public void reset() {
        if (widget != null) widget.reset();
    }

    public void selectNextStage() {
        if (closed || widget == null || stages.size() <= 1) return;
        switchStage(stageIndexAfter(stageIndex, stages.size()));
    }

    public void selectPreviousStage() {
        if (closed || widget == null || stages.size() <= 1) return;
        switchStage(stageIndexBefore(stageIndex, stages.size()));
    }

    private void switchStage(int nextIndex) {
        if (widget != null) widget.close();
        widget = null;
        schema = null;
        materials = StructureMaterialSummary.empty();
        compileAnimationStart = -1L;
        stageIndex = nextIndex;
        compilation = acquireCompilation(stageIndex);
        compilation.start();
        StructurePreviewSchema completed = compilation.schema();
        if (completed != null) adoptCompletedSchema(completed);
    }

    private StructurePreviewCompilation acquireCompilation(int index) {
        return stages.get(index).number() == StructurePreviewCompilationCache.DEFAULT_STAGE_NUMBER
                ? StructurePreviewCompilationCache.instance().acquire(machine)
                : StructurePreviewCompilationCache.instance().acquire(machine, stages.get(index).number());
    }

    private void adoptCompletedSchema(StructurePreviewSchema completed) {
        schema = completed;
        materials = StructureMaterialSummary.from(completed);
        widget = new StructurePreviewWidget(new StructurePreviewRenderer(completed));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (widget != null) widget.close();
    }

    static int candidateIndex(int slot, long timeMillis, int candidateCount) {
        return candidateIndex(slot, timeMillis, candidateCount, VISIBLE_SLOT_COUNT);
    }

    static int candidateIndex(int slot, long timeMillis, int candidateCount, int visibleSlotCount) {
        if (slot < 0 || slot >= visibleSlotCount || visibleSlotCount <= 0 || candidateCount <= 0) return -1;
        if (slot >= candidateCount) return -1;
        int offset = (int) (Math.floorDiv(timeMillis, 1_000L) % candidateCount);
        return Math.floorMod(slot + offset, candidateCount);
    }

    static int materialPage(long timeMillis, int materialCount) {
        return materialPage(timeMillis, materialCount, VISIBLE_SLOT_COUNT);
    }

    static int materialPage(long timeMillis, int materialCount, int visibleSlotCount) {
        if (visibleSlotCount <= 0) return 0;
        int pageCount = (materialCount + visibleSlotCount - 1) / visibleSlotCount;
        if (pageCount == 0) return 0;
        return Math.floorMod((int) (Math.floorDiv(timeMillis, 8_000L) % pageCount), pageCount);
    }

    static int stageIndexAfter(int currentIndex, int stageCount) {
        return stageCount <= 1 ? currentIndex : Math.floorMod(currentIndex + 1, stageCount);
    }

    static int stageIndexBefore(int currentIndex, int stageCount) {
        return stageCount <= 1 ? currentIndex : Math.floorMod(currentIndex - 1, stageCount);
    }

    private static int stageIndexFor(int stageNumber, List<MachineStructureStage> stages) {
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).number() == stageNumber) return index;
        }
        return 0;
    }

    private void ensurePreviewStarted() {
        compilation.start();
        StructurePreviewSchema completed = compilation.schema();
        if (completed != null && widget == null) adoptCompletedSchema(completed);
    }

    private static String compileStatus(long elapsedMillis) {
        int dotCount = (int) (Math.max(0L, elapsedMillis) / 1_000L % 3L) + 1;
        return "Render compile" + ".".repeat(dotCount);
    }
}
