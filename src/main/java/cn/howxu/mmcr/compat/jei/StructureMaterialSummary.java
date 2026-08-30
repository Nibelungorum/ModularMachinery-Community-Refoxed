package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Counted item requirements derived from the blocks rendered by one structure preview schema.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureMaterialSummary {
    private final List<Entry> entries;

    private StructureMaterialSummary(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static StructureMaterialSummary from(StructurePreviewSchema schema) {
        List<MutableEntry> merged = new ArrayList<>();
        schema.states().values().forEach(state -> {
            ItemStack stack = new ItemStack(state.getBlock());
            if (stack.isEmpty()) return;
            MutableEntry existing = merged.stream()
                    .filter(entry -> ItemStack.isSameItemSameComponents(entry.stack, stack))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                merged.add(new MutableEntry(stack, 1));
            } else {
                existing.count++;
            }
        });

        return new StructureMaterialSummary(merged.stream()
                .sorted(Comparator.comparingInt((MutableEntry entry) -> entry.count).reversed())
                .map(entry -> new Entry(entry.stack, entry.count))
                .toList());
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<ItemStack> transferStacks() {
        return entries.stream().map(Entry::transferStack).toList();
    }

    public record Entry(ItemStack stack, int count) {
        public Entry {
            if (count < 1) throw new IllegalArgumentException("count must be positive");
            stack = stack.copyWithCount(1);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        private ItemStack transferStack() {
            return stack.copyWithCount(count);
        }
    }

    private static final class MutableEntry {
        private final ItemStack stack;
        private int count;

        private MutableEntry(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
