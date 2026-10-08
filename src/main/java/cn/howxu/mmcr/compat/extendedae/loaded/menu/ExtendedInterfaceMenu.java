package cn.howxu.mmcr.compat.extendedae.loaded.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantic;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigMenuInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.InterfaceMenuSlots;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InterfaceMenuPageHost;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Adds host-local policies and stable two-page interaction to the native extended interface menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedInterfaceMenu extends ContainerExInterface {
    private int visiblePage = -1;

    public ExtendedInterfaceMenu(MenuType<? extends ExtendedInterfaceMenu> type, int id,
                                 Inventory inventory, InterfaceLogicHost host) {
        super(type, id, inventory, host);
        page = host instanceof InterfaceMenuPageHost paged ? paged.getInterfaceMenuPage() : 0;
        showPage(page);
    }

    @Override
    protected Slot addSlot(Slot slot, SlotSemantic semantic) {
        return super.addSlot(InterfaceMenuSlots.adapt(getTarget(), slot), semantic);
    }

    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        if (slot < 0 || slot >= slots.size()) return;
        if (InterfaceMenuSlots.allowsAction(getTarget(), action, getSlot(slot))) {
            super.doAction(player, action, slot, id);
        }
    }

    @Override
    public void openSetAmountMenu(int configSlot) {
        if (InterfaceMenuSlots.canSetAmount(getHost(), configSlot) && configSlot / 18 == page) {
            super.openSetAmountMenu(configSlot);
        }
    }

    @Override
    public void setPage(int requestedPage) {
        page = Math.clamp(requestedPage, 0, 1);
        if (isServerSide() && getHost() instanceof InterfaceMenuPageHost host) {
            host.setInterfaceMenuPage(page);
        }
        showPage(page);
    }

    @Override
    public void showPage(int requestedPage) {
        int visiblePage = Math.clamp(requestedPage, 0, 1);
        if (this.visiblePage != visiblePage) {
            // Vanilla retains drag targets, including a single-target private doClick recursion.
            resetQuickCraft();
            this.visiblePage = visiblePage;
        }
        var logic = getHost().getInterfaceLogic();
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot aeSlot
                    && aeSlot.getInventory() instanceof ConfigMenuInventory wrapper
                    && (wrapper.getDelegate() == logic.getConfig()
                    || wrapper.getDelegate() == logic.getStorage())) {
                aeSlot.setActive(aeSlot.getContainerSlot() / 18 == visiblePage);
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && getHost() instanceof InterfaceMenuPageHost host) {
            page = host.getInterfaceMenuPage();
        }
        showPage(page);
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slot, int button, ClickType clickType, Player player) {
        if (slot >= 0 && slot < slots.size() && !getSlot(slot).isActive()) return;
        super.clicked(slot, button, clickType, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.isActive() && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.isActive() && super.canDragTo(slot);
    }

    @Override
    public void setFilter(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slots.size() || !getSlot(slot).isActive()) return;
        super.setFilter(slot, stack);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        if (slot < 0 || slot >= slots.size() || !getSlot(slot).isActive()) return ItemStack.EMPTY;
        var source = getSlot(slot);
        var stack = source.getItem();
        if (isServerSide() && isPlayerSideSlot(source) && source.mayPickup(player) && !stack.isEmpty()
                && getQuickMoveDestinationSlots(stack, true).isEmpty()) {
            // AE2's filter fallback bypasses destination validation and visits hidden slots too.
            for (Slot candidate : slots) {
                if (candidate instanceof FakeSlot fakeSlot && candidate.isActive()
                        && !isPlayerSideSlot(candidate) && fakeSlot.canSetFilterTo(stack)) {
                    if (ItemStack.isSameItemSameComponents(candidate.getItem(), stack)) break;
                    if (!candidate.hasItem()) {
                        candidate.set(stack.copy());
                        broadcastChanges();
                        break;
                    }
                }
            }
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, slot);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot slot, ItemStack stack, boolean fromPlayerSide) {
        return slot.isActive() && super.isValidQuickMoveDestination(slot, stack, fromPlayerSide);
    }
}
