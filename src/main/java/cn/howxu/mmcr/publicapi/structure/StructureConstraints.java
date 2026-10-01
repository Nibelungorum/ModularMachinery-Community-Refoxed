package cn.howxu.mmcr.publicapi.structure;

import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Factory-owned modifier placements and level slots; not a consumer SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureConstraints {
    static StructureConstraints empty() { return StructureAdapters.emptyConstraints(); }
    static Builder builder() { return StructureAdapters.constraints(); }
    Map<Character, List<ModifierPlacementView>> modifierReplacements();
    Map<Character, ResourceLocation> levelSlots();

    /** Factory-owned placement view.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface ModifierPlacementView {
        ResourceLocation modifierId();
        BlockCondition replacement();
    }

    /** Factory-owned configuration handle.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface Builder {
        Builder modifier(char symbol, ResourceLocation modifierId);
        Builder modifier(char symbol, ResourceLocation modifierId, BlockCondition replacement);
        Builder levelSlot(char symbol, ResourceLocation typeId);
        StructureConstraints build();
    }
}
