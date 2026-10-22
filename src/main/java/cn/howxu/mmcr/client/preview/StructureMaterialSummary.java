package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewPredicates;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

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
    private static final StructureMaterialSummary EMPTY = new StructureMaterialSummary(List.of());
    private final List<Entry> entries;

    private StructureMaterialSummary(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static StructureMaterialSummary empty() {
        return EMPTY;
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
            ResourceLocation levelSlot = stage.levelSlots().get(position);
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
            boolean fluid = state.getBlock() instanceof LiquidBlock;
            FluidState fluidState = state.getFluidState();
            ItemStack stack = fluid
                    ? fluidState.getType().getFluidType().getBucket(
                            new FluidStack(fluidState.getType(), FluidType.BUCKET_VOLUME))
                    : new ItemStack(state.getBlock());
            if (stack.isEmpty()) continue;
            MutableEntry existing = merged.stream()
                    .filter(entry -> ItemStack.isSameItemSameComponents(entry.stack, stack))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                existing = new MutableEntry(stack);
                merged.add(existing);
            }
            if (!fluid) {
                existing.count++;
            } else if (fluidState.isSource()) {
                existing.count++;
                existing.sourceCount++;
            } else {
                existing.nonSourceCount++;
            }
        }

        return new StructureMaterialSummary(merged.stream()
                .sorted(Comparator.comparingInt((MutableEntry entry) -> entry.count).reversed())
                .map(entry -> new Entry(entry.stack, Math.max(1, entry.count), entry.sourceCount, entry.nonSourceCount))
                .toList());
    }

    private static int highestSharedLevelRank(MachineStructureStage stage) {
        return stage.levelSlots().values().stream()
                .mapToInt(typeId -> MachineLevelRegistry.levelsForType(typeId).stream()
                        .mapToInt(MachineLevel::priority)
                        .max().orElse(-1))
                .max().orElse(-1);
    }

    private static BlockState levelState(ResourceLocation typeId, int levelRank) {
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
        return entries.stream()
                .filter(entry -> entry.sourceCount() > 0 || entry.nonSourceCount() == 0)
                .map(Entry::transferStack).toList();
    }

    /** Display quantity is at least one, including buckets used only to identify non-source fluid. */
    public record Entry(ItemStack stack, int count, int sourceCount, int nonSourceCount) {
        public Entry(ItemStack stack, int count) {
            this(stack, count, 0, 0);
        }

        public Entry {
            if (count < 1) throw new IllegalArgumentException("count must be positive");
            stack = stack.copyWithCount(1);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        public List<Component> tooltipLines() {
            if (sourceCount > 0 || nonSourceCount > 0) {
                return List.of(Component.translatable("gui.mmcr.structure_preview.fluid_source_count",
                                ReadableNumber.formatExact(sourceCount)),
                        Component.translatable("gui.mmcr.structure_preview.fluid_non_source_count",
                                ReadableNumber.formatExact(nonSourceCount)));
            }
            return List.of(Component.translatable("jei.mmcr.machine_recipe.item_count", ReadableNumber.formatExact(count)));
        }

        private ItemStack transferStack() {
            return stack.copyWithCount(count);
        }
    }

    private static final class MutableEntry {
        private final ItemStack stack;
        private int count;
        private int sourceCount;
        private int nonSourceCount;

        private MutableEntry(ItemStack stack) {
            this.stack = stack;
        }
    }
}
