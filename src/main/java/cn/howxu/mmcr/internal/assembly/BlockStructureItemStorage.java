package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.internal.event.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        ResourceHandler<ItemResource> handler = level.getCapability(ModCapabilities.ITEM_BLOCK, position, null);
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

    private interface StorageAccess extends StructureItemSource, StructureItemSink {}

    private static final class HandlerAccess implements StorageAccess {
        private final ResourceHandler<ItemResource> handler;

        private HandlerAccess(ResourceHandler<ItemResource> handler) {
            this.handler = handler;
        }

        @Override
        public List<ItemStack> copyStacks() {
            List<ItemStack> stacks = new ArrayList<>(handler.size());
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                stacks.add(resource.isEmpty() ? ItemStack.EMPTY : resource.toStack((int) handler.getAmountAsLong(slot)));
            }
            return stacks;
        }

        @Override
        public boolean canExtractAll(List<ItemStack> requirements) {
            try (Transaction transaction = Transaction.openRoot()) {
                return extractAll(requirements, transaction);
            }
        }

        @Override
        public boolean extractAll(List<ItemStack> requirements) {
            try (Transaction transaction = Transaction.openRoot()) {
                if (!extractAll(requirements, transaction)) return false;
                transaction.commit();
                return true;
            }
        }

        @Override
        public boolean accept(ItemStack stack) {
            if (stack.isEmpty()) return true;
            try (Transaction transaction = Transaction.openRoot()) {
                int remaining = stack.getCount();
                ItemResource resource = ItemResource.of(stack);
                for (int slot = 0; slot < handler.size() && remaining > 0; slot++) {
                    int inserted = handler.insert(slot, resource, remaining, transaction);
                    remaining -= inserted;
                }
                if (remaining > 0) return false;
                transaction.commit();
                return true;
            }
        }

        private boolean extractAll(List<ItemStack> requirements, Transaction transaction) {
            for (ItemStack requirement : requirements) {
                int remaining = requirement.getCount();
                ItemResource resource = ItemResource.of(requirement);
                for (int slot = 0; slot < handler.size() && remaining > 0; slot++) {
                    remaining -= handler.extract(slot, resource, remaining, transaction);
                }
                if (remaining > 0) return false;
            }
            return true;
        }
    }
}
