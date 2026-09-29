package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JEI recipe type constants for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiMachineRecipeTypes {

    public static final IRecipeType<MachineStructureDisplay> STRUCTURE = IRecipeType.create(
            MMCR.id("multiblock_structure"), MachineStructureDisplay.class);
    private static final Map<ResourceLocation, IRecipeType<MachineRecipeDisplay>> TYPES = new ConcurrentHashMap<>();

    public static IRecipeType<MachineRecipeDisplay> forPool(ResourceLocation poolId) {
        return TYPES.computeIfAbsent(poolId, id -> IRecipeType.create(
                id.withPath("recipe_pool/" + id.getPath()),
                MachineRecipeDisplay.class));
    }

    private JeiMachineRecipeTypes() {
    }
}
