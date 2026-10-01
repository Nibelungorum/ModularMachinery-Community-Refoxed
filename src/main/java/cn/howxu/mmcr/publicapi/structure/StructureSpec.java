package cn.howxu.mmcr.publicapi.structure;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Read-only declaration produced by the structure factory; not a consumer SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureSpec {
    ResourceLocation machineId();
    List<StructureStageSpec> stages();
    boolean stateSensitive();
}
