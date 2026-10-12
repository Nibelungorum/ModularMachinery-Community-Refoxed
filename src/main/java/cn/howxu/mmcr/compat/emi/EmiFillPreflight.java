package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only validation shared by fill feedback and execution; no EMI or client dependencies.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiFillPreflight {
    private EmiFillPreflight() { }

    public static int additionalBatches(int absoluteLimit, int alreadyPresent, int requested) {
        long remaining = Math.max(0L, (long) absoluteLimit - alreadyPresent);
        return (int) Math.min(remaining, Math.max(0L, requested));
    }

    static List<RecipeDisplayEntry> inputs(MachineRecipeData data) {
        return data.entries().stream().filter(entry -> entry.io() == IOType.INPUT).toList();
    }

    static Optional<Component> support(ItemBusMenu menu, MachineRecipeData data) {
        if (!menu.getCarried().isEmpty()) return failure("emi.mmcr.transfer.cursor_not_empty");
        if (menu.owner() != null && menu.owner().ioType() != cn.howxu.mmcr.util.IOType.INPUT) {
            return failure("jei.mmcr.transfer.unsupported", "mmcr:item_output_bus");
        }
        // EMI clears every bus slot, including those outside desired. Native bus extraction
        // removes at most one item stack, even when the synchronized slot displays more.
        for (int i = 0; i < menu.busSlotCount(); i++) {
            ItemStack stack = menu.getSlot(ItemBusMenu.BUS_SLOT_START + i).getItem();
            if (!stack.isEmpty() && stack.getCount() > stack.getMaxStackSize()) {
                return failure("emi.mmcr.transfer.oversized_bus_stack", i + 1, stack.getHoverName(), stack.getMaxStackSize());
            }
        }
        List<RecipeDisplayEntry> inputs = inputs(data);
        if (inputs.isEmpty()) return failure("jei.mmcr.transfer.no_item_inputs");
        for (RecipeDisplayEntry input : inputs) {
            if (input.kind() != RecipeDisplayEntry.Kind.ITEM || !(input.requirement() instanceof ItemRequirement)) {
                return failure("jei.mmcr.transfer.unsupported", input.typeId().toString());
            }
            if (input.items().isEmpty()) return failure("emi.not_enough_ingredients");
            if (!input.exactComponents() || input.components().hasNonExactValues()) {
                return failure("emi.mmcr.transfer.component_constraints");
            }
            if (input.amount() <= 0 || input.amount() > Integer.MAX_VALUE) return failure("emi.mmcr.transfer.slot_capacity");
        }
        if (inputs.size() > menu.busSlotCount()) return failure("jei.mmcr.transfer.not_enough_slots");
        return Optional.empty();
    }

    /** Safe upper bound before native candidate selection; actual selected capacities are checked afterwards. */
    static int capacityLimit(ItemBusMenu menu, MachineRecipeData data) {
        int limit = Integer.MAX_VALUE;
        boolean consumed = false;
        List<RecipeDisplayEntry> inputs = inputs(data);
        for (int i = 0; i < inputs.size(); i++) {
            RecipeDisplayEntry input = inputs.get(i);
            Slot slot = menu.getSlot(ItemBusMenu.BUS_SLOT_START + i);
            int capacity = input.items().stream().filter(slot::mayPlace)
                    .mapToInt(stack -> capacity(slot, stack)).max().orElse(0);
            if (capacity < input.amount()) return 0;
            if (input.chance() != 0F) {
                consumed = true;
                limit = Math.min(limit, (int) (capacity / input.amount()));
            }
        }
        return consumed ? limit : 1;
    }

    /** Counts complete consumed batches, requiring each fixed input only once. */
    static int batchesAlreadyPresent(ItemBusMenu menu, MachineRecipeData data) {
        int batches = Integer.MAX_VALUE;
        boolean consumed = false;
        List<RecipeDisplayEntry> inputs = inputs(data);
        for (int i = 0; i < inputs.size(); i++) {
            RecipeDisplayEntry input = inputs.get(i);
            ItemStack stack = menu.getSlot(ItemBusMenu.BUS_SLOT_START + i).getItem();
            if (!matches(input, stack, data) || stack.getCount() < input.amount()) return 0;
            if (input.chance() != 0F) {
                consumed = true;
                batches = Math.min(batches, (int) (stack.getCount() / input.amount()));
            }
        }
        return consumed ? batches : 0;
    }

    /** Bounds the chosen native candidates by per-slot limits and shared item+component stock. */
    static int selectedLimit(ItemBusMenu menu, MachineRecipeData data, List<ItemStack> selected) {
        List<RecipeDisplayEntry> inputs = inputs(data);
        if (selected.size() != inputs.size()) return 0;
        int limit = capacityLimit(menu, data);
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = selected.get(i);
            RecipeDisplayEntry input = inputs.get(i);
            Slot slot = menu.getSlot(ItemBusMenu.BUS_SLOT_START + i);
            if (!matches(input, stack, data) || !slot.mayPlace(stack) || capacity(slot, stack) < input.amount()) return 0;
            if (input.chance() != 0F) limit = Math.min(limit, (int) (capacity(slot, stack) / input.amount()));
            long fixed = 0, perBatch = 0, available = 0;
            for (int j = 0; j < inputs.size(); j++) {
                if (ItemStack.isSameItemSameComponents(stack, selected.get(j))) {
                    if (inputs.get(j).chance() == 0F) fixed += inputs.get(j).amount();
                    else perBatch += inputs.get(j).amount();
                }
            }
            Player player = player(menu);
            for (Slot source : menu.slots) {
                if (source.mayPickup(player) && ItemStack.isSameItemSameComponents(stack, source.getItem())) {
                    available += source.getItem().getCount();
                }
            }
            if (available < fixed) return 0;
            if (perBatch > 0) limit = (int) Math.min(limit, (available - fixed) / perBatch);
        }
        return limit;
    }

    public static Optional<Component> check(ItemBusMenu menu, MachineRecipeData data, List<ItemStack> desired) {
        Optional<Component> unsupported = support(menu, data);
        if (unsupported.isPresent()) return unsupported;
        List<RecipeDisplayEntry> inputs = inputs(data);
        if (desired == null || desired.size() != inputs.size()) return failure("emi.not_enough_ingredients");
        long batches = -1;
        for (int i = 0; i < inputs.size(); i++) {
            RecipeDisplayEntry input = inputs.get(i);
            ItemStack stack = desired.get(i);
            Slot slot = menu.getSlot(ItemBusMenu.BUS_SLOT_START + i);
            if (!matches(input, stack, data)) return failure("emi.mmcr.transfer.component_constraints");
            if (!slot.mayPlace(stack)) return failure("jei.mmcr.transfer.unsupported", input.typeId().toString());
            if (stack.getCount() > capacity(slot, stack)) return failure("emi.mmcr.transfer.slot_capacity");
            if (input.chance() == 0F) {
                if (stack.getCount() != input.amount()) return failure("emi.mmcr.transfer.slot_capacity");
            } else {
                if (stack.getCount() < input.amount() || stack.getCount() % input.amount() != 0) {
                    return failure("emi.mmcr.transfer.slot_capacity");
                }
                long count = stack.getCount() / input.amount();
                if (batches >= 0 && batches != count) return failure("emi.mmcr.transfer.slot_capacity");
                batches = count;
            }
        }
        // Both native paths can be admitted using the stricter client sequence: clear ALL bus
        // slots to player inventory first, then withdraw the exact desired stacks.
        List<Slot> slots = menu.slots.subList(menu.playerInventorySlotStart(),
                menu.playerInventorySlotStart() + ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT);
        List<ItemStack> inventory = new ArrayList<>(slots.stream().map(slot -> slot.getItem().copy()).toList());
        Player player = player(menu);
        for (int i = 0; i < menu.busSlotCount(); i++) {
            Slot bus = menu.getSlot(ItemBusMenu.BUS_SLOT_START + i);
            ItemStack moving = bus.getItem().copy();
            if (moving.isEmpty()) continue;
            if (!bus.mayPickup(player)) return failure("emi.mmcr.transfer.inventory_space");
            for (int pass = 0; pass < 2 && !moving.isEmpty(); pass++) {
                for (int j = slots.size() - 1; j >= 0 && !moving.isEmpty(); j--) {
                    ItemStack current = inventory.get(j);
                    if (!slots.get(j).mayPlace(moving)) continue;
                    if (pass == 0 && (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, moving))) continue;
                    if (pass == 1 && !current.isEmpty()) continue;
                    int moved = Math.min(moving.getCount(), Math.max(0, capacity(slots.get(j), moving) - current.getCount()));
                    if (moved > 0) {
                        // Empty stacks cannot be grown; replace the copied entry instead.
                        inventory.set(j, moving.copyWithCount(current.getCount() + moved));
                        moving.shrink(moved);
                    }
                }
            }
            if (!moving.isEmpty()) return failure("emi.mmcr.transfer.inventory_space");
        }
        for (ItemStack stack : desired) {
            int needed = stack.getCount();
            for (int i = 0; i < inventory.size() && needed > 0; i++) {
                ItemStack source = inventory.get(i);
                if (!slots.get(i).mayPickup(player) || !ItemStack.isSameItemSameComponents(source, stack)) continue;
                int taken = Math.min(source.getCount(), needed);
                source.shrink(taken);
                needed -= taken;
            }
            if (needed > 0) return failure("emi.not_enough_ingredients");
        }
        return Optional.empty();
    }

    private static boolean matches(RecipeDisplayEntry input, ItemStack stack, MachineRecipeData data) {
        return !stack.isEmpty() && input.requirement() instanceof ItemRequirement item
                && item.item() != null && item.item().test(stack) && input.components().matches(stack, data.componentOps());
    }

    private static int capacity(Slot slot, ItemStack stack) {
        // The old resource's no-argument limit changes when the bus is cleared.
        return Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
    }

    private static Player player(ItemBusMenu menu) {
        Slot slot = menu.getSlot(menu.playerInventorySlotStart());
        return slot.container instanceof Inventory inventory ? inventory.player : null;
    }

    static Optional<Component> failure(String key, Object... arguments) {
        return Optional.of(Component.translatable(key, arguments));
    }
}
