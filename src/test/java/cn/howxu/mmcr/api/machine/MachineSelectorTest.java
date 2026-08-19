package cn.howxu.mmcr.api.machine;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineSelectorTest {

    private static Map<Identifier, Machine> registry(Machine... machines) {
        LinkedHashMap<Identifier, Machine> map = new LinkedHashMap<>();
        for (Machine m : machines) map.put(m.registryName(), m);
        return map;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmcr", path);
    }

    @Test
    void null_or_blank_picks_first_registered() {
        Machine a = new DynamicMachine(id("a"), "A", new BlockArray(Map.of()));
        Machine b = new DynamicMachine(id("b"), "B", new BlockArray(Map.of()));
        MachineSelector.Result result = MachineSelector.select(null, registry(a, b));
        assertThat(result.machine()).isSameAs(a);
        assertThat(result.isFallback()).isTrue();
    }

    @Test
    void specific_id_picks_named_machine() {
        Machine a = new DynamicMachine(id("a"), "A", new BlockArray(Map.of()));
        Machine b = new DynamicMachine(id("b"), "B", new BlockArray(Map.of()));
        MachineSelector.Result result = MachineSelector.select(id("b"), registry(a, b));
        assertThat(result.machine()).isSameAs(b);
        assertThat(result.isFallback()).isFalse();
    }

    @Test
    void unknown_id_returns_null_result() {
        Machine a = new DynamicMachine(id("a"), "A", new BlockArray(Map.of()));
        MachineSelector.Result result = MachineSelector.select(id("missing"), registry(a));
        assertThat(result.machine()).isNull();
        assertThat(result.isFallback()).isFalse();
    }
}
