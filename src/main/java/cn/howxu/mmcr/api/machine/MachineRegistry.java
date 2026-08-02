package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MachineRegistry {

    private static final Map<Identifier, Machine> MACHINES = new LinkedHashMap<>();

    private MachineRegistry() {
    }

    public static void register(Machine machine) {
        if (MACHINES.containsKey(machine.registryName())) {
            throw new IllegalStateException("Machine already registered: " + machine.registryName());
        }
        MACHINES.put(machine.registryName(), machine);
    }

    public static Machine getMachine(Identifier id) {
        return MACHINES.get(id);
    }

    public static Map<Identifier, Machine> getAll() {
        return Collections.unmodifiableMap(MACHINES);
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        MACHINES.clear();
    }
}
