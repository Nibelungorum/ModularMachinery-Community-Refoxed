package cn.howxu.mmcr.api.publicapi.render;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests immutable controller renderer context state.
 * @author howxu <dev@howxu.cn>
 */
class ControllerRenderContextTest {
    @Test
    void contextCopiesAndLocksDataStorageValues() {
        Map<String, DataValue> source = new LinkedHashMap<>();
        source.put("energy", DataValue.of(42L));
        ControllerRenderContext context = new ControllerRenderContext(
                BlockPos.ZERO, Identifier.fromNamespaceAndPath("test", "machine"), Direction.NORTH,
                new ControllerRenderContext.StructureView(false, true, 0),
                new ControllerRenderContext.CraftingView(null, CraftingStatus.Status.IDLE, "", null,
                        0, 0, 0L, 1L),
                source, 15728880, 0.5F);

        source.put("changed", DataValue.of(true));

        assertEquals(Set.of("energy"), context.dataStorageValues().keySet());
        assertThrows(UnsupportedOperationException.class,
                () -> context.dataStorageValues().put("write", DataValue.of(1)));
    }

    @Test
    void contextCopyPreservesTypedFailureReasonAndTrace() {
        Identifier reasonId = Identifier.fromNamespaceAndPath("test", "missing_input");
        Identifier source = Identifier.fromNamespaceAndPath("test", "item_bus");
        Identifier recipeId = Identifier.fromNamespaceAndPath("test", "recipe");
        FailureReason reason = new FailureReason(reasonId, "gui.test.failure.missing_input", 10);
        FailureOccurrence occurrence = FailureOccurrence.at(reason, source, FailurePhase.CAPABILITY_COMMIT,
                recipeId, 2, Map.of("available", "0"));
        ExecutionStatus failure = ExecutionStatus.blocked(
                Identifier.fromNamespaceAndPath("test", "blocked"), source, occurrence);

        ControllerRenderContext context = new ControllerRenderContext(
                BlockPos.ZERO, Identifier.fromNamespaceAndPath("test", "machine"), Direction.NORTH,
                new ControllerRenderContext.StructureView(true, true, 1),
                new ControllerRenderContext.CraftingView(recipeId, CraftingStatus.Status.NO_RECIPE, "", failure,
                        1, 20, 1L, 1L),
                Map.of(), 15728880, 0.5F);

        ExecutionStatus copied = context.crafting().failure();
        assertEquals(reason, copied.reason());
        assertEquals(occurrence.trace(), copied.failure().trace());
        assertEquals(recipeId, copied.failure().trace().frames().getFirst().recipeId());
        assertEquals("0", copied.details().get("available"));
    }
}
