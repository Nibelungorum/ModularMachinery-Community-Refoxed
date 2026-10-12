package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.client.preview.StructureMaterialSummary.Entry;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** @author howxu <dev@howxu.cn> */
class EmiStructureMaterialWidgetTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void nativeSlotUsesSummaryQuantityAndQueryRemainsFrameStable() {
        AtomicLong clock = new AtomicLong();
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < 9; index++) entries.add(new Entry(new ItemStack(Items.STONE), index + 65));
        entries.add(new Entry(new ItemStack(Items.GOLD_INGOT), 150));
        var frame = new EmiStructureMaterialWidget.Frame(entries, clock::get);
        var slot = new EmiStructureMaterialWidget(frame, 0, 2, 3);
        var last = new EmiStructureMaterialWidget(frame, 8, 146, 3);
        assertInstanceOf(SlotWidget.class, slot);
        frame.beginFrame();
        assertEquals(65, slot.getStack().getAmount());
        assertEquals(1, entries.getFirst().stack().getCount());
        clock.set(8_000);
        assertEquals(Items.STONE, slot.getStack().getEmiStacks().getFirst().getKey());
        frame.beginFrame();
        assertEquals(Items.GOLD_INGOT, slot.getStack().getEmiStacks().getFirst().getKey());
        assertEquals(150, slot.getStack().getAmount());
        assertEquals(new Bounds(2, 3, 18, 18), slot.getBounds());
        assertTrue(last.getStack().isEmpty());
        assertEquals(Bounds.EMPTY, last.getBounds());
        clock.set(16_000);
        frame.beginFrame();
        assertFalse(last.getStack().isEmpty());
    }

    @Test void emptySummaryHasNoInteractiveSlots() {
        var frame = new EmiStructureMaterialWidget.Frame(List.of(), () -> 8_000);
        frame.beginFrame();
        var slot = new EmiStructureMaterialWidget(frame, 0, 0, 0);
        assertTrue(slot.getStack().isEmpty());
        assertEquals(Bounds.EMPTY, slot.getBounds());
    }
}
