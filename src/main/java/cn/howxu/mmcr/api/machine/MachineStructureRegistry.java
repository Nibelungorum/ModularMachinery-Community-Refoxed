package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds server-reloadable machine structures and projects them into runtime machines.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureRegistry {
    private static volatile Map<Identifier, MachineStructureDefinition> STARTUP_STRUCTURES = Map.of();
    private static volatile Map<Identifier, MachineStructureDefinition> DYNAMIC_STRUCTURES = Map.of();

    private MachineStructureRegistry() {
    }

    public static void replaceStartup(Map<Identifier, MachineStructureDefinition> structures) {
        Map<Identifier, MachineStructureDefinition> replacement = validate(structures);
        MachineRegistry.installStructures(effective(replacement, DYNAMIC_STRUCTURES));
        STARTUP_STRUCTURES = replacement;
        RuntimeContentVersion.advance();
    }

    public static void replaceDynamic(Map<Identifier, MachineStructureDefinition> structures) {
        Map<Identifier, MachineStructureDefinition> replacement = validate(structures);
        validateDynamicRoles(replacement);
        MachineRegistry.installStructures(effective(STARTUP_STRUCTURES, replacement));
        DYNAMIC_STRUCTURES = replacement;
        RuntimeContentVersion.advance();
    }

    private static Map<Identifier, MachineStructureDefinition> validate(
            Map<Identifier, MachineStructureDefinition> structures) {
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
        return Map.copyOf(replacement);
    }

    private static Map<Identifier, MachineStructureDefinition> effective(
            Map<Identifier, MachineStructureDefinition> startup,
            Map<Identifier, MachineStructureDefinition> dynamic) {
        Map<Identifier, MachineStructureDefinition> effective = new LinkedHashMap<>(startup);
        effective.putAll(dynamic);
        return Map.copyOf(effective);
    }

    public static Map<Identifier, MachineStructureDefinition> dynamicSnapshot() {
        return Map.copyOf(DYNAMIC_STRUCTURES);
    }

    public static Map<Identifier, MachineStructureDefinition> startupSnapshot() {
        return Map.copyOf(STARTUP_STRUCTURES);
    }

    public static Map<Identifier, MachineStructureDefinition> effectiveSnapshot() {
        return effective(STARTUP_STRUCTURES, DYNAMIC_STRUCTURES);
    }

    public static void replaceClientSnapshot(Map<Identifier, MachineStructureDefinition> structures) {
        Map<Identifier, MachineStructureDefinition> replacement = validateClientSnapshot(structures);
        MachineRegistry.installStructures(replacement);
        STARTUP_STRUCTURES = replacement;
        DYNAMIC_STRUCTURES = Map.of();
    }

    public static Map<Identifier, MachineStructureDefinition> validateClientSnapshot(
            Map<Identifier, MachineStructureDefinition> structures) {
        return validate(structures);
    }

    public static Machine toRuntimeMachine(MachineRegistration registration, MachineStructureDefinition structure) {
        List<MachineStructureStage> stages = MachineStructureFamily.of(structure).stages();
        if (stages.size() > 1 && !registration.expandableStructure()) {
            throw new IllegalArgumentException("Machine " + registration.id()
                    + " declares multiple structure stages but is not marked expandableStructure");
        }
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
                registration.acceptedModuleIds(),
                stages);
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
        STARTUP_STRUCTURES = Map.of();
        DYNAMIC_STRUCTURES = Map.of();
    }
}
