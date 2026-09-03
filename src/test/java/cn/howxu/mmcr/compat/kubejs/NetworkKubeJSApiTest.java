package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.network.NetworkInterfaceReference;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class NetworkKubeJSApiTest {
    private final KubeJSApi api = new KubeJSApi();

    @Test
    void facade_exposes_network_methods_and_keeps_world_state_out_of_the_api() throws Exception {
        assertThat(KubeJSApi.class.getMethod("networkInterfaces", MachineBehaviorContext.class)).isNotNull();
        assertThat(KubeJSApi.class.getMethod("sendRequest", NetworkInterfaceReference.class,
                MachineReference.class, String.class, Object.class)).isNotNull();
        assertThat(Arrays.stream(KubeJSApi.class.getDeclaredFields())
                .map(Field::getType)
                .noneMatch(type -> Level.class.isAssignableFrom(type)
                        || MinecraftServer.class.isAssignableFrom(type)
                        || ServerLevel.class.isAssignableFrom(type)
                        || MachineControllerBlockEntity.class.isAssignableFrom(type)
                        || NetworkInterfaceReference.class.isAssignableFrom(type))).isTrue();
    }

    @Test
    void converts_nested_maps_collections_arrays_and_scalar_values() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("identifier", MMCR.id("nested").toString());
        nested.put("values", List.of(1, 2L, 3.5F, true, new BigInteger("7"), new BigDecimal("8.25")));
        nested.put("array", new Object[] {"text", List.of(false, 4.5D)});

        DataValue result = convert(nested);

        assertThat(result).isEqualTo(DataValue.map(Map.of(
                "identifier", DataValue.of("mmcr:nested"),
                "values", DataValue.list(List.of(DataValue.of(1), DataValue.of(2L), DataValue.of(3.5F),
                        DataValue.of(true), DataValue.of(new BigInteger("7")), DataValue.of(new BigDecimal("8.25")))),
                "array", DataValue.list(List.of(DataValue.of("text"),
                        DataValue.list(List.of(DataValue.of(false), DataValue.of(4.5D))))))));
    }

    @Test
    void preserves_existing_data_values_and_converts_primitive_arrays() throws Exception {
        DataValue existing = DataValue.of("existing");

        assertThat(convert(existing)).isSameAs(existing);
        assertThat(convert(new int[] {1, 2})).isEqualTo(DataValue.list(List.of(DataValue.of(1), DataValue.of(2))));
    }

    @Test
    void rejects_null_unsupported_values_and_invalid_map_keys() {
        assertThatThrownBy(() -> convert(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> convert(new Object())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> convert(Map.of(1, "value"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> convert(Map.of(" ", "value"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parses_string_identifiers_for_the_kubejs_api() {
        assertThat(api.id("mmcr:network_request")).isEqualTo(MMCR.id("network_request"));
    }

    private static DataValue convert(Object value) {
        Method converter = Arrays.stream(KubeJSApi.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("toDataValue")
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class)
                .findFirst()
                .orElse(null);
        assertThat(converter).as("recursive KubeJS DataValue converter").isNotNull();
        converter.setAccessible(true);
        try {
            return (DataValue) converter.invoke(null, value);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
