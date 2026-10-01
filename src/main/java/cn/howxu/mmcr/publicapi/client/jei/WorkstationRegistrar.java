package cn.howxu.mmcr.publicapi.client.jei;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided workstation registration window; no JEI dependency is needed to contribute.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface WorkstationRegistrar {
    void addRecipePoolWorkstation(ResourceLocation poolId, ResourceLocation itemId);
    void addRecipePoolWorkstation(ResourceLocation poolId, ItemLike workstation);
    void addRecipePoolWorkstation(ResourceLocation poolId, ItemStack workstation);
    void addMachineWorkstation(ResourceLocation machineId, ResourceLocation recipeTypeId);
    List<Workstation> entries();
}
