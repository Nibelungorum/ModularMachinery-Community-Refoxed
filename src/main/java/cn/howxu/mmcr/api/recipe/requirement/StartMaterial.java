package cn.howxu.mmcr.api.recipe.requirement;

import net.minecraft.resources.Identifier;

/** A material whose input payment (or retention) is settled at admission and persisted by index.
 * @author howxu <dev@howxu.cn>
 */
public interface StartMaterial extends MachineRequirement {

    @Override
    default boolean usesInputScope(Identifier capabilityId) { return io().isInput(); }

    @Override
    default boolean paysAtStart() { return io().isInput(); }

    @Override
    default boolean isCatalyst() { return paysAtStart() && consumeChance() == 0F; }
}
