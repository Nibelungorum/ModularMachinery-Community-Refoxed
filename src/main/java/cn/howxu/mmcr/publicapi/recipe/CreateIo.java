package cn.howxu.mmcr.publicapi.recipe;

import cn.howxu.mmcr.internal.api.facade.recipe.CreateIoAdapters;
import java.util.List;

/** Optional-mod-safe stress recipe declarations, measured in SU and RPM.
 * @author howxu <dev@howxu.cn>
 */
public final class CreateIo {
    private CreateIo() {}
    public static CustomIoSpec stressInput(double stress, double minRpm) { return stressInput(stress, minRpm, List.of()); }
    public static CustomIoSpec stressInput(double stress, double minRpm, List<String> tags) { return CreateIoAdapters.stressInput(stress, minRpm, tags); }
    public static CustomIoSpec stressOutput(double stress, double rpm) { return stressOutput(stress, rpm, List.of()); }
    public static CustomIoSpec stressOutput(double stress, double rpm, List<String> tags) { return CreateIoAdapters.stressOutput(stress, rpm, tags); }
}
