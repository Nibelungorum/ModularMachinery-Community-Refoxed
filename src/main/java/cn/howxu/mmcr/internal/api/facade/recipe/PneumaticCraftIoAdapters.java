package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import java.util.List;

/** Delegates air declarations to neutral codecs without loading PneumaticCraft classes.
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticCraftIoAdapters {
    private PneumaticCraftIoAdapters() {}
    public static CustomIoSpec airInput(long airPerTick, float minPressure, List<String> tags) {
        return IoValues.customIo(PneumaticIds.AIR, IoDirection.INPUT,
                MachineRecipeBuilder.airInputPayload(airPerTick, minPressure, tags));
    }
    public static CustomIoSpec airOutput(long airPerTick, List<String> tags) {
        return IoValues.customIo(PneumaticIds.AIR, IoDirection.OUTPUT,
                MachineRecipeBuilder.airOutputPayload(airPerTick, tags));
    }
}
