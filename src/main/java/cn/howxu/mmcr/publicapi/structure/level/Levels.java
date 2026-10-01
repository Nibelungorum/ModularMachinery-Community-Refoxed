package cn.howxu.mmcr.publicapi.structure.level;

import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Factories for level declarations; registry lifecycle belongs to registration.
 * @author howxu <dev@howxu.cn>
 */
public final class Levels {
    private Levels() {}
    public static LevelTypeSpec type(ResourceLocation id, Component displayName) {
        return StructureAdapters.levelType(id, displayName);
    }
    public static MachineLevelSpec level(ResourceLocation id, ResourceLocation typeId, int priority,
            BlockCondition condition, ItemStack representative, ModifierBundle modifier) {
        return StructureAdapters.level(id, typeId, priority, condition, representative, modifier);
    }
}
