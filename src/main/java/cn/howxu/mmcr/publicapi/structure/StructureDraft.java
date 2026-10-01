package cn.howxu.mmcr.publicapi.structure;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Configuration handle supplied by Structures; not implemented by consumers.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureDraft {
    StructureDraft stateSensitive();
    StructureDraft stateInsensitive();
    StructureDraft fullStructure(Consumer<StructureStageDraft> configure);
    StructureDraft expandStructure(Consumer<StructureStageDraft> configure);
    StructureDraft extension(Consumer<StructureStageDraft> configure);
    /** Configures one FULL stage; the callback declares its controller symbol. */
    StructureDraft singlePattern(Consumer<PatternDraft> configure);
    StructureSpec build(ResourceLocation machineId);
}
