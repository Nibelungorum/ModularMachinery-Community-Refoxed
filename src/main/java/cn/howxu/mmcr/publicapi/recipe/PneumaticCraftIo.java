package cn.howxu.mmcr.publicapi.recipe;

import cn.howxu.mmcr.internal.api.facade.recipe.PneumaticCraftIoAdapters;
import java.util.List;

/** Optional-mod-safe per-tick air recipe declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticCraftIo {
    private PneumaticCraftIo() {}
    public static CustomIoSpec airInput(long airPerTick, float minPressure) { return airInput(airPerTick, minPressure, List.of()); }
    public static CustomIoSpec airInput(long airPerTick, float minPressure, List<String> tags) { return PneumaticCraftIoAdapters.airInput(airPerTick, minPressure, tags); }
    public static CustomIoSpec airOutput(long airPerTick) { return airOutput(airPerTick, List.of()); }
    public static CustomIoSpec airOutput(long airPerTick, List<String> tags) { return PneumaticCraftIoAdapters.airOutput(airPerTick, tags); }
}
