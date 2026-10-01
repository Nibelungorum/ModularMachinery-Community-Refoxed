package cn.howxu.mmcr.api.machine.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Public declaration of one machine level.
 * @author howxu <dev@howxu.cn>
 */
public record MachineLevel(ResourceLocation id, ResourceLocation typeId, int priority,
                           BlockPredicate statePredicate, DisplayStack representative,
                            ModifierDefinition modifier) {
    public MachineLevel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(statePredicate, "statePredicate");
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(modifier, "modifier");
    }
}
