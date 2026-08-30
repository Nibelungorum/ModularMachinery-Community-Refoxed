package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewPredicates;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
        return fromStates(schema.states().values());
    }

    public static StructureMaterialSummary from(Machine machine) {
        Objects.requireNonNull(machine, "machine");
        List<MachineStructureStage> stages = machine.structureStages();
        if (stages.isEmpty()) throw new IllegalArgumentException("machine structure stages empty");

        MachineStructureStage stage = stages.getFirst();
        int levelRank = highestSharedLevelRank(stage);
        List<BlockState> states = new ArrayList<>();
        stage.pattern().pattern().forEach((position, predicate) -> {
            Identifier levelSlot = stage.levelSlots().get(position);
            BlockState state = levelSlot == null
                    ? predicate instanceof BlockPredicate.MachineCoupler
                            ? MultiblockPreviewPredicates.machineCouplerState().orElse(null)
                            : predicate.preferredState().orElse(null)
                    : levelState(levelSlot, levelRank);
            if (state != null) states.add(state);
        });
        return fromStates(states);
    }

    private static StructureMaterialSummary fromStates(Iterable<BlockState> states) {
        List<MutableEntry> merged = new ArrayList<>();
        for (BlockState state : states) {
            ItemStack stack = new ItemStack(state.getBlock());
            if (stack.isEmpty()) continue;
            MutableEntry existing = merged.stream()
                    .filter(entry -> ItemStack.isSameItemSameComponents(entry.stack, stack))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                merged.add(new MutableEntry(stack, 1));
            } else {
                existing.count++;
            }
        }

        return new StructureMaterialSummary(merged.stream()
                .sorted(Comparator.comparingInt((MutableEntry entry) -> entry.count).reversed())
                .map(entry -> new Entry(entry.stack, entry.count))
                .toList());
    }

    private static int highestSharedLevelRank(MachineStructureStage stage) {
        return stage.levelSlots().values().stream()
                .mapToInt(typeId -> MachineLevelRegistry.levelsForType(typeId).stream()
                        .mapToInt(MachineLevel::priority)
                        .max().orElse(-1))
                .max().orElse(-1);
    }

    private static BlockState levelState(Identifier typeId, int levelRank) {
        return MachineLevelRegistry.levelsForType(typeId).stream()
                .filter(level -> level.priority() == levelRank)
                .findFirst()
                .map(MachineLevel::statePredicate)
                .filter(BlockPredicate.OfBlockState.class::isInstance)
                .map(BlockPredicate.OfBlockState.class::cast)
                .map(BlockPredicate.OfBlockState::state)
                .orElse(Blocks.AIR.defaultBlockState());
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
