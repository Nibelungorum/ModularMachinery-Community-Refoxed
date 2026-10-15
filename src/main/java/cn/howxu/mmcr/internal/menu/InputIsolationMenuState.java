package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Server-owned isolation settings shared by native and AE menus.
 * @author howxu <dev@howxu.cn>
 */
public final class InputIsolationMenuState {
    public static final int DISABLE = 9001;
    public static final int ENABLE = 9002;
    private final @Nullable BlockEntity owner;
    private int flags;
    private final DataSlot dataSlot = new DataSlot() {
        @Override public int get() {
            if (owner != null && owner.getLevel() != null && owner.getLevel().isClientSide()) return flags;
            if (owner instanceof IOPortBlockEntity port) {
                return (port.supportsInputIsolation() ? 1 : 0) | (port.isInputIsolated() ? 2 : 0);
            }
            if (owner instanceof MachineControllerBlockEntity controller) {
                return 1 | (controller.isFullyInputIsolated() ? 2 : 0);
            }
            return flags;
        }
        @Override public void set(int value) { flags = value & 3; }
    };

    private InputIsolationMenuState(@Nullable BlockEntity owner) {
        this.owner = owner != null && owner.getLevel() != null && owner.getLevel().isClientSide() ? null : owner;
    }

    public static InputIsolationMenuState forPort(@Nullable IOPortBlockEntity owner) {
        return new InputIsolationMenuState(owner);
    }

    public static InputIsolationMenuState forController(@Nullable MachineControllerBlockEntity owner) {
        return new InputIsolationMenuState(owner);
    }

    public DataSlot dataSlot() { return dataSlot; }
    public boolean supported() { return (dataSlot.get() & 1) != 0; }
    public boolean enabled() { return (dataSlot.get() & 2) != 0; }

    public boolean handleClick(AbstractContainerMenu menu, Player player, int buttonId) {
        if (buttonId != DISABLE && buttonId != ENABLE) return false;
        if (owner == null || owner.getLevel() == null || owner.getLevel().isClientSide()
                || player.containerMenu != menu || player.isSpectator()
                || player.level() != owner.getLevel() || owner.isRemoved()
                || owner.getLevel().getBlockEntity(owner.getBlockPos()) != owner
                || !menu.stillValid(player) || !supported()) return false;
        boolean enabled = buttonId == ENABLE;
        if (owner instanceof IOPortBlockEntity port) port.setInputIsolated(enabled);
        else if (owner instanceof MachineControllerBlockEntity controller) controller.setFullyInputIsolated(enabled);
        return true;
    }
}
