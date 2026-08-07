package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MachineRegistry {

    private static final Map<Identifier, Machine> STATIC_MACHINES = new LinkedHashMap<>();
    private static final Map<Identifier, Machine> DYNAMIC_MACHINES = new LinkedHashMap<>();
    private static final Map<Identifier, CompiledMachinePattern> COMPILED = new LinkedHashMap<>();

    private MachineRegistry() {
    }

    public static void register(Machine machine) {
        if (STATIC_MACHINES.containsKey(machine.registryName())) {
            throw new IllegalStateException("Machine already registered: " + machine.registryName());
        }
        STATIC_MACHINES.put(machine.registryName(), machine);
        COMPILED.put(machine.registryName(), MachinePatternCompiler.compile(machine));
    }

    public static Machine getMachine(Identifier id) {
        Machine machine = STATIC_MACHINES.get(id);
        return machine != null ? machine : DYNAMIC_MACHINES.get(id);
    }

    public static Map<Identifier, Machine> getAll() {
        return Collections.unmodifiableMap(mergedMachines());
    }

    public static CompiledMachinePattern getCompiled(Identifier id) {
        return COMPILED.get(id);
    }

    public static Map<Identifier, CompiledMachinePattern> getAllCompiled() {
        return Collections.unmodifiableMap(COMPILED);
    }

    public static boolean containsStatic(Identifier id) {
        return STATIC_MACHINES.containsKey(id);
    }

    public static void replaceDynamic(Map<Identifier, Machine> machines) {
        Map<Identifier, Machine> replacement = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Machine> entry : machines.entrySet()) {
            if (STATIC_MACHINES.containsKey(entry.getKey())) {
                throw new IllegalStateException("Dynamic machine conflicts with static machine: " + entry.getKey());
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        DYNAMIC_MACHINES.clear();
        DYNAMIC_MACHINES.putAll(replacement);
        rebuildCompiledCache();
    }

    public static Map<Identifier, Machine> dynamicSnapshot() {
        return Map.copyOf(DYNAMIC_MACHINES);
    }

    public static void rebuildCompiledCache() {
        Map<Identifier, Machine> machines = mergedMachines();
        BlockArrayCache.buildCache(machines.values());
        COMPILED.clear();
        for (Machine machine : machines.values()) {
            COMPILED.put(machine.registryName(), MachinePatternCompiler.compile(machine));
        }
    }

    private static Map<Identifier, Machine> mergedMachines() {
        Map<Identifier, Machine> machines = new LinkedHashMap<>(STATIC_MACHINES);
        machines.putAll(DYNAMIC_MACHINES);
        return machines;
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        STATIC_MACHINES.clear();
        DYNAMIC_MACHINES.clear();
        COMPILED.clear();
        BlockArrayCache.clearForTesting();
    }
}
