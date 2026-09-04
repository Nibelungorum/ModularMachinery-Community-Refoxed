package cn.howxu.mmcr.api.publicapi.render;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable state supplied to a machine controller renderer.
 * @author howxu <dev@howxu.cn>
 */
public record ControllerRenderContext(
        @Nullable Level level,
        BlockPos controllerPos,
        Identifier machineId,
        @Nullable Direction facing,
        StructureSnapshot structure,
        CraftingStateSnapshot crafting,
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
}
