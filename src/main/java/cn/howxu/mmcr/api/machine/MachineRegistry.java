package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MachineRegistry {

    private static final Map<Identifier, Machine> STATIC_MACHINES = new LinkedHashMap<>();
    private static volatile Map<Identifier, Machine> STRUCTURE_MACHINES = Map.of();
    private static volatile Map<Identifier, CompiledMachinePattern> COMPILED = Map.of();

    private MachineRegistry() {
    }

    public static void register(Machine machine) {
        if (STATIC_MACHINES.containsKey(machine.registryName())) {
            throw new IllegalStateException("Machine already registered: " + machine.registryName());
        }
        STATIC_MACHINES.put(machine.registryName(), machine);
        Map<Identifier, CompiledMachinePattern> compiled = new LinkedHashMap<>(COMPILED);
        compiled.put(machine.registryName(), MachinePatternCompiler.compile(machine));
        COMPILED = Map.copyOf(compiled);
    }

    public static Machine getMachine(Identifier id) {
        Machine machine = STATIC_MACHINES.get(id);
        return machine != null ? machine : STRUCTURE_MACHINES.get(id);
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

    public static void installStructures(Map<Identifier, MachineStructureDefinition> structures) {
        Map<Identifier, Machine> structureMachines = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineStructureDefinition> entry : structures.entrySet()) {
            MachineRegistration registration = MachineDefinitions.getRegistration(entry.getKey());
            if (registration == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + entry.getKey());
            }
            structureMachines.put(entry.getKey(), MachineStructureRegistry.toRuntimeMachine(registration, entry.getValue()));
        }

        Map<Identifier, Machine> allMachines = new LinkedHashMap<>(STATIC_MACHINES);
        allMachines.putAll(structureMachines);
        Map<BlockArrayCache.Key, BlockArray> cache = BlockArrayCache.buildCacheSnapshot(allMachines.values());
        Map<Identifier, CompiledMachinePattern> compiled = new LinkedHashMap<>();
        for (Machine machine : allMachines.values()) {
            compiled.put(machine.registryName(), MachinePatternCompiler.compile(machine, cache));
        }

        STRUCTURE_MACHINES = Map.copyOf(structureMachines);
        BlockArrayCache.installCache(cache);
        COMPILED = Map.copyOf(compiled);
    }

    public static void rebuildCompiledCache() {
        Map<Identifier, Machine> machines = mergedMachines();
        Map<BlockArrayCache.Key, BlockArray> cache = BlockArrayCache.buildCacheSnapshot(machines.values());
        Map<Identifier, CompiledMachinePattern> compiled = new LinkedHashMap<>();
        for (Machine machine : machines.values()) {
            compiled.put(machine.registryName(), MachinePatternCompiler.compile(machine, cache));
        }
        BlockArrayCache.installCache(cache);
        COMPILED = Map.copyOf(compiled);
    }

    private static Map<Identifier, Machine> mergedMachines() {
        Map<Identifier, Machine> machines = new LinkedHashMap<>(STATIC_MACHINES);
        machines.putAll(STRUCTURE_MACHINES);
        return machines;
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        STATIC_MACHINES.clear();
        STRUCTURE_MACHINES = Map.of();
        COMPILED = Map.of();
        BlockArrayCache.clearForTesting();
    }
}
