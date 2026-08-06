package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MachineRegistry {

    private static final Map<Identifier, Machine> MACHINES = new LinkedHashMap<>();
    private static final Map<Identifier, CompiledMachinePattern> COMPILED = new LinkedHashMap<>();

    private MachineRegistry() {
    }

    public static void register(Machine machine) {
        if (MACHINES.containsKey(machine.registryName())) {
            throw new IllegalStateException("Machine already registered: " + machine.registryName());
        }
        MACHINES.put(machine.registryName(), machine);
        COMPILED.put(machine.registryName(), MachinePatternCompiler.compile(machine));
    }

    public static Machine getMachine(Identifier id) {
        return MACHINES.get(id);
    }

    public static Map<Identifier, Machine> getAll() {
        return Collections.unmodifiableMap(MACHINES);
    }

    public static CompiledMachinePattern getCompiled(Identifier id) {
        return COMPILED.get(id);
    }

    public static Map<Identifier, CompiledMachinePattern> getAllCompiled() {
        return Collections.unmodifiableMap(COMPILED);
    }

    public static void rebuildCompiledCache() {
        BlockArrayCache.buildCache(MACHINES.values());
        COMPILED.clear();
        for (Machine machine : MACHINES.values()) {
            COMPILED.put(machine.registryName(), MachinePatternCompiler.compile(machine));
        }
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        MACHINES.clear();
        COMPILED.clear();
        BlockArrayCache.clearForTesting();
    }
}
