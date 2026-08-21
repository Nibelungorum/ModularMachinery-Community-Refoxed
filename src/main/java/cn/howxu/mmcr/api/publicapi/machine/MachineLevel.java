package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Public declaration of one machine level.
 * @author howxu <dev@howxu.cn>
 */
public record MachineLevel(Identifier id, Identifier typeId, int priority,
                           BlockPredicate statePredicate, DisplayStack representative,
                           LevelModifier modifier) {
    public MachineLevel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(statePredicate, "statePredicate");
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(modifier, "modifier");
    }
}
