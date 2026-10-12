package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.client.preview.StructureMaterialSummary;
import cn.howxu.mmcr.compat.jei.MachineStructureDisplay;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.LongSupplier;

/** A native EMI material slot, with one frame-stable page shared by all nine slots.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiStructureMaterialWidget extends SlotWidget {
    public static final int PAGE_SIZE = 9;
    private final Frame frame;
    private final int slot;

    public static void add(WidgetHolder holder, MachineStructureDisplay display, int x, int y, LongSupplier clock) {
        Frame frame = new Frame(display.materials().entries(), clock);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            holder.add(new EmiStructureMaterialWidget(frame, slot, x + slot * 18, y));
        }
    }

    EmiStructureMaterialWidget(Frame frame, int slot, int x, int y) {
        super(EmiStack.EMPTY, x, y);
        this.frame = frame;
        this.slot = slot;
    }

    @Override public EmiIngredient getStack() { return frame.stack(slot); }

    @Override public Bounds getBounds() { return getStack().isEmpty() ? Bounds.EMPTY : super.getBounds(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (slot == 0) frame.beginFrame();
        if (!getStack().isEmpty()) super.render(graphics, mouseX, mouseY, delta);
    }

    /** The stack's independent EMI amount comes from the summary, never its normalized item count.
     * @author howxu <dev@howxu.cn>
     */
    static final class Frame {
        private final List<EmiStack> materials;
        private final LongSupplier clock;
        private int first;

        Frame(List<StructureMaterialSummary.Entry> entries, LongSupplier clock) {
            this.materials = entries.stream().map(entry -> EmiStack.of(entry.stack(), entry.count())).toList();
            this.clock = clock;
        }

        void beginFrame() {
            int pages = (materials.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            first = pages == 0 ? 0 : Math.floorMod(Math.floorDiv(clock.getAsLong(), 8_000L), pages) * PAGE_SIZE;
        }

        EmiStack stack(int slot) {
            int index = first + slot;
            return slot >= 0 && slot < PAGE_SIZE && index < materials.size() ? materials.get(index) : EmiStack.EMPTY;
        }
    }
}
