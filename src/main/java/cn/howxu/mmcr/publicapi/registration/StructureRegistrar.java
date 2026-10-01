package cn.howxu.mmcr.publicapi.registration;

import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
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
    void registerModifier(ResourceLocation id, ModifierBundle modifier);
    void registerModifierItem(ItemStack stack, ResourceLocation modifierId);
    Map<ResourceLocation, StructureSpec> structures();
    Map<ResourceLocation, LevelTypeSpec> levelTypes();
    Map<ResourceLocation, MachineLevelSpec> levels();
    Map<ResourceLocation, ModifierBundle> modifiers();
    Map<ResourceLocation, List<ItemStack>> modifierItems();
}
