package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Registry-time collection of machine definitions.
 * <p>Definitions feed into {@link cn.howxu.mmcr.registry.ModBlocks} static init
 * so each machine can register its own controller block/item/block-entity
 * before the DeferredRegister is frozen.
 *
 * <p>Built-in or third-party machines register through the SPI
 * {@link #addBuiltinSupplier(Supplier)}; the entry point
 * {@link #bootstrapBuiltins()} drains the queue once, called by the mod
 * constructor before any {@code ModBlocks} class is touched.
 */
public final class MachineDefinitions {

    private static final Map<Identifier, Machine> STATIC_DEFINITIONS = new LinkedHashMap<>();
    private static volatile Map<Identifier, Machine> DYNAMIC_DEFINITIONS = Map.of();
    private static final List<Supplier<Machine>> BUILTIN_SUPPLIERS = new CopyOnWriteArrayList<>();

    private MachineDefinitions() {
    }

    /** Register a single machine definition; intended for runtime scripts (e.g. KubeJS). */
    public static void register(Machine machine) {
        if (STATIC_DEFINITIONS.containsKey(machine.registryName())) {
            throw new IllegalStateException("Machine definition already registered: " + machine.registryName());
        }
        STATIC_DEFINITIONS.put(machine.registryName(), machine);
    }

    /**
     * Add a supplier that produces a built-in machine definition. The supplier
     * is invoked at most once by {@link #bootstrapBuiltins()}; idempotent at
     * the registry level via {@link #register(Machine)}.
     */
    public static void addBuiltinSupplier(Supplier<Machine> supplier) {
        if (supplier == null) throw new IllegalArgumentException("supplier null");
        BUILTIN_SUPPLIERS.add(supplier);
    }

    /**
     * Drain all {@link #addBuiltinSupplier(java.util.function.Supplier) registered
     * suppliers} into the definition map. Called once by {@code MMCR} before
     * any {@code ModBlocks} class is loaded.
     */
    public static void bootstrapBuiltins() {
        for (Supplier<Machine> supplier : BUILTIN_SUPPLIERS) {
            Machine machine = supplier.get();
            if (machine == null) continue;
            try {
                register(machine);
            } catch (IllegalStateException duplicate) {
                // A script already registered the same machine id; ignore silently.
            }
        }
        BUILTIN_SUPPLIERS.clear();
    }

    public static Machine get(Identifier id) {
        Machine machine = STATIC_DEFINITIONS.get(id);
        return machine != null ? machine : DYNAMIC_DEFINITIONS.get(id);
    }

    public static Collection<Machine> all() {
        return Collections.unmodifiableCollection(mergedDefinitions().values());
    }

    public static boolean containsStatic(Identifier id) {
        return STATIC_DEFINITIONS.containsKey(id);
    }

    public static void replaceDynamic(Map<Identifier, Machine> machines) {
        Map<Identifier, Machine> replacement = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Machine> entry : machines.entrySet()) {
            if (STATIC_DEFINITIONS.containsKey(entry.getKey())) {
                throw new IllegalStateException("Dynamic machine conflicts with static definition: " + entry.getKey());
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        DYNAMIC_DEFINITIONS = Map.copyOf(replacement);
    }

    public static Map<Identifier, Machine> dynamicSnapshot() {
        return Map.copyOf(DYNAMIC_DEFINITIONS);
    }

    private static Map<Identifier, Machine> mergedDefinitions() {
        Map<Identifier, Machine> definitions = new LinkedHashMap<>(STATIC_DEFINITIONS);
        definitions.putAll(DYNAMIC_DEFINITIONS);
        return definitions;
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        STATIC_DEFINITIONS.clear();
        DYNAMIC_DEFINITIONS = Map.of();
        BUILTIN_SUPPLIERS.clear();
    }
}
