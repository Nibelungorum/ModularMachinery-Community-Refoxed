package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.helper.CraftCheck;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record ItemRequirement(Ingredient item, int count, @Nullable String tag, IOType ioType) implements MachineRequirement {

    @Override
    public String type() {
        return "item";
    }

    @Override
    public String describe() {
        return count + "x " + item.items()
                .findFirst()
                .map(h -> h.value().builtInRegistryHolder().getRegisteredName())
                .orElseGet(item::toString);
    }

    @Override
    public boolean matches(ProcessingComponent component) {
        if (!component.matchesTag(tag)) return false;
        return component.getContainer() instanceof ItemBusBlockEntity bus && bus.ioType() == ioType;
    }

    @Override
    public CraftCheck simulate(RecipeCraftingContext context) {
        if (ioType == IOType.OUTPUT) {
            return simulateOutput(context);
        }

        List<ItemRoute> route = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        int remaining = count;
        for (ProcessingComponent component : context.componentsMatching(this)) {
            if (!(component.getContainer() instanceof ItemBusBlockEntity bus)) continue;
            IItemHandler handler = bus.getItemHandler(null);
            int componentAvailable = 0;
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!item.test(stack)) continue;

                int take = Math.min(remaining, stack.getCount());
                if (take <= 0) continue;
                componentAvailable += take;
                remaining -= take;
                route.add(new ItemRoute(bus, handler, slot, take));
            }
            searched.add(bus.getBlockPos() + ":item_input_bus=" + componentAvailable);
            if (remaining <= 0) {
                context.route(this, route);
                return CraftCheck.success();
            }
        }
        return CraftCheck.failure("Missing " + describe() + " (short " + remaining + "; searched " + searched + ")");
    }

    @Override
    public boolean commit(RecipeCraftingContext context) {
        if (ioType == IOType.OUTPUT) {
            return commitOutput(context);
        }
        List<ItemRoute> route = context.route(this, ItemRoute.class);
        if (route == null) return false;

        int remaining = count;
        for (ItemRoute entry : route) {
            int before = entry.handler.getStackInSlot(entry.slot).getCount();
            int extracted = entry.handler.extractItem(entry.slot, Math.min(entry.count, remaining), false).getCount();
            int after = entry.handler.getStackInSlot(entry.slot).getCount();
            extracted = Math.max(extracted, before - after);
            remaining -= extracted;
            if (remaining <= 0) return true;
        }
        return false;
    }

    private CraftCheck simulateOutput(RecipeCraftingContext context) {
        ItemStack remaining = item.items()
                .findFirst()
                .map(holder -> new ItemStack(holder.value(), count))
                .orElse(ItemStack.EMPTY);
        if (remaining.isEmpty()) return CraftCheck.failure("Missing output stack for " + describe());

        List<ItemRoute> route = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        for (ProcessingComponent component : context.componentsMatching(this)) {
            if (!(component.getContainer() instanceof ItemBusBlockEntity bus)) continue;
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                int before = remaining.getCount();
                ItemStack simulated = handler.insertItem(slot, remaining.copy(), true);
                int inserted = before - simulated.getCount();
                if (inserted > 0) route.add(new ItemRoute(bus, handler, slot, inserted));
                remaining = simulated;
            }
            searched.add(bus.getBlockPos() + ":item_output_bus");
            if (remaining.isEmpty()) {
                context.route(this, route);
                return CraftCheck.success();
            }
        }
        return CraftCheck.failure("Missing output space for " + describe() + " (short " + remaining.getCount() + "; searched " + searched + ")");
    }

    private boolean commitOutput(RecipeCraftingContext context) {
        List<ItemRoute> route = context.route(this, ItemRoute.class);
        if (route == null) return false;
        ItemStack remaining = item.items()
                .findFirst()
                .map(holder -> new ItemStack(holder.value(), count))
                .orElse(ItemStack.EMPTY);
        for (ItemRoute entry : route) {
            if (remaining.isEmpty()) return true;
            ItemStack toInsert = remaining.copyWithCount(Math.min(entry.count, remaining.getCount()));
            ItemStack leftover = entry.handler.insertItem(entry.slot, toInsert, false);
            remaining.shrink(toInsert.getCount() - leftover.getCount());
        }
        return remaining.isEmpty();
    }

    public record ItemRoute(ItemBusBlockEntity bus, IItemHandler handler, int slot, int count) {
    }
}
