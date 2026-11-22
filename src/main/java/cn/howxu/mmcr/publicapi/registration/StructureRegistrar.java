package cn.howxu.mmcr.publicapi.registration;

import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.BlockStackingMode;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided shared Java/KJS structure registration window.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureRegistrar {
    void registerStructure(ResourceLocation machineId, Consumer<StructureDraft> configuration);
    void registerStructure(StructureSpec structure);
    void registerLevelType(LevelTypeSpec type);
    void registerLevel(MachineLevelSpec level);
    /** Legacy: one structure contribution plus the stacked Upgrade Bus contribution. */
    void registerModifier(ResourceLocation id, ModifierBundle modifier);
    /** When enabled, blocks use additive stacking; otherwise each property takes one source's better result. */
    default void registerModifier(ResourceLocation id, ModifierBundle modifier, boolean stackable) {
        registerModifier(id, modifier, stackable, BlockStackingMode.ADD);
    }
    /** Disabling stacking limits this ID across both structure blocks and upgrade items. */
    default void registerModifier(ResourceLocation id, ModifierBundle modifier, boolean stackable, BlockStackingMode blockMode) {
        throw new UnsupportedOperationException("This registrar does not support explicit modifier stacking");
    }
    void registerModifierItem(ResourceLocation itemId, ResourceLocation modifierId);
    /** Caps the combined effective item count for this modifier across one machine's upgrade buses. */
    default void limitModifierItems(ResourceLocation modifierId, int maxCount) {
        throw new UnsupportedOperationException("This registrar does not support modifier item limits");
    }
    Map<ResourceLocation, StructureSpec> structures();
    Map<ResourceLocation, LevelTypeSpec> levelTypes();
    Map<ResourceLocation, MachineLevelSpec> levels();
    Map<ResourceLocation, ModifierBundle> modifiers();
    Map<ResourceLocation, List<ItemStack>> modifierItems();
    Map<ResourceLocation, ResourceLocation> modifierItemIds();
}
