package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MachineRegistry {

    private static final Map<ResourceLocation, Machine> STATIC_MACHINES = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, Machine> STRUCTURE_MACHINES = Map.of();
    private static volatile Map<ResourceLocation, List<CompiledMachinePattern>> COMPILED = Map.of();
    private static volatile Map<ResourceLocation, Machine> EFFECTIVE_MACHINES = Map.of();
    private static volatile Map<ResourceLocation, List<ResourceLocation>> CLIENT_RECIPE_POOLS = Map.of();

    private MachineRegistry() {
    }

    public static void register(Machine machine) {
        synchronized (RuntimeContentVersion.lock()) {
            if (STATIC_MACHINES.containsKey(machine.registryName())) {
                throw new IllegalStateException("Machine already registered: " + machine.registryName());
            }
            STATIC_MACHINES.put(machine.registryName(), machine);
            Map<ResourceLocation, List<CompiledMachinePattern>> compiled = new LinkedHashMap<>(COMPILED);
            compiled.put(machine.registryName(), MachinePatternCompiler.compileStages(machine, null));
            COMPILED = Map.copyOf(compiled);
            rebuildEffectiveSnapshot();
        }
    }

    public static Machine getMachine(ResourceLocation id) {
        Machine machine = STATIC_MACHINES.get(id);
        return machine != null ? machine : STRUCTURE_MACHINES.get(id);
    }

    public static ResourceLocation recipePoolForMachine(Machine machine) {
        return machine == null ? null : recipePoolForMachine(machine.registryName());
    }

    public static ResourceLocation recipePoolForMachine(ResourceLocation machineId) {
        List<ResourceLocation> recipePools = recipePoolsForMachine(machineId);
        return recipePools.isEmpty() ? null : recipePools.getFirst();
    }

    public static List<ResourceLocation> recipePoolsForMachine(Machine machine) {
        return machine == null ? List.of() : recipePoolsForMachine(machine.registryName());
    }

    public static List<ResourceLocation> recipePoolsForMachine(ResourceLocation machineId) {
        if (machineId == null) return List.of();
        List<ResourceLocation> clientPools = CLIENT_RECIPE_POOLS.get(machineId);
        if (clientPools != null) return clientPools;
        MachineRegistration registration = MachineDefinitions.getRegistration(machineId);
        return registration == null ? List.of(machineId) : registration.recipePoolIds();
    }

    public static void replaceClientRecipePools(Map<ResourceLocation, List<ResourceLocation>> recipePools) {
        synchronized (RuntimeContentVersion.lock()) {
            validateClientRecipePools(recipePools);
            Map<ResourceLocation, List<ResourceLocation>> copy = new LinkedHashMap<>();
            recipePools.forEach((id, pools) -> copy.put(id, List.copyOf(pools)));
            CLIENT_RECIPE_POOLS = Map.copyOf(copy);
        }
    }

    public static void clearClientRecipePools() {
        synchronized (RuntimeContentVersion.lock()) {
            CLIENT_RECIPE_POOLS = Map.of();
        }
    }

    public static void validateClientRecipePools(Map<ResourceLocation, List<ResourceLocation>> recipePools) {
        if (recipePools == null) throw new IllegalArgumentException("Invalid machine recipe pool mapping");
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : recipePools.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()
                    || entry.getValue().stream().anyMatch(java.util.Objects::isNull)
                    || entry.getValue().stream().distinct().count() != entry.getValue().size()) {
                throw new IllegalArgumentException("Invalid machine recipe pool mapping");
            }
        }
    }

    public static Map<ResourceLocation, Machine> getAll() {
        synchronized (RuntimeContentVersion.lock()) {
            return EFFECTIVE_MACHINES;
        }
    }

    public static Map<ResourceLocation, Machine> effectiveSnapshot() {
        return getAll();
    }

    public static CompiledMachinePattern getCompiled(ResourceLocation id) {
        return getCompiledStages(id).isEmpty() ? null : getCompiledStages(id).getFirst();
    }

    public static List<CompiledMachinePattern> getCompiledStages(ResourceLocation id) {
        return COMPILED.getOrDefault(id, List.of());
    }

    public static Map<ResourceLocation, CompiledMachinePattern> getAllCompiled() {
        Map<ResourceLocation, CompiledMachinePattern> firstStages = new LinkedHashMap<>();
        COMPILED.forEach((id, stages) -> { if (!stages.isEmpty()) firstStages.put(id, stages.getFirst()); });
        return Collections.unmodifiableMap(firstStages);
    }

    public static boolean containsStatic(ResourceLocation id) {
        return STATIC_MACHINES.containsKey(id);
    }

    public static boolean containsRecipePool(ResourceLocation recipePoolId) {
        if (recipePoolId == null) return false;
        if (MachineDefinitions.allRegistrations().stream()
                .anyMatch(registration -> registration.recipePoolIds().contains(recipePoolId))) return true;
        return EFFECTIVE_MACHINES.values().stream()
                .anyMatch(machine -> recipePoolsForMachine(machine).contains(recipePoolId));
    }

    public static void installStructures(Map<ResourceLocation, MachineStructureDefinition> structures) {
        synchronized (RuntimeContentVersion.lock()) {
            Map<ResourceLocation, Machine> structureMachines = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, MachineStructureDefinition> entry : structures.entrySet()) {
                MachineRegistration registration = MachineDefinitions.getRegistration(entry.getKey());
                if (registration == null) {
                    throw new IllegalStateException("No startup machine registration for structure: " + entry.getKey());
                }
                structureMachines.put(entry.getKey(), MachineStructureRegistry.toRuntimeMachine(registration, entry.getValue()));
            }

            Map<ResourceLocation, Machine> allMachines = new LinkedHashMap<>(STATIC_MACHINES);
            allMachines.putAll(structureMachines);
            Map<BlockArrayCache.Key, BlockArray> cache = BlockArrayCache.buildCacheSnapshot(allMachines.values());
            Map<ResourceLocation, List<CompiledMachinePattern>> compiled = new LinkedHashMap<>();
            for (Machine machine : allMachines.values()) {
                compiled.put(machine.registryName(), MachinePatternCompiler.compileStages(machine, cache));
            }

            STRUCTURE_MACHINES = immutableSnapshot(structureMachines);
            BlockArrayCache.installCache(cache);
            COMPILED = Map.copyOf(compiled);
            EFFECTIVE_MACHINES = immutableSnapshot(allMachines);
        }
    }

    public static void rebuildCompiledCache() {
        synchronized (RuntimeContentVersion.lock()) {
            Map<ResourceLocation, Machine> machines = mergedMachines();
            Map<BlockArrayCache.Key, BlockArray> cache = BlockArrayCache.buildCacheSnapshot(machines.values());
            Map<ResourceLocation, List<CompiledMachinePattern>> compiled = new LinkedHashMap<>();
            for (Machine machine : machines.values()) {
                compiled.put(machine.registryName(), MachinePatternCompiler.compileStages(machine, cache));
            }
            BlockArrayCache.installCache(cache);
            COMPILED = Map.copyOf(compiled);
            rebuildEffectiveSnapshot();
        }
    }

    private static Map<ResourceLocation, Machine> mergedMachines() {
        Map<ResourceLocation, Machine> machines = new LinkedHashMap<>(STATIC_MACHINES);
        machines.putAll(STRUCTURE_MACHINES);
        return machines;
    }

    private static void rebuildEffectiveSnapshot() {
        EFFECTIVE_MACHINES = immutableSnapshot(mergedMachines());
    }

    private static Map<ResourceLocation, Machine> immutableSnapshot(Map<ResourceLocation, Machine> machines) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(machines));
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        synchronized (RuntimeContentVersion.lock()) {
            STATIC_MACHINES.clear();
            STRUCTURE_MACHINES = Map.of();
            COMPILED = Map.of();
            EFFECTIVE_MACHINES = Map.of();
            CLIENT_RECIPE_POOLS = Map.of();
            BlockArrayCache.clearForTesting();
        }
    }

    /** Restores compiled startup machines after a test that intentionally clears the registry. */
    public static void restoreStartupForTesting() {
        installStructures(MachineStructureRegistry.startupSnapshot());
    }
}
