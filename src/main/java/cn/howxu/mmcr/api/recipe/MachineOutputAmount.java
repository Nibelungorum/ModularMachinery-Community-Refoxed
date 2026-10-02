package cn.howxu.mmcr.api.recipe;

import java.util.Objects;

/**
 * A machine output template paired with its aggregated quantity.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineOutputAmount(MachineOutput output, long amount) {
    public MachineOutputAmount {
        output = MachineOutput.copyOf(Objects.requireNonNull(output, "output"));
        if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
    }

    @Override
    public MachineOutput output() {
        return MachineOutput.copyOf(output);
    }
}
