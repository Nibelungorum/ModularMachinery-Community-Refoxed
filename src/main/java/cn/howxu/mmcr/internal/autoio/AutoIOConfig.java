package cn.howxu.mmcr.internal.autoio;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;
import java.util.Set;

/**
 * Persistent Auto IO state for one IO port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AutoIOConfig {
    private static final String ENABLED_KEY = "enabled";
    private static final String SIDES_KEY = "sides";

    private boolean enabled;
    private final EnumSet<Direction> enabledSides = EnumSet.noneOf(Direction.class);

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Direction> enabledSides() {
        return Set.copyOf(enabledSides);
    }

    public boolean isSideEnabled(Direction side) {
        return enabledSides.contains(side);
    }

    public void toggleSide(Direction side) {
        if (enabledSides.contains(side)) enabledSides.remove(side);
        else enabledSides.add(side);
    }

    public void save(ValueOutput output) {
        output.putBoolean(ENABLED_KEY, enabled);
        output.putInt(SIDES_KEY, toMask(enabledSides));
    }

    public void loadInto(ValueInput input) {
        enabled = input.getBooleanOr(ENABLED_KEY, false);
        enabledSides.clear();
        enabledSides.addAll(fromMask(input.getIntOr(SIDES_KEY, 0)));
    }

    public static AutoIOConfig load(ValueInput input) {
        AutoIOConfig config = new AutoIOConfig();
        config.loadInto(input);
        return config;
    }

    public static int toMask(Set<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    public static EnumSet<Direction> fromMask(int mask) {
        EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }
}
