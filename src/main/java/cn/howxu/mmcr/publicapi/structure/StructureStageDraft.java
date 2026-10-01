package cn.howxu.mmcr.publicapi.structure;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Immediate stage configuration handle; not implemented by consumers.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureStageDraft {
    StructureStageDraft full();
    StructureStageDraft expansion();
    StructureStageDraft extension();
    StructureStageDraft pattern(Consumer<PatternDraft> configure);
    StructureStageDraft ports(Consumer<PortLimits.Builder> configure);
    StructureStageDraft portTiers(Consumer<PortTierLimits.Builder> configure);
    /** Repeated calls append to the same constraint builder. */
    StructureStageDraft requirements(Consumer<StructureConstraints.Builder> configure);
    StructureStageDraft modifier(char symbol, ResourceLocation modifierId, BlockCondition replacement);
    StructureStageSpec build();
}
