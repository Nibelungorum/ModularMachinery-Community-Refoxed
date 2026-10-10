package cn.howxu.mmcr.internal.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.function.BooleanSupplier;

/** Player slot whose client activity follows the opening's visibility policy.
 * @author howxu <dev@howxu.cn> */
public final class ControllerPlayerSlot extends Slot {
    private final BooleanSupplier visible;

    public ControllerPlayerSlot(Container inventory, int index, int x, int y, BooleanSupplier visible) {
        super(inventory, index, x, y);
        this.visible = visible;
    }

    @Override
    public boolean isActive() {
        return visible.getAsBoolean();
    }
}
