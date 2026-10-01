package cn.howxu.mmcr.publicapi.client.render;

import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.runtime.RuntimeFailure;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Read-only render-safe published state, supplied by MMCR only during a Java render callback.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface ControllerRenderContext {
    BlockPos controllerPos();
    ResourceLocation machineId();
    @Nullable Direction facing();
    StructureView structure();
    CraftingView crafting();
    Map<String, DataKey> dataStorageValues();
    int lightCoords();
    float partialTick();

    /** Published formation state.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface StructureView {
        boolean formed();
        boolean structureAreaLoaded();
        int matchedStage();
    }

    /** Published recipe state; statusMessage is a translation key.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface CraftingView {
        @Nullable ResourceLocation recipeId();
        Status status();
        String statusMessage();
        @Nullable RuntimeFailure failure();
        int tick();
        int totalTick();
        long parallelism();
        long maxParallelism();
    }

    /** Render-facing crafting phase.
     * @author howxu <dev@howxu.cn>
     */
    enum Status { IDLE, CRAFTING, MISSING_STRUCTURE, CHUNK_UNLOADED, NO_RECIPE, PAUSED }
}
