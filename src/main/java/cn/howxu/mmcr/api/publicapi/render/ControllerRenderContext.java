package cn.howxu.mmcr.api.publicapi.render;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable state supplied to a machine controller renderer.
 * @author howxu <dev@howxu.cn>
 */
public record ControllerRenderContext(
        BlockPos controllerPos,
        Identifier machineId,
        @Nullable Direction facing,
        StructureView structure,
        CraftingView crafting,
        Map<String, DataValue> dataStorageValues,
        int lightCoords,
        float partialTick) {

    public ControllerRenderContext {
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        machineId = Objects.requireNonNull(machineId, "machineId");
        structure = Objects.requireNonNull(structure, "structure");
        crafting = Objects.requireNonNull(crafting, "crafting");
        dataStorageValues = dataStorageValues == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dataStorageValues));
    }

    /** Render-safe formation state published by a controller.
     * @author howxu <dev@howxu.cn>
     */
    public record StructureView(boolean formed, boolean structureAreaLoaded, int matchedStage) {
    }

    /** Render-safe crafting state published by a controller.
     * @author howxu <dev@howxu.cn>
     */
    public record CraftingView(
            @Nullable Identifier recipeId,
            CraftingStatus.Status status,
            String statusMessage,
            @Nullable ExecutionStatus failure,
            int tick,
            int totalTick,
            long parallelism,
            long maxParallelism,
            boolean recipeLocked,
            String lockedRecipeId) {
        public CraftingView {
            status = Objects.requireNonNull(status, "status");
            statusMessage = statusMessage == null ? "" : statusMessage;
            failure = copyFailure(failure);
            lockedRecipeId = lockedRecipeId == null ? "" : lockedRecipeId;
        }

        private static @Nullable ExecutionStatus copyFailure(@Nullable ExecutionStatus failure) {
            return failure == null ? null : new ExecutionStatus(failure.id(), failure.severity(), failure.source(), failure.details());
        }
    }
}
