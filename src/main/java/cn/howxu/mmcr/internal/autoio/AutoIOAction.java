package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side mutation requested by the Auto IO control page.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum AutoIOAction {
    SET_ENABLED {
        @Override
        public void apply(IOPortBlockEntity port, CapabilityType type, @Nullable Direction side, boolean enabled) {
            port.setAutoIOEnabled(type, enabled);
        }
    },
    SET_SIDE {
        @Override
        public void apply(IOPortBlockEntity port, CapabilityType type, @Nullable Direction side, boolean enabled) {
            port.setAutoIOSide(type, side, enabled);
        }

        @Override
        public boolean requiresSide() {
            return true;
        }
    },
    SET_ALL_SIDES {
        @Override
        public void apply(IOPortBlockEntity port, CapabilityType type, @Nullable Direction side, boolean enabled) {
            port.setAllAutoIOSides(type, enabled);
        }
    };

    public abstract void apply(IOPortBlockEntity port, CapabilityType type, @Nullable Direction side, boolean enabled);

    public boolean requiresSide() {
        return false;
    }
}
