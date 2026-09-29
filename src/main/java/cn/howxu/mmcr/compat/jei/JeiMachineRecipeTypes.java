package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JEI recipe type constants for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiMachineRecipeTypes {

    public static final RecipeType<MachineStructureDisplay> STRUCTURE = RecipeType.create(
            MMCR.MODID, "multiblock_structure", MachineStructureDisplay.class);
    private static final Map<ResourceLocation, RecipeType<MachineRecipeDisplay>> TYPES = new ConcurrentHashMap<>();

    public static RecipeType<MachineRecipeDisplay> forPool(ResourceLocation poolId) {
        return TYPES.computeIfAbsent(poolId, id -> RecipeType.create(
                id.getNamespace(), "recipe_pool/" + id.getPath(),
                MachineRecipeDisplay.class));
    }

    private JeiMachineRecipeTypes() {
    }
}
