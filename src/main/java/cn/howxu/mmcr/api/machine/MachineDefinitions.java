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
 * Registry-time collection of startup machine declarations.
 * <p>Declarations feed into {@link cn.howxu.mmcr.registry.ModBlocks} static init
 * so each machine can register its own controller block/item/block-entity
 * before the DeferredRegister is frozen.
 *
 * <p>Built-in or third-party machines register through the SPI
 * {@link #addBuiltinSupplier(Supplier)}; the entry point
 * {@link #bootstrapBuiltins()} drains the queue once, called by the mod
 * constructor before any {@code ModBlocks} class is touched.
 */
public final class MachineDefinitions {

    private static final Map<Identifier, MachineRegistration> STATIC_REGISTRATIONS = new LinkedHashMap<>();
    private static final List<Supplier<MachineRegistration>> BUILTIN_SUPPLIERS = new CopyOnWriteArrayList<>();
    private static boolean registryPhaseOpen = false;

    private MachineDefinitions() {
    }

    /** Register a single startup machine declaration; intended for startup scripts (e.g. KubeJS). */
    public static void register(MachineRegistration registration) {
        if (!registryPhaseOpen) {
            throw new IllegalStateException("Machine registration rejected: registry phase is closed");
        }
        if (STATIC_REGISTRATIONS.containsKey(registration.id())) {
            throw new IllegalStateException("Machine registration already registered: " + registration.id());
        }
        STATIC_REGISTRATIONS.put(registration.id(), registration);
    }

    public static void replace(MachineRegistration registration) {
        if (!registryPhaseOpen) {
            throw new IllegalStateException("Machine replacement rejected: registry phase is closed");
        }
        if (!STATIC_REGISTRATIONS.containsKey(registration.id())) {
            throw new IllegalStateException("Machine replacement not registered: " + registration.id());
        }
        STATIC_REGISTRATIONS.put(registration.id(), registration);
    }

    public static void beginRegistryPhase() {
        registryPhaseOpen = true;
    }

    public static void freezeRegistryPhase() {
        validateRegistryPhase();
        registryPhaseOpen = false;
    }

    /** Validate the current declarations without closing or mutating the phase. */
    public static void validateRegistryPhase() {
        MachineRoleValidator.validate(STATIC_REGISTRATIONS.values(), STATIC_REGISTRATIONS::get);
    }

    public static boolean isRegistryPhaseOpen() {
        return registryPhaseOpen;
    }

    /**
     * Add a supplier that produces a built-in machine definition. The supplier
     * is invoked at most once by {@link #bootstrapBuiltins()}; idempotent at
     * the registry level via {@link #register(MachineRegistration)}.
     */
    public static void addBuiltinSupplier(Supplier<MachineRegistration> supplier) {
        if (supplier == null) throw new IllegalArgumentException("supplier null");
        BUILTIN_SUPPLIERS.add(supplier);
    }

    /**
     * Drain all {@link #addBuiltinSupplier(java.util.function.Supplier) registered
     * suppliers} into the definition map. Called once by {@code MMCR} before
     * any {@code ModBlocks} class is loaded.
     */
    public static void bootstrapBuiltins() {
        for (Supplier<MachineRegistration> supplier : BUILTIN_SUPPLIERS) {
            MachineRegistration registration = supplier.get();
            if (registration == null) continue;
            try {
                register(registration);
            } catch (IllegalStateException duplicate) {
                // A script already registered the same machine id; ignore silently.
            }
        }
        BUILTIN_SUPPLIERS.clear();
    }

    public static MachineRegistration getRegistration(Identifier id) {
        return STATIC_REGISTRATIONS.get(id);
    }

    public static Collection<MachineRegistration> allRegistrations() {
        return Collections.unmodifiableCollection(STATIC_REGISTRATIONS.values());
    }

    public static Map<Identifier, MachineRegistration> effectiveSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(STATIC_REGISTRATIONS));
    }

    public static boolean containsStatic(Identifier id) {
        return STATIC_REGISTRATIONS.containsKey(id);
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        STATIC_REGISTRATIONS.clear();
        BUILTIN_SUPPLIERS.clear();
        registryPhaseOpen = true;
    }
}
