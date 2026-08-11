package cn.howxu.mmcr.api.machine.level;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Immutable startup declaration for one machine level.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineLevel(Identifier id, Identifier typeId, int priority,
                           BlockPredicate statePredicate, ItemStack representative,
                           LevelModifier modifier) {
    public MachineLevel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(statePredicate, "statePredicate");
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(modifier, "modifier");
        representative = representative.copy();
    }

    @Override
    public ItemStack representative() {
        return representative.copy();
    }
}
