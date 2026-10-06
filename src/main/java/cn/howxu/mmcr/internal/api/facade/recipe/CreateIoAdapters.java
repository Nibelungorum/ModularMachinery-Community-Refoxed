package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import java.util.List;

/** Delegates stress declarations to neutral codecs without loading Create classes.
 * @author howxu <dev@howxu.cn>
 */
public final class CreateIoAdapters {
    private CreateIoAdapters() {}
    public static CustomIoSpec stressInput(double stress, double minRpm, List<String> tags) {
        return IoValues.customIo(CreateRecipeTypes.STRESS, IoDirection.INPUT,
                MachineRecipeBuilder.stressInputPayload(stress, minRpm, tags));
    }
    public static CustomIoSpec stressOutput(double stress, double rpm, List<String> tags) {
        return IoValues.customIo(CreateRecipeTypes.STRESS, IoDirection.OUTPUT,
                MachineRecipeBuilder.stressOutputPayload(stress, rpm, tags));
    }
}
