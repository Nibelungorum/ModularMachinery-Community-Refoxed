package cn.howxu.mmcr.internal.menu;

import java.util.function.LongSupplier;
import net.minecraft.world.inventory.DataSlot;

/**
 * Synchronizes an unsigned pair of 32-bit menu data slots as one {@code long}.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongDataSlot {
    private final LongSupplier source;
    private long clientValue;
    private final DataSlot low = new DataSlot() {
        @Override
        public int get() {
            return (int) value();
        }

        @Override
        public void set(int value) {
            clientValue = (clientValue & 0xFFFFFFFF00000000L) | Integer.toUnsignedLong(value);
        }
    };
    private final DataSlot high = new DataSlot() {
        @Override
        public int get() {
            return (int) (value() >>> 32);
        }

        @Override
        public void set(int value) {
            clientValue = (clientValue & 0xFFFFFFFFL) | ((long) value << 32);
        }
    };

    public LongDataSlot(LongSupplier source) {
        this.source = source;
    }

    public static LongDataSlot standalone() {
        return new LongDataSlot(null);
    }

    DataSlot low() {
        return low;
    }

    DataSlot high() {
        return high;
    }

    public long value() {
        return source == null ? clientValue : source.getAsLong();
    }
}
