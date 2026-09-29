package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.internal.event.ModCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Block capability backed storage for structure assembly blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BlockStructureItemStorage implements StructureItemStorage {
    private final StorageAccess access;

    private BlockStructureItemStorage(StorageAccess access) {
        this.access = access;
    }

    static Optional<BlockStructureItemStorage> at(ServerLevel level, BlockPos position) {
        IItemHandler handler = level.getCapability(ModCapabilities.ITEM_BLOCK, position, null);
        return handler == null ? Optional.empty() : Optional.of(new BlockStructureItemStorage(new HandlerAccess(handler)));
    }

    @Override
    public StructureItemSource source() {
        return access;
    }

    @Override
    public StructureItemSink sink() {
        return access;
    }

    private interface StorageAccess extends StructureItemSource, StructureItemSink {
    }

    private record HandlerAccess(IItemHandler handler) implements StorageAccess {
        @Override
        public List<ItemStack> copyStacks() {
            List<ItemStack> stacks = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) stacks.add(handler.getStackInSlot(slot).copy());
            return stacks;
        }

        @Override
        public boolean canExtractAll(List<ItemStack> requirements) {
            List<ItemStack> available = copyStacks();
            for (ItemStack requirement : requirements) {
                int remaining = requirement.getCount();
                for (ItemStack stack : available) {
                    if (!ItemStack.isSameItemSameComponents(stack, requirement)) continue;
                    int extracted = Math.min(remaining, stack.getCount());
                    stack.shrink(extracted);
                    remaining -= extracted;
                    if (remaining == 0) break;
                }
                if (remaining > 0) return false;
            }
            return true;
        }

        @Override
        public boolean extractAll(List<ItemStack> requirements) {
            if (!canExtractAll(requirements)) return false;
            for (ItemStack requirement : requirements) {
                int remaining = requirement.getCount();
                for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!ItemStack.isSameItemSameComponents(stack, requirement)) continue;
                    remaining -= handler.extractItem(slot, remaining, false).getCount();
                }
                if (remaining > 0) return false;
            }
            return true;
        }

        @Override
        public boolean accept(ItemStack stack) {
            if (stack.isEmpty()) return true;
            ItemStack remainder = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = handler.insertItem(slot, remainder, true);
            }
            if (!remainder.isEmpty()) return false;
            remainder = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = handler.insertItem(slot, remainder, false);
            }
            return remainder.isEmpty();
        }
    }
}
