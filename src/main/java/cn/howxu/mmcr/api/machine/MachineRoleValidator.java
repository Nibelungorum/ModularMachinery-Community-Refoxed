package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Validates declarative host/module roles before machine snapshots are installed.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRoleValidator {

    private MachineRoleValidator() {
    }

    public static void validate(Collection<MachineRegistration> registrations,
                                Function<Identifier, MachineRegistration> resolver) {
        Map<Identifier, MachineRegistration> byId = new LinkedHashMap<>();
        for (MachineRegistration registration : registrations) {
            byId.put(registration.id(), registration);
        }

        for (MachineRegistration registration : registrations) {
            validateCouplerCount(registration, countCouplers(registration.pattern()));
            if (registration.isHost()) validateAcceptedModules(registration, byId, resolver);
        }
    }

    public static void validateMachines(Collection<Machine> machines, Function<Identifier, Machine> resolver) {
        Map<Identifier, Machine> byId = new LinkedHashMap<>();
        for (Machine machine : machines) {
            byId.put(machine.registryName(), machine);
        }

        for (Machine machine : machines) {
            validateCouplerCount(machine.registryName(), machine.role(), countCouplers(machine.pattern()));
            if (machine.isHost()) validateAcceptedModules(machine, byId, resolver);
        }
    }

    private static void validateCouplerCount(MachineRegistration registration, int couplers) {
        validateCouplerCount(registration.id(), registration.role(), couplers);
    }

    private static void validateCouplerCount(Identifier id, MachineRole role, int couplers) {
        switch (role) {
            case NORMAL -> {
                if (couplers != 0) throw new IllegalArgumentException("NORMAL machine must declare 0 couplers: " + id);
            }
            case HOST -> {
                if (couplers < 1) throw new IllegalArgumentException("HOST machine must declare at least 1 coupler: " + id);
            }
            case MODULE -> {
                if (couplers != 1) throw new IllegalArgumentException("MODULE machine must declare exactly 1 coupler: " + id);
            }
        }
    }

    private static void validateAcceptedModules(MachineRegistration host,
                                                Map<Identifier, MachineRegistration> byId,
                                                Function<Identifier, MachineRegistration> resolver) {
        if (host.acceptedModuleIds().isEmpty()) {
            throw new IllegalArgumentException("HOST machine must accept at least 1 module: " + host.id());
        }
        for (Identifier moduleId : host.acceptedModuleIds()) {
            MachineRegistration module = byId.get(moduleId);
            if (module == null && resolver != null) module = resolver.apply(moduleId);
            if (module == null) throw new IllegalArgumentException("Unknown module reference: " + host.id() + " -> " + moduleId);
            if (!module.isModule()) {
                throw new IllegalArgumentException("Host " + host.id() + " references " + moduleId
                        + " but it does not reference a MODULE machine");
            }
        }
    }

    private static void validateAcceptedModules(Machine host, Map<Identifier, Machine> byId,
                                                Function<Identifier, Machine> resolver) {
        if (host.acceptedModuleIds().isEmpty()) {
            throw new IllegalArgumentException("HOST machine must accept at least 1 module: " + host.registryName());
        }
        for (Identifier moduleId : host.acceptedModuleIds()) {
            Machine module = byId.get(moduleId);
            if (module == null && resolver != null) module = resolver.apply(moduleId);
            if (module == null) throw new IllegalArgumentException("Unknown module reference: " + host.registryName() + " -> " + moduleId);
            if (!module.isModule()) {
                throw new IllegalArgumentException("Host " + host.registryName() + " references " + moduleId
                        + " but it does not reference a MODULE machine");
            }
        }
    }

    private static int countCouplers(BlockArray pattern) {
        int count = 0;
        for (BlockPredicate predicate : pattern.pattern().values()) count += countCouplers(predicate);
        return count;
    }

    private static int countCouplers(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.MachineCoupler ignored -> 1;
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream().mapToInt(MachineRoleValidator::countCouplers).sum();
            default -> 0;
        };
    }
}
