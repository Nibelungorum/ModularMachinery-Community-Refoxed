package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.registration.OptionalSourceRegistration;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;

import java.util.Map;

/** Installs public built-in declarations into the internal runtime models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinRuntime {
    private PublicBuiltinRuntime() {
    }

    public static void registerStructures(DynamicContentReloadService.Candidate candidate) {
        Map<?, ?> structures = OptionalSourceRegistration.invokeDevelopmentSource(
                "org.nibelungorum.builtin.PublicBuiltinDefinitions", "structureDefinitions", new Class<?>[]{});
        if (structures == null) return;
        structures.values().stream()
                .map(structure -> PublicMachineAdapter.toStructureDefinition((MachineStructureDefinition) structure))
                .forEach(candidate::registerStructure);
    }

}
