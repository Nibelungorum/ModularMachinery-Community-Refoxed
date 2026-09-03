package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.network.RequestBody;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies request callback registration consumed by the dispatcher.
 *
 * @author howxu <dev@howxu.cn>
 */
class NetworkRequestDispatcherTest {
    @Test
    void requestHandlersPreserveRegistrationOrderAndRejectDuplicates() {
        Identifier first = Identifier.parse("mmcr:first");
        Identifier second = Identifier.parse("mmcr:second");
        var builder = MachineBuilder.machine(Identifier.parse("mmcr:request_test"))
                .requestProcess(first, (body, request, sender, receiver) -> { })
                .requestProcess(second, (body, request, sender, receiver) -> { });

        assertEquals(List.of(first, second), List.copyOf(builder.build().requestProcessors().keySet()));
        assertThrows(IllegalArgumentException.class,
                () -> builder.requestProcess(first, (body, request, sender, receiver) -> { }));
    }

    @Test
    void pendingRequestKeepsTheSuppliedImmutableBodyInstance() {
        Map<String, DataValue> values = new LinkedHashMap<>();
        values.put("nested", DataValue.map(Map.of("value", DataValue.of(1))));
        RequestBody body = RequestBody.of(values);

        assertEquals(1, body.get("nested").orElseThrow().asMap().orElseThrow().get("value").intValue());
    }
}
