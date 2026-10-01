package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;

import java.util.List;

/** Immutable public definition of a registered structure or recipe modifier.
 * @author howxu <dev@howxu.cn>
 */
public record ModifierDefinition(List<MachineModifier> modifiers) {
    public static final ModifierDefinition EMPTY = new ModifierDefinition(List.of());

    public static ModifierDefinition of(String target, String scope, double modifier, String operation,
            boolean affectsChance) {
        return new ModifierDefinition(List.of(MachineModifier.numeric(
                target, scope, modifier, operation, affectsChance)));
    }

    public static ModifierDefinition combine(ModifierDefinition... definitions) {
        return new ModifierDefinition(List.of(definitions).stream()
                .flatMap(definition -> definition.modifiers().stream())
                .toList());
    }

    public ModifierDefinition {
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
    }
}
