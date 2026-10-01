package cn.howxu.mmcr.publicapi.structure;

import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import java.util.Map;
import java.util.OptionalInt;
import org.jetbrains.annotations.ApiStatus;

/** Port count constraints produced by the facade; not a consumer SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface PortLimits {
    static PortLimits none() { return StructureAdapters.noPorts(); }
    static Builder builder() { return StructureAdapters.ports(); }
    Map<String, CountRangeView> requirements();

    /** Factory-owned count range.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface CountRangeView {
        int min();
        OptionalInt max();
    }

    /** Factory-owned configuration handle.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface Builder {
        Builder min(String portId, int min);
        Builder range(String portId, int min, int max);
        PortLimits build();
    }
}
