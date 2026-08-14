package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds server-reloadable machine structures and projects them into runtime machines.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureRegistry {
    private static volatile Map<Identifier, MachineStructureDefinition> DYNAMIC_STRUCTURES = Map.of();

    private MachineStructureRegistry() {
    }

    public static void replaceDynamic(Map<Identifier, MachineStructureDefinition> structures) {
        Map<Identifier, MachineStructureDefinition> replacement = new LinkedHashMap<>();
        for (var entry : structures.entrySet()) {
            Identifier id = entry.getKey();
            MachineStructureDefinition structure = entry.getValue();
            if (MachineDefinitions.getRegistration(id) == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            if (!id.equals(structure.machineId())) {
                throw new IllegalStateException("Structure key does not match machine id: " + id + " != " + structure.machineId());
            }
            replacement.put(id, structure);
        }
        validateDynamicRoles(replacement);
        Map<Identifier, MachineStructureDefinition> snapshot = Map.copyOf(replacement);
        MachineRegistry.installStructures(snapshot);
        DYNAMIC_STRUCTURES = snapshot;
    }

    public static Map<Identifier, MachineStructureDefinition> dynamicSnapshot() {
        return Map.copyOf(DYNAMIC_STRUCTURES);
    }

    public static Machine toRuntimeMachine(MachineRegistration registration, MachineStructureDefinition structure) {
        return new DynamicMachine(
                registration.id(),
                registration.displayNameKey(),
                structure.pattern(),
                registration.controllerSpec(),
                registration.appearance(),
                structure.portRequirements(),
                structure.portTierRequirements(),
                structure.dynamicPatterns(),
                structure.modifierReplacements(),
                registration.maxParallelAmount(),
                registration.allowParallelism(),
                registration.allowMultithreading(),
                1,
                java.util.List.of(),
                registration.role(),
                registration.acceptedModuleIds());
    }

    public static void validateDynamicRoles(Map<Identifier, MachineStructureDefinition> structures) {
        Map<Identifier, MachineRegistration> registrations = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineStructureDefinition> entry : structures.entrySet()) {
            Identifier id = entry.getKey();
            MachineStructureDefinition structure = entry.getValue();
            MachineRegistration registration = MachineDefinitions.getRegistration(id);
            if (registration == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            if (!id.equals(structure.machineId())) {
                throw new IllegalStateException("Structure key does not match machine id: " + id + " != " + structure.machineId());
            }
            registrations.put(id, registration.withPattern(structure.pattern()));
        }
        MachineRoleValidator.validate(registrations.values(), null);
    }

    public static void clearForTesting() {
        DYNAMIC_STRUCTURES = Map.of();
    }
}
