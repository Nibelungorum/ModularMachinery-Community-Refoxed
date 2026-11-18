package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;

import java.util.IdentityHashMap;
import java.util.Map;

/** Identity tokens of the converters and handlers actually used to derive a cached view.
 * @author howxu <dev@howxu.cn>
 */
final class DerivationEvidence {
    private final Map<OutputType<?>, Object> outputs = new IdentityHashMap<>();
    private final Map<RequirementType<?>, Object> handlers = new IdentityHashMap<>();

    DerivationEvidence(MachineRecipe source) { source.machineOutputs().forEach(this::record); }

    void record(MachineOutput output) {
        var type = output.outputType();
        if (!outputs.containsKey(type)) outputs.put(type, OutputRegistry.isCanonical(output) ? type.conversionToken() : null);
    }

    void record(MachineRequirement requirement) {
        var type = requirement.type();
        if (!handlers.containsKey(type)) handlers.put(type, token(type));
    }

    private static Object token(RequirementType<?> type) {
        var handler = RequirementHandlerRegistry.handlerFor(type);
        return handler == null ? null : handler.derivationToken();
    }

    boolean current() {
        for (var dependency : handlers.entrySet()) {
            if (dependency.getValue() == null || token(dependency.getKey()) != dependency.getValue()) return false;
        }
        for (var dependency : outputs.entrySet()) {
            var type = dependency.getKey();
            if (dependency.getValue() == null || OutputRegistry.typeFor(type.id()) != type
                    || type.conversionToken() != dependency.getValue()) return false;
        }
        return true;
    }
}
