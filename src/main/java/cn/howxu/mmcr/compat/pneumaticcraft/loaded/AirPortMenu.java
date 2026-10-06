package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.LongDataSlot;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Read-only native air state synchronized while the interface menu is open.
 * @author howxu <dev@howxu.cn>
 */
public final class AirPortMenu extends AbstractMachineMenu {
    private final AirPortBlockEntity owner;
    private final BlockPos pos;
    private final LongDataSlot air;
    private final LongDataSlot volume;
    private final LongDataSlot danger;
    private final LongDataSlot critical;

    public AirPortMenu(int containerId, Inventory inventory, AirPortBlockEntity owner) {
        this(containerId, inventory, owner, owner.getBlockPos());
    }

    private AirPortMenu(int containerId, Inventory inventory, AirPortBlockEntity owner, BlockPos pos) {
        super(ModUIs.AIR_PORT.get(), containerId);
        this.owner = owner;
        this.pos = pos;
        air = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.airHandler().getAir()));
        volume = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.airHandler().getVolume()));
        danger = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> Float.floatToIntBits(owner.airHandler().getDangerPressure())));
        critical = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> Float.floatToIntBits(owner.airHandler().getCriticalPressure())));
        addPlayerSlots(inventory);
    }

    public static AirPortMenu clientOpen(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new AirPortMenu(containerId, inventory, null, buffer.readBlockPos());
    }

    public int air() { return (int) air.value(); }
    public int volume() { return (int) volume.value(); }
    public float pressure() { return volume() > 0 ? (float) air() / volume() : 0F; }
    public float dangerPressure() { return Float.intBitsToFloat((int) danger.value()); }
    public float criticalPressure() { return Float.intBitsToFloat((int) critical.value()); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || owner.getLevel() != null
                && owner.getLevel().getBlockEntity(pos) == owner
                && MenuSupport.stillValidWithin(player, pos);
    }
}
