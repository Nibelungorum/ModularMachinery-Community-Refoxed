package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.util.IOType;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Fills input buses using the exact preflighted plan and EMI's native transport.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineEmiRecipeHandler implements StandardRecipeHandler<ItemBusMenu> {
    private EmiCraftContext<ItemBusMenu> checkedContext;
    private EmiRecipe checkedRecipe;
    private Plan checkedPlan;

    @Override public boolean supportsRecipe(EmiRecipe recipe) { return recipe instanceof MachineEmiRecipe; }

    @Override public List<Slot> getInputSources(ItemBusMenu menu) {
        var player = Minecraft.getInstance().player;
        List<Slot> sources = new ArrayList<>();
        for (Slot slot : getCraftingSlots(menu)) if (slot.mayPickup(player)) sources.add(slot);
        for (int i = menu.playerInventorySlotStart(); i < menu.playerInventorySlotStart() + ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.mayPickup(player)) sources.add(slot);
        }
        return sources;
    }

    @Override public List<Slot> getCraftingSlots(ItemBusMenu menu) {
        return List.copyOf(menu.slots.subList(ItemBusMenu.BUS_SLOT_START, ItemBusMenu.BUS_SLOT_START + menu.busSlotCount()));
    }

    @Override public @Nullable Slot getOutputSlot(ItemBusMenu menu) { return null; }

    @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<ItemBusMenu> context) {
        checkedContext = context;
        checkedRecipe = recipe;
        checkedPlan = prepare(recipe, context);
        return checkedPlan.error().isEmpty();
    }

    @Override public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<ItemBusMenu> context) {
        canCraft(recipe, context);
        return checkedPlan.error().map(error -> List.of(ClientTooltipComponent.create(error.getVisualOrderText())))
                .orElseGet(List::of);
    }

    @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<ItemBusMenu> context) {
        // performFill calls canCraft and craft with the same context. Reuse those candidates,
        // rather than letting StandardRecipeHandler select a different native plan a second time.
        Plan plan = checkedContext == context && checkedRecipe == recipe && checkedPlan != null
                ? checkedPlan : prepare(recipe, context);
        checkedContext = null;
        checkedRecipe = null;
        checkedPlan = null;
        if (plan.error().isPresent() || !(recipe instanceof MachineEmiRecipe machine)) return false;
        ItemBusMenu menu = context.getScreenHandler();
        int containerId = plan.containerId();
        if (!current(context, menu, containerId) || !unchanged(menu, plan) || direction(menu).isPresent()
                || EmiFillPreflight.check(menu, machine.data(), plan.desired()).isPresent()) return false;
        Minecraft client = Minecraft.getInstance();
        client.setScreen(context.getScreen());
        if (!current(context, menu, containerId)) return false;
        if (EmiClient.onServer) {
            EmiClient.sendFillRecipe(this, context.getScreen(), containerId, 0, plan.desired(), recipe);
            return true;
        }
        return EmiRecipeFiller.clientFill(this, recipe, context.getScreen(), plan.desired(), EmiCraftContext.Destination.NONE);
    }

    private Plan prepare(EmiRecipe recipe, EmiCraftContext<ItemBusMenu> context) {
        if (!(recipe instanceof MachineEmiRecipe machine)) return failed("jei.mmcr.transfer.unsupported", recipe.getId());
        ItemBusMenu menu = context.getScreenHandler();
        MachineRecipeData data = machine.data();
        Optional<Component> error = EmiFillPreflight.support(menu, data);
        if (error.isPresent()) return new Plan(List.of(), error, -1, List.of());
        error = direction(menu);
        if (error.isPresent()) return new Plan(List.of(), error, -1, List.of());
        int upperLimit = EmiFillPreflight.capacityLimit(menu, data);
        int already = EmiFillPreflight.batchesAlreadyPresent(menu, data);
        int additional = EmiFillPreflight.additionalBatches(upperLimit, already, context.getAmount());
        if (additional == 0) return failed("emi.mmcr.transfer.slot_capacity");
        int target = already + additional;
        // EMI counts fixed inputs as batches too. Convert our final target to its native
        // increment, so one catalyst does not reset a bus containing several consumed batches.
        int nativeAlready = EmiRecipeFiller.batchesAlreadyPresent(recipe, this, context.getScreen());
        if (nativeAlready > upperLimit) return failed("emi.mmcr.transfer.slot_capacity");
        int amount = EmiFillPreflight.additionalBatches(target, nativeAlready, Integer.MAX_VALUE);
        List<ItemStack> selected = EmiRecipeFiller.getStacks(this, recipe, context.getScreen(), amount);
        if (selected == null) return failed("emi.not_enough_ingredients");
        List<RecipeDisplayEntry> inputs = EmiFillPreflight.inputs(data);
        if (selected.size() != inputs.size()) return failed("emi.not_enough_ingredients");
        int actualLimit = EmiFillPreflight.selectedLimit(menu, data, selected);
        target = Math.min(target, actualLimit);
        // The release's selection can be conservative for overlapping alternatives/catalysts.
        // Never inflate its consumed-batch budget; only reduce counts for the chosen slots.
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).chance() != 0F) {
                target = (int) Math.min(target, selected.get(i).getCount() / inputs.get(i).amount());
            }
        }
        if (target <= already) return failed(actualLimit < upperLimit ? "emi.not_enough_ingredients" : "emi.mmcr.transfer.slot_capacity");
        List<ItemStack> desired = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            RecipeDisplayEntry input = inputs.get(i);
            long count = input.amount() * (input.chance() == 0F ? 1L : target);
            if (count > Integer.MAX_VALUE) return failed("emi.mmcr.transfer.slot_capacity");
            desired.add(selected.get(i).copyWithCount((int) count));
        }
        return new Plan(List.copyOf(desired), EmiFillPreflight.check(menu, data, desired), menu.containerId,
                menu.slots.stream().map(slot -> slot.getItem().copy()).toList());
    }

    private static Optional<Component> direction(ItemBusMenu menu) {
        Minecraft client = Minecraft.getInstance();
        if (menu.owner() == null && client.level != null
                && client.level.getBlockState(menu.pos()).getBlock() instanceof IOPortBlock port
                && port.kind().ioType() != IOType.INPUT) {
            return EmiFillPreflight.failure("jei.mmcr.transfer.unsupported", port.kind().id());
        }
        return Optional.empty();
    }

    private static boolean current(EmiCraftContext<ItemBusMenu> context, ItemBusMenu menu, int containerId) {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu == menu && context.getScreen().getMenu() == menu
                && context.getScreenHandler() == menu && menu.containerId == containerId;
    }

    private static Plan failed(String key, Object... arguments) {
        return new Plan(List.of(), EmiFillPreflight.failure(key, arguments), -1, List.of());
    }

    private static boolean unchanged(ItemBusMenu menu, Plan plan) {
        if (menu.slots.size() != plan.contents().size()) return false;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (!ItemStack.matches(menu.getSlot(i).getItem(), plan.contents().get(i))) return false;
        }
        return true;
    }

    /** One native candidate selection retained through the final check and send.
     * @author howxu <dev@howxu.cn>
     */
    private record Plan(List<ItemStack> desired, Optional<Component> error, int containerId, List<ItemStack> contents) { }
}
