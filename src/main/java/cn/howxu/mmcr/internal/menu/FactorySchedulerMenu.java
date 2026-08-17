package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
/**
 * Single-slot thread disperser menu for factory schedulers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class FactorySchedulerMenu extends AbstractMachineMenu {

    public static final int SCHEDULER_SLOT_COUNT = 1;
    private static final int SLOT_X = 81;
    private static final int SLOT_Y = 30;

    private final FactorySchedulerBlockEntity owner;
    private final Level level;
    private final BlockPos pos;

    public FactorySchedulerMenu(int containerId, Inventory playerInv, FactorySchedulerBlockEntity owner) {
        super(ModUIs.FACTORY_SCHEDULER.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        addSchedulerSlot(owner);
        addPlayerSlots(playerInv);
    }

    public FactorySchedulerMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, (FactorySchedulerBlockEntity) null);
    }

    public FactorySchedulerMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.FACTORY_SCHEDULER.get(), containerId);
        this.owner = null;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = pos;
        addSchedulerSlot(resolvedOwner());
        addPlayerSlots(playerInv);
    }

    public static FactorySchedulerMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new FactorySchedulerMenu(containerId, playerInv, buf.readBlockPos());
    }

    public FactorySchedulerBlockEntity owner() {
        return owner;
    }

    public int schedulerSlotCount() {
        return SCHEDULER_SLOT_COUNT;
    }

    public int playerInventorySlotStart() {
        return SCHEDULER_SLOT_COUNT;
    }

    public String texturePath() {
        return "textures/gui/guifactorycontroller.png";
    }

    private void addSchedulerSlot(FactorySchedulerBlockEntity owner) {
        if (owner == null) {
            addSlot(new Slot(new SimpleContainer(1), 0, SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.THREAD_DISPERSER.get());
                }
            });
            return;
        }
        addSlot(new DirectionalItemSlot(owner.getItemStackHandler(null), 0, SLOT_X, SLOT_Y));
    }

    private FactorySchedulerBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof FactorySchedulerBlockEntity scheduler ? scheduler : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < SCHEDULER_SLOT_COUNT) {
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, SCHEDULER_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            Slot schedulerSlot = slots.get(0);
            int previousCount = stack.getCount();
            stack = schedulerSlot.safeInsert(stack);
            if (stack.getCount() == previousCount) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}
