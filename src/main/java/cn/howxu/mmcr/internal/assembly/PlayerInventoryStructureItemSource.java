package cn.howxu.mmcr.internal.assembly;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Player inventory backed source for structure assembly blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlayerInventoryStructureItemSource implements StructureItemSource {
    private final List<ItemStack> stacks;

    public PlayerInventoryStructureItemSource(Player player) {
        this(stacksFrom(player.getInventory()));
    }

    private PlayerInventoryStructureItemSource(List<ItemStack> stacks) {
        this.stacks = stacks;
    }

    public static PlayerInventoryStructureItemSource forStacks(List<ItemStack> stacks) {
        return new PlayerInventoryStructureItemSource(stacks);
    }

    @Override
    public List<ItemStack> copyStacks() {
        return copyStacks(stacks);
    }

    @Override
    public boolean canExtractAll(List<ItemStack> requirements) {
        List<ItemStack> simulated = copyStacks();
        return extractFrom(simulated, requirements);
    }

    @Override
    public boolean extractAll(List<ItemStack> requirements) {
        if (!canExtractAll(requirements)) return false;
        return extractFrom(stacks, requirements);
    }

    private static List<ItemStack> stacksFrom(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            result.add(inventory.getItem(slot));
        }
        return result;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static boolean extractFrom(List<ItemStack> inventory, List<ItemStack> requirements) {
        for (ItemStack requirement : requirements) {
            int remaining = requirement.getCount();
            for (ItemStack stack : inventory) {
                if (remaining <= 0) break;
                if (!ItemStack.isSameItemSameComponents(stack, requirement)) continue;
                int extracted = Math.min(remaining, stack.getCount());
                stack.shrink(extracted);
                remaining -= extracted;
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
