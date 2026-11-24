package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantic;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.server.level.ServerLevel;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternLogicKind;

/**
 * Retains native pattern-provider behavior under an MMCR menu type.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceMenu extends PatternProviderMenu {
    public static final int REFUND_HOSTED = 9003;
    private final InputIsolationMenuState inputIsolation;
    private final DataSlot hostedContents = new DataSlot() {
        private int synced;
        @Override public int get() {
            if (getBlockEntity() instanceof PatternInterfaceBlockEntity owner && owner.getLevel() instanceof ServerLevel)
                return owner.hasGroupHostedMaterials() ? 1 : 0;
            return synced;
        }
        @Override public void set(int value) { synced = value; }
    };

    public boolean hasHostedMaterials() { return hostedContents.get() != 0; }
    public PatternInterfaceMenu(MenuType<? extends PatternInterfaceMenu> type, int id,
                                Inventory inventory, PatternProviderLogicHost host) {
        super(type, id, inventory, host);
        inputIsolation = InputIsolationMenuState.forPort(host instanceof BlockEntity port ? port : null);
        addDataSlot(inputIsolation.dataSlot());
        addDataSlot(hostedContents);
    }

    public InputIsolationMenuState inputIsolation() { return inputIsolation; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == REFUND_HOSTED) {
            if (!(getBlockEntity() instanceof PatternInterfaceBlockEntity owner) || !(owner.getLevel() instanceof ServerLevel server)
                    || player.containerMenu != this || player.isSpectator() || player.level() != server || owner.isRemoved()
                    || !server.hasChunkAt(owner.getBlockPos()) || server.getBlockEntity(owner.getBlockPos()) != owner
                    || ((PatternLogicKind) owner.kind()).readOnlyPatterns() || !stillValid(player)) return false;
            owner.requestGroupHostedRefund();
            return true;
        }
        if (buttonId == InputIsolationMenuState.DISABLE || buttonId == InputIsolationMenuState.ENABLE) {
            return inputIsolation.handleClick(this, player, buttonId);
        }
        return super.clickMenuButton(player, buttonId);
    }

    @Override
    protected Slot addSlot(Slot slot, SlotSemantic semantic) {
        if (slot instanceof AppEngSlot aeSlot && aeSlot.getInventory() instanceof ConfigMenuInventory wrapper
                && wrapper.getDelegate() instanceof PatternReturnInventory) {
            slot = new PatternReturnSlot(wrapper, aeSlot.getSlotIndex());
        }
        return super.addSlot(slot, semantic);
    }
}
