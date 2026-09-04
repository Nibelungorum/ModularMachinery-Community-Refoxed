package cn.howxu.mmcr.api.publicapi.render;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
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
                null, BlockPos.ZERO, Identifier.fromNamespaceAndPath("test", "machine"),
                Direction.NORTH, StructureSnapshot.empty(),
                CraftingStateSnapshot.empty(0L, 0L, 0L), source, 15728880, 0.5F);

        source.put("changed", DataValue.of(true));

        assertEquals(Set.of("energy"), context.dataStorageValues().keySet());
        assertThrows(UnsupportedOperationException.class,
                () -> context.dataStorageValues().put("write", DataValue.of(1)));
    }
}
