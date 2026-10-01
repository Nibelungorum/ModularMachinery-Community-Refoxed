package cn.howxu.mmcr.publicapi.client.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided workstation association; stack getters return copies.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface Workstation {
    /** Item registry association.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface RecipePoolItem extends Workstation {
        ResourceLocation recipePoolId();
        ResourceLocation itemId();
    }
    /** Component-bearing stack association.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface RecipePoolStack extends Workstation {
        ResourceLocation recipePoolId();
        ItemStack workstation();
    }
    /** A machine controller associated with a JEI recipe type ID.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface Machine extends Workstation {
        ResourceLocation machineId();
        ResourceLocation recipeTypeId();
    }
}
